package Apalabrazos.backend.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Clase de prueba para verificar serialización/deserialización JSON
 * de la clase Question usando Jackson.
 */
public class QuestionJsonTest {
    private static final Logger log = LoggerFactory.getLogger(QuestionJsonTest.class);

    public static void main(String[] args) {
        try {
            String r1 = "Respuesta A";
            String r2 = "Respuesta B";
            String r3 = "Respuesta C";
            String r4 = "Respuesta D";

            Question q = new Question("¿Cuál es la respuesta correcta?", Arrays.asList(r1, r2, r3, r4), 1,
                    QuestionStatus.INIT, QuestionLevel.MEDIUM, "a", "init");

            ObjectMapper m = new ObjectMapper();
            m.enable(SerializationFeature.INDENT_OUTPUT);

            String json = m.writeValueAsString(q);
            System.out.println("Serialized JSON:\n" + json);

            Question des = m.readValue(json, Question.class);
            System.out.println("\nDeserialized Question: " + des);

            boolean equal = q.equals(des);
            System.out.println("Objects equal: " + equal);
            if (!equal) {
                throw new IllegalStateException("Deserialized object is not equal to original");
            }
        } catch (Exception e) {
            log.error("QuestionJsonTest failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
