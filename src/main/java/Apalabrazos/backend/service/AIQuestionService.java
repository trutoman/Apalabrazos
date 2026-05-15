package Apalabrazos.backend.service;

import Apalabrazos.backend.events.AIQuestionPreloadCompletedEvent;
import Apalabrazos.backend.events.AIQuestionPreloadFailedEvent;
import Apalabrazos.backend.events.AIQuestionPreloadRequestedEvent;
import Apalabrazos.backend.events.EventListener;
import Apalabrazos.backend.events.GameEvent;
import Apalabrazos.backend.events.GlobalAsyncEventBus;
import Apalabrazos.backend.events.GlobalBusEventCatalog;
import Apalabrazos.backend.model.AlphabetMap;
import Apalabrazos.backend.model.Question;
import Apalabrazos.backend.model.QuestionList;
import Apalabrazos.backend.tools.AIQuestionGenerator;
import Apalabrazos.backend.tools.QuestionFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Central service for AI question generation.
 *
 * Responsibilities:
 * - Generate question batteries for matches.
 * - Handle fallback to local JSON when configured.
 * - Receive preload commands through the global bus and emit completion/failure events.
 * - Manage optional scheduled generation and manual trigger for admin flows.
 */
public class AIQuestionService implements EventListener {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionService.class);

    private static final String SOURCE_AI = "AI";
    private static final String SOURCE_FALLBACK = "FALLBACK_JSON";

    private static final AIQuestionService INSTANCE = new AIQuestionService();

    private final QuestionFileLoader fallbackLoader = new QuestionFileLoader();
    private final ExecutorService preloadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> scheduledTask;
    private volatile boolean schedulerStarted;
    private volatile boolean schedulerRunning;

    // Scheduler config
    private final boolean schedulerEnabled;
    private final int schedulerHour;
    private final int schedulerMinute;
    private final ZoneId schedulerZone;
    private final String schedulerOutputDir;
    private final String schedulerFilename;

    private AIQuestionService() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ai-question-service-scheduler");
            t.setDaemon(true);
            return t;
        });

        this.schedulerEnabled = readEnvBool("AI_GENERATOR_ENABLED", false);
        this.schedulerHour = readEnvInt("AI_GENERATOR_HOUR", 8);
        this.schedulerMinute = readEnvInt("AI_GENERATOR_MINUTE", 0);
        this.schedulerZone = ZoneId.of(readEnv("AI_GENERATOR_TIMEZONE", "Europe/Madrid"));
        this.schedulerOutputDir = readEnv("AI_GENERATOR_OUTPUT_DIR", "src/main/resources/Apalabrazos/data");
        this.schedulerFilename = readEnv("AI_GENERATOR_FILENAME", "questions2.json");

        GlobalAsyncEventBus.addListener(this);
        log.info("AIQuestionService initialized. schedulerEnabled={}, schedule={}:{}, zone={}",
                schedulerEnabled, schedulerHour, schedulerMinute, schedulerZone);
    }

    public static AIQuestionService getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEvent(GameEvent event) {
        if (!GlobalBusEventCatalog.isHandledByAIQuestionService(event)) {
            return;
        }

        if (!(event instanceof AIQuestionPreloadRequestedEvent requested)) {
            return;
        }

        preloadExecutor.submit(() -> processPreloadRequest(requested));
    }

    public QuestionList generateQuestionsForNewGame(int numberOfQuestions) throws Exception {
        return generateQuestionsForNewGame(numberOfQuestions, true);
    }

    public QuestionList generateQuestionsForNewGame(int numberOfQuestions, boolean allowFallback) throws Exception {
        return generateQuestionsForNewGameWithSource(numberOfQuestions, allowFallback).questions();
    }

    public QuestionList generateQuestionsForLetters(List<String> letters, boolean allowFallback) throws Exception {
        List<String> normalizedLetters = letters == null ? buildSpanishAlphabet() : letters.stream()
                .map(AIQuestionService::normalizeLetter)
                .filter(letter -> !letter.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (normalizedLetters.isEmpty()) {
            normalizedLetters = buildSpanishAlphabet();
        }

        try {
            AIQuestionGenerator generator = new AIQuestionGenerator();
            QuestionList generated = generator.generateBatteryForMissingLetters(normalizedLetters);
            return normalizeAndLimit(generated, normalizedLetters.size());
        } catch (Exception e) {
            log.warn("Could not generate questions by AI for letters {}: {}", normalizedLetters, e.getMessage(), e);
            if (!allowFallback) {
                throw e;
            }
            return loadFallbackQuestions(normalizedLetters.size());
        }
    }

    public void startScheduledGeneration() {
        if (!schedulerEnabled) {
            log.info("AI scheduled generation disabled (AI_GENERATOR_ENABLED != true)");
            return;
        }

        if (schedulerStarted) {
            return;
        }

        synchronized (this) {
            if (schedulerStarted) {
                return;
            }

            long delayMinutes = calculateDelayMinutes();
            scheduledTask = scheduler.scheduleAtFixedRate(
                    this::generateAndSaveScheduled,
                    delayMinutes,
                    TimeUnit.DAYS.toMinutes(1),
                    TimeUnit.MINUTES);

            schedulerStarted = true;
            log.info("AI scheduled generation started. Next run in {} minutes at {}:{} ({})",
                    delayMinutes, schedulerHour, schedulerMinute, schedulerZone);
        }
    }

    public void forceGenerateNow() {
        log.info("Manual AI generation requested");
        scheduler.submit(this::generateAndSaveScheduled);
    }

    public void stop() {
        synchronized (this) {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }
            scheduler.shutdown();
            schedulerStarted = false;
        }

        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        preloadExecutor.shutdown();
        log.info("AIQuestionService stopped");
    }

    private void processPreloadRequest(AIQuestionPreloadRequestedEvent request) {
        String matchId = request.getMatchId();
        int requested = request.getNumberOfQuestions();

        log.info("[AI-PRELOAD] Starting preload for match {} (requested={}, allowFallback={})",
                matchId, requested, request.isAllowFallback());

        long startNs = System.nanoTime();
        try {
            GenerationResult result = generateQuestionsForNewGameWithSource(requested, request.isAllowFallback());
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

            log.info("[AI-PRELOAD] Completed preload for match {} in {} ms. count={}, source={}",
                    matchId,
                    elapsedMs,
                    result.questions().getCurrentLength(),
                    result.source());

            GlobalAsyncEventBus.publishAndForget(
                    new AIQuestionPreloadCompletedEvent(matchId, result.questions(), result.source()));
        } catch (Exception e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            log.error("[AI-PRELOAD] Failed preload for match {} in {} ms: {}",
                    matchId, elapsedMs, e.getMessage(), e);

            GlobalAsyncEventBus.publishAndForget(
                    new AIQuestionPreloadFailedEvent(matchId, e.getMessage(), "LOAD_FAILED"));
        }
    }

    private GenerationResult generateQuestionsForNewGameWithSource(int numberOfQuestions, boolean allowFallback)
            throws Exception {
        int safeCount = normalizeQuestionCount(numberOfQuestions);

        try {
            AIQuestionGenerator generator = new AIQuestionGenerator();
            QuestionList generated = generator.generateBatteryForMissingLetters(buildSpanishAlphabet().subList(0, safeCount));
            QuestionList normalized = normalizeAndLimit(generated, safeCount);

            if (normalized.getCurrentLength() < safeCount) {
                throw new IllegalStateException("AI generated only " + normalized.getCurrentLength()
                        + " questions out of " + safeCount + " requested.");
            }

            log.info("Generated {} questions by AI for new match", normalized.getCurrentLength());
            return new GenerationResult(normalized, SOURCE_AI);
        } catch (Exception e) {
            log.warn("Could not generate questions by AI: {}", e.getMessage(), e);
            if (!allowFallback) {
                throw e;
            }
            QuestionList fallback = loadFallbackQuestions(safeCount);
            log.info("Using {} local JSON questions as fallback", fallback.getCurrentLength());
            return new GenerationResult(fallback, SOURCE_FALLBACK);
        }
    }

    private void generateAndSaveScheduled() {
        if (schedulerRunning) {
            log.warn("AI scheduled generation already running, skipping this trigger");
            return;
        }

        schedulerRunning = true;
        String dateTag = LocalDate.now(schedulerZone).format(DateTimeFormatter.ISO_LOCAL_DATE);

        try {
            log.info("[AI-SCHEDULED] Starting scheduled generation [{}]", dateTag);

            AIQuestionGenerator generator = new AIQuestionGenerator();
            QuestionList questions = generator.generateFullBattery();
            int count = questions.getCurrentLength();

            if (count == 0) {
                log.error("[AI-SCHEDULED] No valid questions generated. Existing file will not be overwritten.");
                return;
            }

            String outputPath = schedulerOutputDir + "/" + schedulerFilename;
            generator.saveToFile(questions, outputPath);
            log.info("[AI-SCHEDULED] Saved {} generated questions to {}", count, outputPath);

            String backupPath = schedulerOutputDir + "/questions_backup_" + dateTag + ".json";
            generator.saveToFile(questions, backupPath);
            log.info("[AI-SCHEDULED] Backup saved to {}", backupPath);
        } catch (Exception e) {
            log.error("[AI-SCHEDULED] Error generating questions [{}]: {}", dateTag, e.getMessage(), e);
        } finally {
            schedulerRunning = false;
        }
    }

    private long calculateDelayMinutes() {
        ZonedDateTime now = ZonedDateTime.now(schedulerZone);
        ZonedDateTime nextRun = now.withHour(schedulerHour).withMinute(schedulerMinute).withSecond(0).withNano(0);
        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).toMinutes();
    }

    private QuestionList loadFallbackQuestions(int count) throws IOException {
        return fallbackLoader.loadQuestions(count);
    }

    private QuestionList normalizeAndLimit(QuestionList source, int maxQuestions) {
        if (source == null || source.getQuestionList() == null) {
            return new QuestionList(new ArrayList<>(), maxQuestions);
        }

        Map<String, Question> byLetter = new LinkedHashMap<>();
        for (Question question : source.getQuestionList()) {
            if (question == null) {
                continue;
            }

            String letter = normalizeLetter(question.getQuestionLetter());
            if (letter.isBlank() || byLetter.containsKey(letter)) {
                continue;
            }

            question.setQuestionLetter(letter);
            byLetter.put(letter, question);
        }

        List<Question> ordered = new ArrayList<>();
        for (String letter : buildSpanishAlphabet()) {
            Question question = byLetter.get(letter);
            if (question != null) {
                ordered.add(question);
            }

            if (ordered.size() >= maxQuestions) {
                break;
            }
        }

        return new QuestionList(ordered, maxQuestions);
    }

    private int normalizeQuestionCount(int requested) {
        if (requested <= 0) {
            return AlphabetMap.MAP.size();
        }
        return Math.min(requested, AlphabetMap.MAP.size());
    }

    private List<String> buildSpanishAlphabet() {
        return AlphabetMap.MAP.keySet().stream()
                .sorted()
                .map(AlphabetMap.MAP::get)
                .map(AIQuestionService::normalizeLetter)
                .distinct()
                .collect(Collectors.toList());
    }

    private static String normalizeLetter(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        if ("ñ".equals(trimmed) || "├▒".equals(trimmed) || "ã±".equals(trimmed) || "Ã±".equals(trimmed)) {
            return "ñ";
        }
        return Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static String readEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static int readEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean readEnvBool(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    private record GenerationResult(QuestionList questions, String source) {
    }
}
