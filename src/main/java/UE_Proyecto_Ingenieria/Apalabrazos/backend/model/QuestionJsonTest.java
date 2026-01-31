package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Arrays;

/**
 * Clase de prueba para verificar serialización/deserialización JSON
 * de la clase Question usando Jackson.
 */
public class QuestionJsonTest {
    public static void main(String[] args) {
        try {
            String r1 = "Respuesta A";
            String r2 = "Respuesta B";
            String r3 = "Respuesta C";
            String r4 = "Respuesta D";

            Question q = new Question("¿Cuál es la respuesta correcta?", Arrays.asList(r1, r2, r3, r4), 1, QuestionStatus.INIT, QuestionLevel.EASY, "a");

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
            e.printStackTrace();
            System.exit(1);
        }
    }
}
