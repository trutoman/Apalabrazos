package Apalabrazos.backend.tools;

import Apalabrazos.backend.model.Question;
import Apalabrazos.backend.model.QuestionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class AIQuestionGeneratorRunner {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionGeneratorRunner.class);

    public static void main(String[] args) {
        try {
            log.info("RUNNER ARRANCADO");

            String outputDir = readEnv("AI_GENERATOR_OUTPUT_DIR", "src/main/resources/Apalabrazos/data");
            String outputFileName = readEnv("AI_GENERATOR_FILENAME", "questions2.generated.json");

            Path outputDirectoryPath = Paths.get(outputDir).toAbsolutePath().normalize();
            Path outputFilePath = outputDirectoryPath.resolve(outputFileName);

            Files.createDirectories(outputDirectoryPath);

            AIQuestionGenerator generator = new AIQuestionGenerator();
            QuestionList generatedQuestions = generator.generateFullBattery();

            List<Question> questions = generatedQuestions.getQuestionList();

            if (generatedQuestions == null || questions == null || questions.isEmpty()) {
                throw new IllegalStateException("No se generaron preguntas válidas");
            }

            validateGeneratedQuestions(questions);

            generator.saveToFile(generatedQuestions, outputFilePath.toString());

            log.info("Archivo generado correctamente en: {}", outputFilePath);
            log.info("Total preguntas generadas: {}", questions.size());

        } catch (Exception e) {
            log.error("Error ejecutando AIQuestionGeneratorRunner", e);
            System.exit(1);
        }
    }

    private static void validateGeneratedQuestions(List<Question> questions) {
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

    private static String readEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}