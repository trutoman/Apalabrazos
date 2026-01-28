package UE_Proyecto_Ingenieria.Apalabrazos.backend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionList;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Clase encargada de cargar las preguntas desde un archivo JSON.
 * Utiliza la librería Jackson para convertir el JSON en objetos Java.
 */
public class QuestionFileLoader {

    // Ruta por defecto del archivo de preguntas
    private String defaultQuestionsFile = "src/main/resources/UE_Proyecto_Ingenieria/Apalabrazos/data/questions.json";

    /**
     * En el JSON de la rama de Alejandro hay 3 preguntas por letra (A..Z incluyendo Ñ):
     * 27 letras * 3 = 81 preguntas.
     *
     * Para construir un rosco, necesitamos 1 pregunta por letra, manteniendo el orden
     * de las letras (índice 0..N-1) para que el GameController pinte correctamente el rosco.
     */
    private static final int DEFAULT_QUESTIONS_PER_LETTER = 3;
    private final Random rng = new Random();

    /**
     * Carga las preguntas desde el archivo por defecto.
     *
     * @return Lista de preguntas cargadas desde el archivo JSON
     * @throws IOException Si hay un error al leer el archivo (no existe, permisos, etc.)
     */
    public QuestionList loadQuestions() throws IOException {
        // Use the default file path
        return loadQuestions(defaultQuestionsFile);
    }

    /**
     * Carga las preguntas desde un archivo específico.
     *
     * @param rutaArchivo Ruta completa del archivo JSON a cargar
     * @return Lista de preguntas cargadas desde el archivo JSON
     * @throws IOException Si hay un error al leer el archivo (no existe, permisos, etc.)
     */
    public QuestionList loadQuestions(String filePath) throws IOException {
        // Create JSON -> Java converter
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(filePath);
        QuestionList questions = objectMapper.readValue(file, QuestionList.class);
        // Return loaded questions
        return questions;
    }

    /**
     * Carga exactamente "numero" preguntas del archivo indicado.
     * Si el archivo contiene menos de ese número, lanza excepción.
     *
     * @param rutaArchivo Ruta del JSON de preguntas
     * @param numero Número exacto de preguntas a cargar
     * @return Una nueva instancia de QuestionList con exactamente "numero" preguntas
     * @throws IOException Si hay problemas leyendo el archivo
     * @throws IllegalArgumentException Si el archivo no contiene suficientes preguntas
     */
    public QuestionList loadQuestions(String filePath, int count) throws IOException {
        if (count <= 0) {
            throw new IllegalArgumentException("El número de preguntas debe ser mayor que 0");
        }
        QuestionList all = loadQuestions(filePath);
        int available = all.getCurrentLength();
        if (available < count) {
            throw new IllegalArgumentException(
                    "El archivo solo contiene " + available + " preguntas, se solicitaron " + count);
        }

        // Caso ROSCO (1 pregunta por letra) -> si tenemos al menos count * 3 preguntas,
        // asumimos el formato "bloques" (3 por letra) y elegimos 1 aleatoria por bloque.
        if (available >= count * DEFAULT_QUESTIONS_PER_LETTER) {
            List<Question> picked = pickOnePerLetter(all.getQuestionList(), count, DEFAULT_QUESTIONS_PER_LETTER);
            return new QuestionList(picked, count);
        }

        // Fallback: comportamiento anterior (coger las primeras "count")
        List<Question> subset = all.getQuestionList().subList(0, count);
        return new QuestionList(subset, count);
    }

    /**
     * Versión usando la ruta por defecto del archivo.
     */
    public QuestionList loadQuestions(int count) throws IOException {
        return this.loadQuestions(defaultQuestionsFile, count);
    }

    /**
     * Selecciona 1 pregunta aleatoria por letra.
     *
     * @param allQuestions          lista completa (esperada en bloques por letra)
     * @param lettersCount          número de letras/preguntas del rosco
     * @param questionsPerLetter    tamaño de cada bloque (por defecto 3)
     */
    private List<Question> pickOnePerLetter(List<Question> allQuestions, int lettersCount, int questionsPerLetter) {
        if (allQuestions == null) {
            return Collections.emptyList();
        }
        List<Question> out = new ArrayList<>(lettersCount);
        for (int letterIndex = 0; letterIndex < lettersCount; letterIndex++) {
            int start = letterIndex * questionsPerLetter;
            int end = start + questionsPerLetter;
            if (end > allQuestions.size()) {
                // Si el fichero no tiene suficientes, paramos para evitar IndexOutOfBounds
                break;
            }
            List<Question> block = allQuestions.subList(start, end);
            Question chosen = block.get(rng.nextInt(block.size()));
            out.add(chosen);
        }
        return out;
    }
}
