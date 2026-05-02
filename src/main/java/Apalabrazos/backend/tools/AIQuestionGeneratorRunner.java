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
import java.util.*;
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

            log.info("Preguntas existentes válidas: {}", existingQuestionList.size());

            // ✅ ALFABETO CORRECTO Y FIJO
            List<String> alphabetLetters = buildSpanishAlphabet();

            // Conteo actual
            Map<String, Long> countsByLetter = existingQuestionList.stream()
                    .filter(Objects::nonNull)
                    .map(Question::getQuestionLetter)
                    .map(AIQuestionGeneratorRunner::normalizeLetter)
                    .filter(letter -> !letter.isBlank())
                    .collect(Collectors.groupingBy(
                            letter -> letter,
                            LinkedHashMap::new,
                            Collectors.counting()
                    ));

            // Letras pendientes
            List<String> missingLetters = alphabetLetters.stream()
                    .filter(letter -> countsByLetter.getOrDefault(letter, 0L) < generator.getQuestionsPerLetter())
                    .collect(Collectors.toList());

            log.info("Letras pendientes: {}", missingLetters);

            List<Question> newQuestions = new ArrayList<>();

            if (!missingLetters.isEmpty()) {
                QuestionList generatedForMissing = generator.generateBatteryForMissingLetters(missingLetters);

                if (generatedForMissing != null && generatedForMissing.getQuestionList() != null) {
                    newQuestions.addAll(generatedForMissing.getQuestionList());
                }
            }

            sanitizeExistingQuestions(newQuestions);
            validateGeneratedQuestions(newQuestions);

            log.info("Preguntas nuevas generadas: {}", newQuestions.size());

            // Merge final
            List<Question> mergedQuestions = mergeQuestions(existingQuestionList, newQuestions, generator.getQuestionsPerLetter());
            validateGeneratedQuestions(mergedQuestions);

            QuestionList finalQuestionList = new QuestionList(mergedQuestions, mergedQuestions.size());
            generator.saveToFile(finalQuestionList, outputFilePath.toString());

            log.info("Archivo generado en: {}", outputFilePath);
            log.info("Total preguntas guardadas: {}", mergedQuestions.size());

            // Conteo final
            Map<String, Long> finalCounts = mergedQuestions.stream()
                    .map(Question::getQuestionLetter)
                    .map(AIQuestionGeneratorRunner::normalizeLetter)
                    .collect(Collectors.groupingBy(
                            letter -> letter,
                            LinkedHashMap::new,
                            Collectors.counting()
                    ));

            // ✅ RESUMEN LIMPIO
            log.info("========== RESUMEN FINAL ==========");

            for (String letter : alphabetLetters) {
                long count = finalCounts.getOrDefault(letter, 0L);

                if (count < generator.getQuestionsPerLetter()) {
                    log.warn("Letra '{}' incompleta: {}/{}", letter, count, generator.getQuestionsPerLetter());
                } else {
                    log.info("Letra '{}' completa: {}/{}", letter, count, generator.getQuestionsPerLetter());
                }
            }

            log.info("===================================");

        } catch (Exception e) {
            log.error("Error ejecutando AIQuestionGeneratorRunner", e);
            System.exit(1);
        }
    }

    // ✅ ALFABETO ESPAÑOL CORRECTO
    private static List<String> buildSpanishAlphabet() {
        return List.of(
                "a","b","c","d","e","f","g","h","i","j","k","l","m",
                "n","ñ","o","p","q","r","s","t","u","v","w","x","y","z"
        );
    }

    private static QuestionList loadExistingQuestionsIfAny(Path path) {
        try {
            if (!Files.exists(path)) return new QuestionList(new ArrayList<>(), 0);

            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.isBlank()) return new QuestionList(new ArrayList<>(), 0);

            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(content, QuestionList.class);

        } catch (Exception e) {
            log.warn("Error leyendo fichero previo: {}", e.getMessage());
            return new QuestionList(new ArrayList<>(), 0);
        }
    }

    private static List<Question> mergeQuestions(List<Question> existing, List<Question> incoming, int maxPerLetter) {
        Map<String, List<Question>> map = new LinkedHashMap<>();

        for (Question q : existing) {
            String l = normalizeLetter(q.getQuestionLetter());
            map.computeIfAbsent(l, k -> new ArrayList<>());
            if (map.get(l).size() < maxPerLetter) map.get(l).add(q);
        }

        for (Question q : incoming) {
            String l = normalizeLetter(q.getQuestionLetter());
            map.computeIfAbsent(l, k -> new ArrayList<>());
            if (map.get(l).size() < maxPerLetter) map.get(l).add(q);
        }

        return map.values().stream().flatMap(List::stream).toList();
    }

    private static void validateGeneratedQuestions(List<Question> questions) {
        for (Question q : questions) {
            if (q.getQuestionResponsesList().size() != 4) {
                throw new IllegalStateException("Pregunta sin 4 respuestas");
            }
        }
    }

    private static void sanitizeExistingQuestions(List<Question> questions) {
        for (Question q : questions) {
            q.setQuestionLetter(normalizeLetter(q.getQuestionLetter()));
        }
    }

    private static String normalizeLetter(String text) {
        if (text == null) return "";
        text = repairMojibakeIfNeeded(text).toLowerCase().trim();

        if (text.contains("ñ")) return "ñ";

        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static String repairMojibakeIfNeeded(String input) {
        if (input == null) return "";

        for (Charset cs : MOJIBAKE_SOURCE_CHARSETS) {
            try {
                String fixed = new String(input.getBytes(cs), StandardCharsets.UTF_8);
                if (!fixed.equals(input)) return fixed;
            } catch (Exception ignored) {}
        }

        return input;
    }

    private static String readEnv(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}