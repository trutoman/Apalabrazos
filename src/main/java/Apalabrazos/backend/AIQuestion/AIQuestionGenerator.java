package Apalabrazos.backend.AIQuestion;

import Apalabrazos.backend.config.AIQuestionConfig;
import Apalabrazos.backend.model.AlphabetMap;
import Apalabrazos.backend.model.Question;
import Apalabrazos.backend.model.QuestionLevel;
import Apalabrazos.backend.model.QuestionList;
import Apalabrazos.backend.model.QuestionStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AIQuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionGenerator.class);

    private static final String ENYE = "ñ";
    private static final String ENYE_UPPER = "Ñ";

    private static final List<Charset> MOJIBAKE_SOURCE_CHARSETS = List.of(
            Charset.forName("CP437"),
            Charset.forName("CP850"),
            Charset.forName("windows-1252"),
            StandardCharsets.ISO_8859_1);

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String fallbackModel;
    private final int questionsPerLetter;
    private final int questionsToGeneratePerLetterInBatch;
    private final int lettersPerBatch;
    private final int maxAttemptsPerBatch;
    private final int maxTokens;
    private final String appName;
    private final String appUrl;
    private final String wordDictionaryPath;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private Map<String, List<String>> wordsByLetterCache;

    public AIQuestionGenerator() {
        this.apiKey       = AIQuestionConfig.getApiKey();
        this.apiUrl       = AIQuestionConfig.getApiUrl();
        this.model        = AIQuestionConfig.getModel();
        this.fallbackModel = AIQuestionConfig.getFallbackModel();
        this.questionsPerLetter                  = AIQuestionConfig.getQuestionsPerLetter();
        this.questionsToGeneratePerLetterInBatch = AIQuestionConfig.getQuestionsToGeneratePerLetterInBatch();
        this.lettersPerBatch                     = AIQuestionConfig.getLettersPerBatch();
        this.maxAttemptsPerBatch                 = AIQuestionConfig.getMaxAttemptsPerBatch();
        this.maxTokens                           = AIQuestionConfig.getMaxTokens();
        this.appName           = AIQuestionConfig.getAppName();
        this.appUrl            = AIQuestionConfig.getAppUrl();
        this.wordDictionaryPath = AIQuestionConfig.getWordDictionaryPath();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);

        log.info("AIQuestionGenerator initialized from AIQuestionConfig");
    }

    public int getQuestionsPerLetter() {
        return questionsPerLetter;
    }

    public void validateConfiguration() {
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("AI_API_URL no configurada.");
        }

        if (model == null || model.isBlank()) {
            throw new IllegalStateException("AI_MODEL no configurado.");
        }

        if (questionsPerLetter <= 0) {
            throw new IllegalStateException("AI_QUESTIONS_PER_LETTER debe ser > 0.");
        }

        if (questionsToGeneratePerLetterInBatch < questionsPerLetter) {
            throw new IllegalStateException(
                    "AI_QUESTIONS_TO_GENERATE_PER_LETTER_IN_BATCH debe ser >= AI_QUESTIONS_PER_LETTER.");
        }

        if (lettersPerBatch <= 0) {
            throw new IllegalStateException("AI_LETTERS_PER_BATCH debe ser > 0.");
        }

        if (maxTokens <= 0) {
            throw new IllegalStateException("AI_MAX_TOKENS debe ser > 0.");
        }
    }

    public QuestionList generateFullBattery() throws Exception {
        validateConfiguration();

        List<String> allLetters = new ArrayList<>(AlphabetMap.MAP.values()).stream()
                .map(this::normalizeLetter)
                .distinct()
                .collect(Collectors.toList());

        return generateBatteryForMissingLetters(allLetters);
    }

    public QuestionList generateBatteryForMissingLetters(List<String> targetLetters) throws Exception {
        validateConfiguration();

        if (targetLetters == null || targetLetters.isEmpty()) {
            log.info("No pending letters to generate.");
            return new QuestionList(new ArrayList<>(), 0);
        }

        List<String> normalizedTargetLetters = normalizeTargetLetters(targetLetters);
        Map<String, List<Question>> acceptedByLetter = initializeAcceptedByLetter(normalizedTargetLetters);

        if (normalizedTargetLetters.isEmpty()) {
            return buildQuestionListResult(acceptedByLetter);
        }

        List<List<String>> batches = partitionLetters(normalizedTargetLetters, lettersPerBatch);
        boolean quotaExceeded = false;

        for (List<String> batchLetters : batches) {
            if (quotaExceeded) {
                break;
            }

            quotaExceeded = processBatch(batchLetters, acceptedByLetter);
        }

        return buildQuestionListResult(acceptedByLetter);
    }

    private List<String> normalizeTargetLetters(List<String> targetLetters) {
        return targetLetters.stream()
                .map(this::normalizeLetter)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, List<Question>> initializeAcceptedByLetter(List<String> normalizedTargetLetters) {
        Map<String, List<Question>> acceptedByLetter = new LinkedHashMap<>();
        for (String letter : normalizedTargetLetters) {
            acceptedByLetter.put(letter, new ArrayList<>());
        }
        return acceptedByLetter;
    }

    private QuestionList buildQuestionListResult(Map<String, List<Question>> acceptedByLetter) {
        List<Question> allQuestions = flattenQuestions(acceptedByLetter.values());
        QuestionList result = new QuestionList(allQuestions, allQuestions.size());
        log.info("Generated {} questions for pending letters", allQuestions.size());
        return result;
    }

    private boolean processBatch(List<String> batchLetters, Map<String, List<Question>> acceptedByLetter)
            throws InterruptedException {
        log.info("Processing pending letters batch: {}", batchLetters);

        int attempts = 0;
        boolean quotaExceeded = false;

        while (attempts < maxAttemptsPerBatch && !areBatchLettersComplete(batchLetters, acceptedByLetter)) {
            attempts++;
            try {
                Map<String, CandidateQuestionData> candidatesByLetter = buildCandidatesForBatch(batchLetters);

                if (candidatesByLetter.isEmpty()) {
                    log.warn("Could not prepare candidates for batch {}. Skipping attempt.", batchLetters);
                    break;
                }

                log.info("Sending batch {} to AI with {} candidates", batchLetters, candidatesByLetter.size());
                String responseBody = callAIForBatch(batchLetters, candidatesByLetter);
                List<Question> parsed = parseAIResponse(responseBody, batchLetters, candidatesByLetter);

                int acceptedThisAttempt = acceptParsedQuestions(parsed, acceptedByLetter);

                log.info(
                    "Pending batch {} attempt {}/{} -> accepted this attempt: {}",
                        batchLetters,
                        attempts,
                        maxAttemptsPerBatch,
                        acceptedThisAttempt);

            } catch (QuotaExceededException e) {
                log.warn("Quota exceeded while processing pending letters. Stopping generation: {}",
                        e.getMessage());
                quotaExceeded = true;
                break;
            } catch (Exception e) {
                handleBatchAttemptFailure(batchLetters, attempts, e);
            }
        }

        logBatchStatus(batchLetters, acceptedByLetter);
        return quotaExceeded;
    }

    private int acceptParsedQuestions(List<Question> parsed, Map<String, List<Question>> acceptedByLetter) {
        int acceptedThisAttempt = 0;

        for (Question q : parsed) {
            String invalidReason = getInvalidQuestionReason(q);
            if (invalidReason != null) {
                log.warn(
                    "Discarded question | Reason: {} | Letter: {} | Clue: {} | Responses: {}",
                        invalidReason,
                        q != null ? q.getQuestionLetter() : "null",
                        q != null ? q.getQuestionText() : "null",
                        q != null ? q.getQuestionResponsesList() : "null");
                continue;
            }

            String letter = normalizeLetter(q.getQuestionLetter());
            List<Question> existingForLetter = acceptedByLetter.getOrDefault(letter, Collections.emptyList());

            if (existingForLetter.size() >= questionsPerLetter) {
                continue;
            }

            String duplicateReason = getDuplicateReason(flattenQuestions(acceptedByLetter.values()), q);
            if (duplicateReason != null) {
                log.warn(
                    "Discarded duplicate question | Reason: {} | Letter: {} | Clue: {} | Correct: {}",
                        duplicateReason,
                        letter,
                        q.getQuestionText(),
                        q.getQuestionResponsesList().get(q.getCorrectQuestionIndex()));
                continue;
            }

            acceptedByLetter.get(letter).add(q);
            acceptedThisAttempt++;

                log.info(
                    "Accepted question | Letter: {} | Clue: {} | Correct: {} | Letter total: {}/{}",
                    letter,
                    q.getQuestionText(),
                    q.getQuestionResponsesList().get(q.getCorrectQuestionIndex()),
                    acceptedByLetter.get(letter).size(),
                    questionsPerLetter);
        }

        return acceptedThisAttempt;
    }

    private void handleBatchAttemptFailure(List<String> batchLetters, int attempts, Exception e)
            throws InterruptedException {
        log.error(
            "Pending batch {} attempt {}/{} failed: {}",
                batchLetters,
                attempts,
                maxAttemptsPerBatch,
                e.getMessage(),
                e);

        if (isQuotaException(e)) {
            long waitMs = extractRetryDelayMillis(e.getMessage());
            if (waitMs <= 0) {
                waitMs = AIQuestionConfig.DEFAULT_429_WAIT_MS;
            }

            log.warn("Waiting {} ms before retrying pending batch {}", waitMs, batchLetters);
            Thread.sleep(waitMs);
        }
    }

    private String callAIForBatch(
            List<String> batchLetters,
            Map<String, CandidateQuestionData> candidatesByLetter) throws Exception {
        return callAIWithModel(batchLetters, candidatesByLetter, model, true);
    }

    private String callAIWithModel(
            List<String> batchLetters,
            Map<String, CandidateQuestionData> candidatesByLetter,
            String modelToUse,
            boolean allowFallback) throws Exception {
        String prompt = buildBatchPrompt(batchLetters, candidatesByLetter);
        String requestBody = buildRequestBody(prompt, modelToUse);

        long delayMs = AIQuestionConfig.INITIAL_RETRY_DELAY_MS;
        Exception lastException = null;

        for (int attempt = 1; attempt <= AIQuestionConfig.MAX_RETRIES_ON_503 + 1; attempt++) {
            try {
                return executeMessagesRequest(batchLetters, modelToUse, requestBody);
            } catch (Exception e) {
                lastException = e;

                if (!is503Exception(e) || attempt > AIQuestionConfig.MAX_RETRIES_ON_503) {
                    break;
                }

                log.warn(
                    "Batch {} -> attempt {} with model '{}' failed with 503. Retrying in {} ms",
                        batchLetters,
                        attempt,
                        modelToUse,
                        delayMs);

                Thread.sleep(delayMs);
                delayMs *= 2;
            }
        }

        if (allowFallback
                && fallbackModel != null
                && !fallbackModel.isBlank()
                && !fallbackModel.equals(modelToUse)
                && is503Exception(lastException)) {

                log.warn(
                    "Batch {} -> model fallback due to 503: '{}' -> '{}'",
                    batchLetters,
                    modelToUse,
                    fallbackModel);

            return callAIWithModel(batchLetters, candidatesByLetter, fallbackModel, false);
        }

        if (lastException != null) {
            throw lastException;
        }

        throw new RuntimeException("No se pudo obtener respuesta de la IA.");
    }

    private String buildRequestBody(String prompt, String modelToUse) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelToUse);
        body.put("stream", false);
        body.put("options", Map.of("num_predict", maxTokens));

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        body.put("messages", messages);

        return mapper.writeValueAsString(body);
    }

    private String executeMessagesRequest(List<String> batchLetters, String modelToUse, String requestBody)
            throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120));

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int status = response.statusCode();
        String body = response.body();

        log.info("Ollama HTTP status for batch {} with model '{}': {}", batchLetters, modelToUse, status);

        if (status == 200) {
            if (body == null || body.isBlank()) {
                throw new RuntimeException("La API Anthropic-compatible devolvió HTTP 200 pero el cuerpo está vacío.");
            }
            return body;
        }

        log.error(
            "Anthropic-compatible API error (status {}) with model '{}' for batch {}: {}",
                status,
                modelToUse,
                batchLetters,
                preview(body, AIQuestionConfig.DEFAULT_LOG_PREVIEW));

        String detailedMessage = extractApiErrorMessage(body);

        if (status == 400) {
            throw new RuntimeException("HTTP 400 INVALID_REQUEST. Detalle: " + detailedMessage);
        }

        if (status == 401 || status == 403) {
            throw new RuntimeException("HTTP " + status + " auth/permiso. Detalle: " + detailedMessage);
        }

        if (status == 404) {
            throw new RuntimeException("HTTP 404 endpoint/modelo no encontrado. Detalle: " + detailedMessage);
        }

        if (status == 429) {
            throw new QuotaExceededException("HTTP 429 cuota/rate limit. Detalle: " + detailedMessage);
        }

        if (status == 503) {
            throw new RuntimeException("HTTP 503 UNAVAILABLE. Detalle: " + detailedMessage);
        }

        if (status >= 500) {
            throw new RuntimeException("HTTP " + status + " error interno del proveedor. Detalle: " + detailedMessage);
        }

        throw new RuntimeException(
                "Error llamando a la API Anthropic-compatible: HTTP " + status + ". Detalle: " + detailedMessage);
    }

    private String buildBatchPrompt(
            List<String> batchLetters,
            Map<String, CandidateQuestionData> candidatesByLetter) {
        String wordsData = batchLetters.stream()
                .map(this::normalizeLetter)
                .filter(candidatesByLetter::containsKey)
                .map(letter -> {
                    CandidateQuestionData c = candidatesByLetter.get(letter);
                    boolean isEnye = ENYE.equals(letter);
                    String upper = isEnye ? ENYE_UPPER : letter.toUpperCase(Locale.ROOT);
                    String prefix = isEnye ? "Contiene la Ñ:" : "Con la " + upper + ":";

                    return """
                            - Letra: "%s"
                              Palabra correcta: "%s"
                              El enunciado debe empezar exactamente por: "%s"
                            """.formatted(letter, c.correctWord(), prefix);
                })
                .collect(Collectors.joining("\n"));

        return """
                Eres un generador experto de preguntas tipo rosco de Pasapalabra en español.
                Debes responder SOLO con JSON válido.
                No escribas markdown.
                No escribas explicaciones.
                No generes respuestas posibles.
                No generes correctQuestionIndex.

                Tu única tarea es generar una pista breve para cada palabra correcta.
                La pista NO puede mencionar literalmente la palabra correcta ni derivados evidentes.
                La pista debe definir EXACTAMENTE la palabra correcta, no otra palabra parecida.
                La pista debe ser clara, natural, en español y de dificultad media.

                DATOS:
                %s

                FORMATO JSON OBLIGATORIO:
                {
                  "questionList": [
                    {
                      "questionLetter": "a",
                      "questionText": "Con la A: Pista breve sin decir la respuesta."
                    }
                  ]
                }
                """.formatted(wordsData);
    }

    private List<Question> parseAIResponse(
            String responseBody,
            List<String> expectedLetters,
            Map<String, CandidateQuestionData> candidatesByLetter) throws Exception {
        String content = extractTextContentFromResponse(responseBody);

        if (content.isBlank()) {
            throw new RuntimeException("La IA devolvió contenido vacío.");
        }

        log.info("Raw AI response for batch {}:\n{}", expectedLetters, preview(content, AIQuestionConfig.DEFAULT_LOG_PREVIEW));

        JsonNode questionsNode;
        try {
            questionsNode = mapper.readTree(content);
        } catch (Exception e) {
            String repairedContent = escapeRawNewlinesInsideJsonStrings(content);
            try {
                questionsNode = mapper.readTree(repairedContent);
                log.warn("AI response JSON required newline repair before parsing for batch {}", expectedLetters);
            } catch (Exception repairException) {
                throw new RuntimeException("La IA no devolvió JSON válido. Contenido recibido: " + preview(content, 1000),
                        e);
            }
        }

        JsonNode listNode = null;

        if (questionsNode.isObject() && questionsNode.has("questionList")
                && questionsNode.get("questionList").isArray()) {
            listNode = questionsNode.get("questionList");
        } else if (questionsNode.isArray()) {
            listNode = questionsNode;
        }

        if (listNode == null || !listNode.isArray()) {
            throw new RuntimeException("La respuesta de la IA no contiene un array de preguntas válido.");
        }

        Set<String> expected = expectedLetters.stream()
                .map(this::normalizeLetter)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Question> questions = new ArrayList<>();

        for (JsonNode qNode : listNode) {
            try {
                String rawLetter = repairAndTrim(qNode.path("questionLetter").asText(""));
                String rawText = repairAndTrim(qNode.path("questionText").asText(""));

                String letter = normalizeLetter(rawLetter);
                String text = rawText;

                if (!expected.contains(letter)) {
                    continue;
                }

                CandidateQuestionData candidate = candidatesByLetter.get(letter);

                if (candidate == null || text.isBlank()) {
                    continue;
                }

                String finalLetter = normalizeLetter(candidate.letter());

                List<String> capitalizedResponses = candidate.responses().stream()
                        .map(this::capitalizeWords)
                        .collect(Collectors.toCollection(ArrayList::new));

                Question q = new Question(
                        text,
                        capitalizedResponses,
                        candidate.correctIndex(),
                        QuestionStatus.INIT,
                        QuestionLevel.MEDIUM,
                        finalLetter,
                        "init");

                q.setQuestionLetter(finalLetter);
                questions.add(q);

            } catch (Exception e) {
                log.warn("Error parsing AI question for batch {}: {}", expectedLetters, e.getMessage());
            }
        }

        log.info("Parsed {} candidate questions for batch {}", questions.size(), expectedLetters);
        return questions;
    }

    private String extractTextContentFromResponse(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        JsonNode root = mapper.readTree(responseBody);

        // Ollama /api/chat: {"message": {"role": "assistant", "content": "..."},
        // "done": true}
        JsonNode messageNode = root.path("message");
        if (!messageNode.isMissingNode()) {
            String text = messageNode.path("content").asText("").trim();
            if (text.isBlank()) {
                throw new RuntimeException("La respuesta Ollama contiene 'message' pero 'content' está vacío.");
            }
            return sanitizeContent(repairMojibakeIfNeeded(text));
        }

        throw new RuntimeException("Respuesta inesperada de Ollama — no se encontró 'message.content'. Body: "
                + responseBody.substring(0, Math.min(500, responseBody.length())));
    }

    private Map<String, CandidateQuestionData> buildCandidatesForBatch(List<String> batchLetters) throws Exception {
        Map<String, List<String>> wordsByLetter = getWordsByLetter();
        Map<String, CandidateQuestionData> result = new LinkedHashMap<>();
        Random random = new Random();

        for (String rawLetter : batchLetters) {
            String letter = normalizeLetter(rawLetter);
            List<String> availableWords = new ArrayList<>(wordsByLetter.getOrDefault(letter, Collections.emptyList()));

            if (availableWords.size() < 4) {
                log.warn("Not enough words for letter '{}'. Available: {}", letter,
                        availableWords.size());
                continue;
            }

            Collections.shuffle(availableWords);

            List<String> responses = availableWords.stream()
                    .limit(4)
                    .map(this::capitalizeWords)
                    .collect(Collectors.toCollection(ArrayList::new));

            int correctIndex = random.nextInt(4);
            String correctWord = responses.get(correctIndex);

            result.put(letter, new CandidateQuestionData(letter, responses, correctIndex, correctWord));

                log.info(
                    "Candidates prepared | Letter: {} | Responses: {} | Correct: {}",
                    letter,
                    responses,
                    correctWord);
        }

        return result;
    }

    private Map<String, List<String>> getWordsByLetter() throws Exception {
        if (wordsByLetterCache != null) {
            return wordsByLetterCache;
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        Map<String, Set<String>> seenByLetter = new LinkedHashMap<>();

        long totalLines = 0;
        long acceptedWords = 0;

        // Intenta filesystem primero, luego classpath (Docker)
        java.io.InputStream dictStream = null;
        Path fsPath = Path.of(wordDictionaryPath).toAbsolutePath().normalize();
        if (Files.exists(fsPath)) {
            log.info("Loading dictionary from filesystem: {}", fsPath);
            dictStream = Files.newInputStream(fsPath);
        } else {
            dictStream = getClass().getClassLoader().getResourceAsStream(wordDictionaryPath);
            if (dictStream == null) {
                throw new IllegalStateException("No existe el diccionario de palabras: " + wordDictionaryPath);
            }
            log.info("Loading dictionary from classpath: {}", wordDictionaryPath);
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(dictStream, StandardCharsets.UTF_8));
                java.util.stream.Stream<String> lines = reader.lines()) {
            for (String line : (Iterable<String>) lines::iterator) {
                totalLines++;

                String word = repairAndTrim(line);

                if (!looksReasonableDictionaryWord(word)) {
                    continue;
                }

                String firstLetter = getDictionaryKeyForWord(word);

                if (firstLetter.isBlank()) {
                    continue;
                }

                String normalizedWord = normalizeFreeText(word);

                result.computeIfAbsent(firstLetter, k -> new ArrayList<>());
                seenByLetter.computeIfAbsent(firstLetter, k -> new LinkedHashSet<>());

                if (seenByLetter.get(firstLetter).add(normalizedWord)) {
                    result.get(firstLetter).add(word);
                    acceptedWords++;
                }
            }
        }

        wordsByLetterCache = result;

        log.info("Dictionary loaded from {}. Lines read: {}. Words accepted: {}", wordDictionaryPath,
                totalLines, acceptedWords);
        for (Map.Entry<String, List<String>> entry : wordsByLetterCache.entrySet()) {
            log.info("Letter '{}' -> {} words available", entry.getKey(), entry.getValue().size());
        }

        return wordsByLetterCache;
    }

    private boolean looksReasonableDictionaryWord(String word) {
        if (word == null)
            return false;

        String repaired = repairAndTrim(word);
        String normalized = normalizeFreeText(repaired);

        if (repaired.isBlank())
            return false;
        if (repaired.length() < 4 || repaired.length() > 11)
            return false;
        if (repaired.contains(" "))
            return false;
        if (repaired.contains("-"))
            return false;
        if (looksLikeMojibake(repaired))
            return false;
        if (!repaired.matches("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü]+$"))
            return false;

        if (normalized.contains("k") || normalized.contains("w")) {
            return false;
        }

        if (isLikelyBadConjugation(normalized))
            return false;

        return true;
    }

    private boolean isLikelyBadConjugation(String normalized) {
        if (normalized == null || normalized.isBlank())
            return true;

        return normalized.endsWith("abais")
                || normalized.endsWith("abamos")
                || normalized.endsWith("arais")
                || normalized.endsWith("erais")
                || normalized.endsWith("irais")
                || normalized.endsWith("aramos")
                || normalized.endsWith("eramos")
                || normalized.endsWith("iramos")
                || normalized.endsWith("aremos")
                || normalized.endsWith("eremos")
                || normalized.endsWith("iremos")
                || normalized.endsWith("aria")
                || normalized.endsWith("arias")
                || normalized.endsWith("arian")
                || normalized.endsWith("eria")
                || normalized.endsWith("erias")
                || normalized.endsWith("erian")
                || normalized.endsWith("iria")
                || normalized.endsWith("irias")
                || normalized.endsWith("irian")
                || normalized.endsWith("ase")
                || normalized.endsWith("ases")
                || normalized.endsWith("asen")
                || normalized.endsWith("aseis")
                || normalized.endsWith("iese")
                || normalized.endsWith("iesen")
                || normalized.endsWith("ieses")
                || normalized.endsWith("ieseis")
                || normalized.endsWith("aste")
                || normalized.endsWith("asteis")
                || normalized.endsWith("iste")
                || normalized.endsWith("isteis")
                || normalized.endsWith("aron")
                || normalized.endsWith("aran")
                || normalized.endsWith("areis")
                || normalized.endsWith("ereis")
                || normalized.endsWith("ireis")
                || normalized.endsWith("ando")
                || normalized.endsWith("iendo")
                || normalized.endsWith("ad");
    }

    /**
     * Clave con la que se agrupa una palabra del diccionario.
     *
     * Reglas tipo Pasapalabra:
     * - A-Z: la palabra debe EMPEZAR por la letra.
     * - Ñ: la palabra debe CONTENER la ñ, no necesariamente empezar por ella.
     */
    private String getDictionaryKeyForWord(String word) {
        if (word == null || word.isBlank())
            return "";

        String repaired = repairAndTrim(word);
        String lower = repaired.toLowerCase(Locale.ROOT);

        if (lower.contains(ENYE)) {
            return ENYE;
        }

        String first = lower.substring(0, 1);
        return normalizeFreeText(first);
    }

    private record CandidateQuestionData(
            String letter,
            List<String> responses,
            int correctIndex,
            String correctWord) {
    }

    private String getInvalidQuestionReason(Question q) {
        if (q == null)
            return "question is null";
        if (q.getQuestionResponsesList() == null || q.getQuestionResponsesList().size() != 4)
            return "responses list must contain exactly 4 options";
        if (q.getCorrectQuestionIndex() < 0 || q.getCorrectQuestionIndex() > 3)
            return "correctQuestionIndex must be between 0 and 3";
        if (q.getQuestionText() == null || q.getQuestionText().isBlank())
            return "question text is blank";
        if (q.getQuestionLetter() == null || q.getQuestionLetter().isBlank())
            return "question letter is blank";

        String text = normalizeFreeText(repairAndTrim(q.getQuestionText()));
        String letter = normalizeLetter(repairAndTrim(q.getQuestionLetter()));

        String expectedStartsPrefix = "con la " + letter;
        String expectedContainsPrefix = "contiene la " + letter;

        boolean startsMode = text.startsWith(expectedStartsPrefix);
        boolean containsMode = text.startsWith(expectedContainsPrefix);

        if (!startsMode && !containsMode) {
            return "question text must start with 'Con la " + letter.toUpperCase(Locale.ROOT)
                    + ":' or 'Contiene la " + letter.toUpperCase(Locale.ROOT) + ":'";
        }

        for (String r : q.getQuestionResponsesList()) {
            if (!looksReasonableAnswer(r)) {
                return "invalid response option: " + repairAndTrim(r);
            }
        }

        String correct = repairAndTrim(q.getQuestionResponsesList().get(q.getCorrectQuestionIndex()));

        for (String r : q.getQuestionResponsesList()) {
            if (!containsLetter(repairAndTrim(r), letter))
                return "response does not match letter '" + letter + "': " + repairAndTrim(r);
        }

        if (startsMode && !startsWithLetter(correct, letter)) {
            return "correct response does not start with required letter '" + letter + "': " + correct;
        }

        if (containsMode && !containsLetter(correct, letter)) {
            return "correct response does not contain required letter '" + letter + "': " + correct;
        }

        if (hasDuplicateResponses(q.getQuestionResponsesList())) {
            return "responses list contains duplicates";
        }

        return null;
    }

    private String getDuplicateReason(List<Question> existing, Question candidate) {
        String candidateText = normalizeFreeText(repairAndTrim(candidate.getQuestionText()));
        String candidateCorrect = normalizeFreeText(
                repairAndTrim(candidate.getQuestionResponsesList().get(candidate.getCorrectQuestionIndex())));

        for (Question q : existing) {
            String existingText = normalizeFreeText(repairAndTrim(q.getQuestionText()));
            String existingCorrect = normalizeFreeText(
                    repairAndTrim(q.getQuestionResponsesList().get(q.getCorrectQuestionIndex())));

            if (existingText.equals(candidateText))
                return "same clue as an already accepted question";
            if (existingCorrect.equals(candidateCorrect))
                return "same correct response as an already accepted question";
        }

        return null;
    }

    private boolean hasDuplicateResponses(List<String> responses) {
        Set<String> set = responses.stream()
                .map(this::repairAndTrim)
                .map(this::normalizeFreeText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return set.size() != responses.size();
    }

    private boolean looksReasonableAnswer(String answer) {
        if (answer == null || answer.isBlank())
            return false;

        String repaired = repairAndTrim(answer);
        String normalized = normalizeFreeText(repaired);

        if (normalized.isBlank())
            return false;
        if (normalized.length() < 2 || normalized.length() > 40)
            return false;
        if (normalized.matches(".*\\d.*"))
            return false;
        if (normalized.contains("?") || normalized.contains("¿") || normalized.contains("!"))
            return false;
        if (normalized.contains(","))
            return false;
        if (normalized.contains(";"))
            return false;
        if (normalized.contains(":"))
            return false;

        int words = repaired.trim().split("\\s+").length;
        if (words > 2)
            return false;

        if (looksLikeMojibake(repaired))
            return false;
        if (!repaired.matches("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü\\s-]+$"))
            return false;

        return true;
    }

    private boolean areBatchLettersComplete(List<String> batchLetters, Map<String, List<Question>> acceptedByLetter) {
        for (String letter : batchLetters) {
            List<Question> list = acceptedByLetter.getOrDefault(letter, Collections.emptyList());
            if (list.size() < questionsPerLetter) {
                return false;
            }
        }
        return true;
    }

    private void logBatchStatus(List<String> batchLetters, Map<String, List<Question>> acceptedByLetter) {
        for (String letter : batchLetters) {
            int count = acceptedByLetter.getOrDefault(letter, Collections.emptyList()).size();
            if (count < questionsPerLetter) {
                log.warn("Letter '{}' incomplete: {}/{}", letter, count, questionsPerLetter);
            } else {
                log.info("Letter '{}' complete: {}/{}", letter, count, questionsPerLetter);
            }
        }
    }

    private List<List<String>> partitionLetters(List<String> letters, int batchSize) {
        List<List<String>> batches = new ArrayList<>();

        for (int i = 0; i < letters.size(); i += batchSize) {
            int end = Math.min(i + batchSize, letters.size());
            batches.add(new ArrayList<>(letters.subList(i, end)));
        }

        return batches;
    }

    private List<Question> flattenQuestions(Collection<List<Question>> values) {
        List<Question> result = new ArrayList<>();
        for (List<Question> list : values) {
            result.addAll(list.stream().limit(questionsPerLetter).toList());
        }
        return result;
    }

    private String sanitizeContent(String content) {
        if (content == null)
            return "";

        content = content.trim();

        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }

        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        return content.trim();
    }

    /**
     * Repairs malformed JSON where a raw line break appears inside a quoted string.
     * Example: "questionText": "foo\nbar" (raw newline) -> "questionText": "foo\\nbar"
     */
    private String escapeRawNewlinesInsideJsonStrings(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        StringBuilder repaired = new StringBuilder(content.length() + 32);
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (!inString) {
                repaired.append(c);
                if (c == '"') {
                    inString = true;
                }
                continue;
            }

            if (escaped) {
                repaired.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                repaired.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                repaired.append(c);
                inString = false;
                continue;
            }

            if (c == '\r') {
                repaired.append("\\n");
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                continue;
            }

            if (c == '\n') {
                repaired.append("\\n");
                continue;
            }

            repaired.append(c);
        }

        return repaired.toString();
    }

    public void saveToFile(QuestionList questions, String filePath) throws Exception {
        String json = mapper.writeValueAsString(questions);
        Files.writeString(Path.of(filePath), json, StandardCharsets.UTF_8);
        log.info("Questions saved to: {}", filePath);
    }

    private boolean isQuotaException(Exception e) {
        if (e == null || e.getMessage() == null)
            return false;
        return e.getMessage().contains("HTTP 429");
    }

    private boolean is503Exception(Exception e) {
        if (e == null || e.getMessage() == null)
            return false;
        return e.getMessage().contains("HTTP 503") || e.getMessage().contains("UNAVAILABLE");
    }

    private long extractRetryDelayMillis(String message) {
        if (message == null || message.isBlank()) {
            return 0L;
        }

        Matcher matcher = Pattern.compile("Please retry in\\s+([0-9]+(?:\\.[0-9]+)?)s", Pattern.CASE_INSENSITIVE)
                .matcher(message);

        if (matcher.find()) {
            double seconds = Double.parseDouble(matcher.group(1));
            return (long) Math.ceil(seconds * 1000);
        }

        matcher = Pattern.compile("\"retryDelay\"\\s*:\\s*\"([0-9]+)s\"", Pattern.CASE_INSENSITIVE)
                .matcher(message);

        if (matcher.find()) {
            long seconds = Long.parseLong(matcher.group(1));
            return seconds * 1000L;
        }

        return 0L;
    }

    private String normalizeLetter(String letter) {
        if (letter == null)
            return "";
        String repaired = repairAndTrim(letter);
        String trimmed = repaired.trim().toLowerCase(Locale.ROOT);

        if ("ñ".equals(trimmed) || "├▒".equals(trimmed) || "ã±".equals(trimmed) || "Ã±".equals(trimmed)
                || "┬ñ".equals(trimmed)) {
            return "ñ";
        }

        return normalizeFreeText(trimmed);
    }

    private String normalizeFreeText(String text) {
        if (text == null)
            return "";

        String repaired = repairAndTrim(text)
                .trim()
                .toLowerCase(Locale.ROOT);

        // IMPORTANTE:
        // Normalizer NFD convierte "ñ" en "n" + tilde combinada.
        // Si luego quitamos los diacríticos, la ñ se pierde y pasa a ser n.
        // Para el rosco necesitamos conservar la ñ como letra distinta.
        repaired = repaired
                .replace("ñ", "__ENYE__")
                .replace("Ñ", "__ENYE__");

        String normalized = Normalizer.normalize(repaired, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("__ENYE__", "ñ");

        return normalized;
    }

    /**
     * Capitalizes the first letter of each word in a string.
     * Example: "mi respuesta" -> "Mi Respuesta"
     */
    private String capitalizeWords(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private boolean startsWithLetter(String word, String letter) {
        if (word == null || letter == null)
            return false;

        String rawWord = repairAndTrim(word).toLowerCase(Locale.ROOT);
        String normalizedLetter = normalizeLetter(letter);

        if (rawWord.isBlank() || normalizedLetter.isBlank())
            return false;

        if ("ñ".equals(normalizedLetter)) {
            return rawWord.startsWith("ñ");
        }

        return normalizeFreeText(rawWord).startsWith(normalizedLetter);
    }

    private boolean containsLetter(String word, String letter) {
        if (word == null || letter == null)
            return false;

        String rawWord = repairAndTrim(word).toLowerCase(Locale.ROOT);
        String normalizedLetter = normalizeLetter(letter);

        if (rawWord.isBlank() || normalizedLetter.isBlank())
            return false;

        if ("ñ".equals(normalizedLetter)) {
            return rawWord.contains("ñ");
        }

        return normalizeFreeText(rawWord).contains(normalizedLetter);
    }

    private String extractApiErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "sin detalle";
        }

        try {
            JsonNode root = mapper.readTree(responseBody);

            if (root.has("error")) {
                JsonNode errorNode = root.path("error");
                String message = errorNode.path("message").asText("");
                String type = errorNode.path("type").asText("");
                String code = errorNode.path("code").asText("");

                StringBuilder sb = new StringBuilder();

                if (!code.isBlank()) {
                    sb.append("code=").append(code);
                }
                if (!type.isBlank()) {
                    if (sb.length() > 0)
                        sb.append(", ");
                    sb.append("type=").append(type);
                }
                if (!message.isBlank()) {
                    if (sb.length() > 0)
                        sb.append(", ");
                    sb.append("message=").append(message);
                }

                if (sb.length() > 0) {
                    return sb.toString();
                }
            }

            String type = root.path("type").asText("");
            String message = root.path("message").asText("");
            if (!type.isBlank() || !message.isBlank()) {
                StringBuilder sb = new StringBuilder();
                if (!type.isBlank())
                    sb.append("type=").append(type);
                if (!message.isBlank()) {
                    if (sb.length() > 0)
                        sb.append(", ");
                    sb.append("message=").append(message);
                }
                return sb.toString();
            }

        } catch (Exception e) {
            log.warn("No se pudo parsear el error de la API: {}", e.getMessage());
        }

        return preview(responseBody, 500);
    }

    private String preview(String text, int maxLen) {
        if (text == null)
            return "";
        return text.substring(0, Math.min(text.length(), maxLen));
    }

    private String repairMojibakeIfNeeded(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        if (!looksLikeMojibake(input)) {
            return input;
        }

        for (Charset sourceCharset : MOJIBAKE_SOURCE_CHARSETS) {
            try {
                String repaired = new String(input.getBytes(sourceCharset), StandardCharsets.UTF_8);

                if (repaired != null
                        && !repaired.isBlank()
                        && !repaired.equals(input)
                        && scoreTextQuality(repaired) > scoreTextQuality(input)) {
                    return repaired;
                }
            } catch (Exception ignored) {
            }
        }

        String trimmed = input.trim();
        if ("├▒".equals(trimmed) || "Ã±".equals(trimmed) || "ã±".equals(trimmed) || "┬ñ".equals(trimmed)) {
            return "ñ";
        }

        return input;
    }

    private boolean looksLikeMojibake(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return text.contains("Ã")
                || text.contains("Â")
                || text.contains("├")
                || text.contains("┬")
                || text.contains("�");
    }

    private int scoreTextQuality(String text) {
        if (text == null || text.isBlank()) {
            return Integer.MIN_VALUE;
        }

        int score = 0;

        if (text.contains("Ã"))
            score -= 10;
        if (text.contains("Â"))
            score -= 10;
        if (text.contains("├"))
            score -= 12;
        if (text.contains("┬"))
            score -= 12;
        if (text.contains("�"))
            score -= 20;

        for (char c : text.toCharArray()) {
            if ("áéíóúÁÉÍÓÚñÑüÜ".indexOf(c) >= 0) {
                score += 3;
            }
        }

        return score;
    }

    private String repairAndTrim(String text) {
        if (text == null) {
            return "";
        }
        return repairMojibakeIfNeeded(text).trim();
    }

    private static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }
}