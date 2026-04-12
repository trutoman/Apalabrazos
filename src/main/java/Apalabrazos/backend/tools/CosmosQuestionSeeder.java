package Apalabrazos.backend.tools;

import Apalabrazos.backend.config.CosmosDBConfig;
import Apalabrazos.backend.model.Question;
import Apalabrazos.backend.model.QuestionList;
import com.azure.cosmos.CosmosContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Seed de preguntas hacia Azure Cosmos DB usando un JSON local del proyecto.
 *
 * Variables de entorno soportadas:
 * - QUESTIONS_SEED_FILE (default: classpath:/Apalabrazos/data/questions2.json)
 * - COSMOS_DB_QUESTIONS_CONTAINER (default: Questions)
 */
public class CosmosQuestionSeeder {

    private static final Logger log = LoggerFactory.getLogger(CosmosQuestionSeeder.class);
    private static final String DEFAULT_FILE = "classpath:/Apalabrazos/data/questions2.json";

    private CosmosQuestionSeeder() {
    }

    public static void main(String[] args) {
        try {
            int upserts = seedQuestions();
            log.info("Question seeding finalizado. Total upserts: {}", upserts);
        } catch (Exception e) {
            log.error("Error ejecutando seeding de preguntas: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    public static int seedQuestions() throws Exception {
        String inputFile = readEnv("QUESTIONS_SEED_FILE", DEFAULT_FILE);
        String containerName = readEnv("COSMOS_DB_QUESTIONS_CONTAINER", "Questions");

        ObjectMapper mapper = new ObjectMapper();
        QuestionList list = loadQuestionList(mapper, inputFile);
        CosmosContainer container = CosmosDBConfig.getContainer(containerName);

        int count = 0;
        for (Question q : list.getQuestionList()) {
            String id = buildStableId(q);

            ObjectNode doc = mapper.valueToTree(q);
            doc.put("id", id);
            doc.put("type", "question");
            doc.put("source", "questions2.json");

            container.upsertItem(doc);
            count++;
        }

        return count;
    }

    private static QuestionList loadQuestionList(ObjectMapper mapper, String source) throws Exception {
        if (source.startsWith("classpath:")) {
            String classpathPath = source.substring("classpath:".length());
            try (InputStream in = CosmosQuestionSeeder.class.getResourceAsStream(classpathPath)) {
                if (in == null) {
                    throw new IllegalStateException("No se encontró recurso en classpath: " + classpathPath);
                }
                return mapper.readValue(in, QuestionList.class);
            }
        }

        return mapper.readValue(new java.io.File(source), QuestionList.class);
    }

    private static String readEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static String buildStableId(Question q) {
        try {
            String base = q.getQuestionLetter() + "|" + q.getQuestionText();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(base.getBytes(StandardCharsets.UTF_8));
            return "q_" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (Exception ex) {
            return "q_" + Math.abs((q.getQuestionLetter() + q.getQuestionText()).hashCode());
        }
    }
}
