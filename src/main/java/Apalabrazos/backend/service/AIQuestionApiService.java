package Apalabrazos.backend.service;

import Apalabrazos.backend.model.AlphabetMap;
import Apalabrazos.backend.model.Question;
import Apalabrazos.backend.model.QuestionList;
import Apalabrazos.backend.tools.AIQuestionGenerator;
import Apalabrazos.backend.tools.QuestionFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fachada interna para generar preguntas mediante IA.
 *
 * Esta clase actua como la API de generacion que usa tanto el endpoint REST como
 * el inicio de cada partida. Si la IA no esta disponible, puede caer al fichero
 * JSON local para que el juego siga siendo usable en desarrollo o en defensa.
 */
public class AIQuestionApiService {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionApiService.class);
    private static final AIQuestionApiService INSTANCE = new AIQuestionApiService();

    private final QuestionFileLoader fallbackLoader = new QuestionFileLoader();

    private AIQuestionApiService() {
    }

    public static AIQuestionApiService getInstance() {
        return INSTANCE;
    }

    public QuestionList generateQuestionsForNewGame(int numberOfQuestions) throws Exception {
        return generateQuestionsForNewGame(numberOfQuestions, true);
    }

    public QuestionList generateQuestionsForNewGame(int numberOfQuestions, boolean allowFallback) throws Exception {
        int safeCount = normalizeQuestionCount(numberOfQuestions);

        try {
            AIQuestionGenerator generator = new AIQuestionGenerator();
            QuestionList generated = generator.generateBatteryForMissingLetters(buildSpanishAlphabet().subList(0, safeCount));
            QuestionList normalized = normalizeAndLimit(generated, safeCount);

            if (normalized.getCurrentLength() < safeCount) {
                throw new IllegalStateException("La IA solo genero " + normalized.getCurrentLength()
                        + " preguntas de " + safeCount + " solicitadas.");
            }

            log.info("Generadas {} preguntas por IA para nueva partida", normalized.getCurrentLength());
            return normalized;
        } catch (Exception e) {
            log.warn("No se pudieron generar preguntas por IA: {}", e.getMessage(), e);
            if (!allowFallback) {
                throw e;
            }
            QuestionList fallback = loadFallbackQuestions(safeCount);
            log.info("Usando {} preguntas del JSON local como fallback", fallback.getCurrentLength());
            return fallback;
        }
    }

    public QuestionList generateQuestionsForLetters(List<String> letters, boolean allowFallback) throws Exception {
        List<String> normalizedLetters = letters == null ? buildSpanishAlphabet() : letters.stream()
                .map(AIQuestionApiService::normalizeLetter)
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
            log.warn("No se pudieron generar preguntas por IA para letras {}: {}", normalizedLetters, e.getMessage(), e);
            if (!allowFallback) {
                throw e;
            }
            return loadFallbackQuestions(normalizedLetters.size());
        }
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
                .map(AIQuestionApiService::normalizeLetter)
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
}
