package UE_Proyecto_Ingenieria.Apalabrazos.backend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionLevel;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionList;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Small utility to generate a JSON file with 100 random Questions
 * and wrap them into a QuestionList with max_length >= number of questions.
 */
public class QuestionJsonGenerator {
    private static final int NUM_QUESTIONS = 100;
    private static final Path OUTPUT_PATH = Paths.get(
            "src/main/resources/UE_Proyecto_Ingenieria/Apalabrazos/data/questions.json");

    public static void main(String[] args) throws IOException {
        List<Question> questions = new ArrayList<>(NUM_QUESTIONS);
        Random random = new Random();

        for (int i = 1; i <= NUM_QUESTIONS; i++) {
            String text = "Question #" + i + ": choose the correct option";
            List<String> responses = generateResponses(i);
            int correctIndex = random.nextInt(4);

            Question q = new Question(
                    text,
                    responses,
                    correctIndex,
                    QuestionStatus.INIT,
                    QuestionLevel.EASY,
                    "a"
            );
            questions.add(q);
        }

        QuestionList list = new QuestionList(questions, NUM_QUESTIONS); // max_length = 100

        // Ensure directory exists
        Files.createDirectories(OUTPUT_PATH.getParent());

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(OUTPUT_PATH.toFile(), list);

        // Optional: read back to verify
        QuestionList readBack = mapper.readValue(OUTPUT_PATH.toFile(), QuestionList.class);
        System.out.println("Generated " + readBack.getCurrentLength() + " questions at: " + OUTPUT_PATH);
    }

    private static List<String> generateResponses(int seed) {
        // Simple deterministic responses based on seed to keep under 128 chars
        List<String> responses = new ArrayList<>(4);
        responses.add("Option A - seed " + seed);
        responses.add("Option B - seed " + seed);
        responses.add("Option C - seed " + seed);
        responses.add("Option D - seed " + seed);
        return responses;
    }
}
