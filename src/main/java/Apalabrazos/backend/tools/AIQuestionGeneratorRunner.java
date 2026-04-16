package Apalabrazos.backend.tools;

import Apalabrazos.backend.model.AlphabetMap;
import Apalabrazos.backend.model.Question;
import Apalabrazos.backend.model.QuestionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class AIQuestionGeneratorRunner {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionGeneratorRunner.class);

    private static final String ENYE = "ñ";

    private static final List<Charset> MOJIBAKE_SOURCE_CHARSETS = List.of(
            Charset.forName("CP437"),
            Charset.forName("CP850"),
            Charset.forName("windows-1252"),
            StandardCharsets.ISO_8859_1
    );

    public static void main(String[] args) {
        try {
            log.info("RUNNER ARRANCADO");

            String outputDir = readEnv("AI_GENERATOR_OUTPUT_DIR", "src/main/resources/Apalabrazos/data");
            String outputFileName = readEnv("AI_GENERATOR_FILENAME", "questions2.generated.json");

            Path outputDirectoryPath = Paths.get(outputDir).toAbsolutePath().normalize();
            Path outputFilePath = outputDirectoryPath.resolve(outputFileName);

            Files.createDirectories(outputDirectoryPath);

            AIQuestionGenerator generator = new AIQuestionGenerator();

            QuestionList existingQuestions = loadExistingQuestionsIfAny(outputFilePath);
            List<Question> existingQuestionList = existingQuestions != null && existingQuestions.getQuestionList() != null
                    ? new ArrayList<>(existingQuestions.getQuestionList())
                    : new ArrayList<>();

            sanitizeExistingQuestions(existingQuestionList);
            validateGeneratedQuestions(existingQuestionList);

            log.info("Preguntas existentes válidas encontradas: {}", existingQuestionList.size());

            List<String> alphabetLetters = new ArrayList<>(AlphabetMap.MAP.values()).stream()
                    .map(AIQuestionGeneratorRunner::normalizeLetter)
                    .filter(letter -> !letter.isBlank())
                    .distinct()
                    .collect(Collectors.toList());

            Map<String, Long> countsByLetter = existingQuestionList.stream()
                    .filter(q -> q != null)
                    .map(Question::getQuestionLetter)
                    .map(AIQuestionGeneratorRunner::normalizeLetter)
                    .filter(letter -> !letter.isBlank())
                    .collect(Collectors.groupingBy(
                            letter -> letter,
                            LinkedHashMap::new,
                            Collectors.counting()
                    ));

            List<String> missingLetters = alphabetLetters.stream()
                    .filter(letter -> countsByLetter.getOrDefault(letter, 0L) < generator.getQuestionsPerLetter())
                    .collect(Collectors.toList());

            log.info("Letras pendientes de completar: {}", missingLetters);

            List<Question> newQuestions = new ArrayList<>();

            if (!missingLetters.isEmpty()) {
                QuestionList generatedForMissing = generator.generateBatteryForMissingLetters(missingLetters);

                if (generatedForMissing != null && generatedForMissing.getQuestionList() != null) {
                    newQuestions.addAll(generatedForMissing.getQuestionList());
                }
            }

            sanitizeExistingQuestions(newQuestions);
            validateGeneratedQuestions(newQuestions);

            log.info("Preguntas nuevas generadas en esta ejecución: {}", newQuestions.size());

            List<Question> mergedQuestions = mergeQuestions(existingQuestionList, newQuestions, generator.getQuestionsPerLetter());
            validateGeneratedQuestions(mergedQuestions);

            QuestionList finalQuestionList = new QuestionList(mergedQuestions, mergedQuestions.size());
            generator.saveToFile(finalQuestionList, outputFilePath.toString());

            log.info("Archivo generado correctamente en: {}", outputFilePath);
            log.info("Total preguntas guardadas: {}", mergedQuestions.size());
            log.info("Directorio de salida: {}", outputDirectoryPath);
            log.info("Fichero de salida: {}", outputFilePath);

            Map<String, Long> finalCounts = mergedQuestions.stream()
                    .map(Question::getQuestionLetter)
                    .map(AIQuestionGeneratorRunner::normalizeLetter)
                    .collect(Collectors.groupingBy(
                            letter -> letter,
                            LinkedHashMap::new,
                            Collectors.counting()
                    ));

            for (String letter : alphabetLetters) {
                long count = finalCounts.getOrDefault(letter, 0L);
                if (count < generator.getQuestionsPerLetter()) {
                    log.warn("La letra '{}' sigue incompleta: {}/{}", letter, count, generator.getQuestionsPerLetter());
                } else {
                    log.info("La letra '{}' está completa: {}/{}", letter, count, generator.getQuestionsPerLetter());
                }
            }

        } catch (Exception e) {
            log.error("Error ejecutando AIQuestionGeneratorRunner", e);
            System.exit(1);
        }
    }

    private static QuestionList loadExistingQuestionsIfAny(Path outputFilePath) {
        try {
            if (!Files.exists(outputFilePath)) {
                return new QuestionList(new ArrayList<>(), 0);
            }

            String content = Files.readString(outputFilePath, StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) {
                return new QuestionList(new ArrayList<>(), 0);
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(content, QuestionList.class);

        } catch (Exception e) {
            log.warn("No se pudieron cargar preguntas existentes desde {}. Se continuará con lista vacía. Motivo: {}",
                    outputFilePath, e.getMessage());
            return new QuestionList(new ArrayList<>(), 0);
        }
    }

    private static void sanitizeExistingQuestions(List<Question> questions) {
        if (questions == null) {
            return;
        }

        for (Question q : questions) {
            if (q == null) {
                continue;
            }

            q.setQuestionLetter(normalizeLetter(q.getQuestionLetter()));

            if (q.getQuestionText() != null) {
                q.setQuestionText(repairAndTrim(q.getQuestionText()));
            }

            if (q.getQuestionResponsesList() != null) {
                List<String> repairedResponses = q.getQuestionResponsesList().stream()
                        .map(AIQuestionGeneratorRunner::repairAndTrim)
                        .collect(Collectors.toList());
                q.setQuestionResponsesList(repairedResponses);
            }
        }
    }

    private static List<Question> mergeQuestions(List<Question> existing, List<Question> incoming, int questionsPerLetter) {
        List<Question> safeExisting = existing != null ? existing : new ArrayList<>();
        List<Question> safeIncoming = incoming != null ? incoming : new ArrayList<>();

        Map<String, List<Question>> byLetter = new LinkedHashMap<>();

        for (Question q : safeExisting) {
            if (q == null) continue;
            String letter = normalizeLetter(q.getQuestionLetter());
            if (letter.isBlank()) continue;

            byLetter.computeIfAbsent(letter, k -> new ArrayList<>());

            if (!containsEquivalentQuestion(byLetter.get(letter), q) && byLetter.get(letter).size() < questionsPerLetter) {
                byLetter.get(letter).add(q);
            }
        }

        for (Question q : safeIncoming) {
            if (q == null) continue;
            String letter = normalizeLetter(q.getQuestionLetter());
            if (letter.isBlank()) continue;

            byLetter.computeIfAbsent(letter, k -> new ArrayList<>());

            if (!containsEquivalentQuestion(byLetter.get(letter), q) && byLetter.get(letter).size() < questionsPerLetter) {
                byLetter.get(letter).add(q);
            }
        }

        List<Question> merged = new ArrayList<>();
        for (List<Question> perLetter : byLetter.values()) {
            merged.addAll(perLetter);
        }

        return merged;
    }

    private static boolean containsEquivalentQuestion(List<Question> existing, Question candidate) {
        if (existing == null || candidate == null) {
            return false;
        }

        String candidateText = normalizeFreeText(candidate.getQuestionText());
        String candidateCorrect = getNormalizedCorrectAnswer(candidate);

        for (Question q : existing) {
            String existingText = normalizeFreeText(q.getQuestionText());
            String existingCorrect = getNormalizedCorrectAnswer(q);

            if (!candidateText.isBlank() && candidateText.equals(existingText)) {
                return true;
            }

            if (!candidateCorrect.isBlank() && candidateCorrect.equals(existingCorrect)) {
                return true;
            }
        }

        return false;
    }

    private static String getNormalizedCorrectAnswer(Question q) {
        if (q == null || q.getQuestionResponsesList() == null) {
            return "";
        }

        int idx = q.getCorrectQuestionIndex();
        if (idx < 0 || idx >= q.getQuestionResponsesList().size()) {
            return "";
        }

        return normalizeFreeText(q.getQuestionResponsesList().get(idx));
    }

    private static void validateGeneratedQuestions(List<Question> questions) {
        if (questions == null) {
            return;
        }

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);

            if (q == null) {
                throw new IllegalStateException("Pregunta nula en posición " + i);
            }
            if (q.getQuestionLetter() == null || q.getQuestionLetter().isBlank()) {
                throw new IllegalStateException("questionLetter vacío en posición " + i);
            }
            if (q.getQuestionText() == null || q.getQuestionText().isBlank()) {
                throw new IllegalStateException("questionText vacío en posición " + i);
            }
            if (q.getQuestionResponsesList() == null || q.getQuestionResponsesList().size() != 4) {
                throw new IllegalStateException("La pregunta " + i + " no tiene 4 respuestas");
            }
            if (q.getCorrectQuestionIndex() < 0 || q.getCorrectQuestionIndex() > 3) {
                throw new IllegalStateException("correctQuestionIndex inválido en posición " + i);
            }
        }
    }

    private static String repairAndTrim(String text) {
        if (text == null) {
            return "";
        }
        return repairMojibakeIfNeeded(text).trim();
    }

    private static String normalizeLetter(String input) {
        String repaired = repairAndTrim(input);
        if (repaired.isBlank()) {
            return "";
        }

        String lower = repaired.toLowerCase(Locale.ROOT).trim();

        if ("ñ".equals(lower) || "├▒".equals(lower) || "ã±".equals(lower) || "Ã±".equals(lower) || "┬ñ".equals(lower)) {
            return "ñ";
        }

        return normalizeFreeText(lower);
    }

    private static String normalizeFreeText(String text) {
        if (text == null) return "";
        String repaired = repairAndTrim(text);

        if (ENYE.equalsIgnoreCase(repaired)) {
            return ENYE;
        }

        return Normalizer.normalize(repaired.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static String repairMojibakeIfNeeded(String input) {
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

    private static boolean looksLikeMojibake(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return text.contains("Ã")
                || text.contains("Â")
                || text.contains("├")
                || text.contains("┬")
                || text.contains("�");
    }

    private static int scoreTextQuality(String text) {
        if (text == null || text.isBlank()) {
            return Integer.MIN_VALUE;
        }

        int score = 0;

        if (text.contains("Ã")) score -= 10;
        if (text.contains("Â")) score -= 10;
        if (text.contains("├")) score -= 12;
        if (text.contains("┬")) score -= 12;
        if (text.contains("�")) score -= 20;

        for (char c : text.toCharArray()) {
            if ("áéíóúÁÉÍÓÚñÑüÜ".indexOf(c) >= 0) {
                score += 3;
            }
        }

        return score;
    }

    private static String readEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}