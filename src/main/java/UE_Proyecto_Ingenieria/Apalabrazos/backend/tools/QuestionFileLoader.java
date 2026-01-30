package UE_Proyecto_Ingenieria.Apalabrazos.backend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionList;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionLevel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase encargada de cargar las preguntas desde un archivo JSON.
 * Utiliza la librería Jackson para convertir el JSON en objetos Java.
 */
public class QuestionFileLoader {

    // Ruta por defecto del archivo de preguntas
    private String defaultQuestionsFile = "src/main/resources/UE_Proyecto_Ingenieria/Apalabrazos/data/questions.json";

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
        // Take the first "count" questions
        java.util.List<UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question> subset =
                all.getQuestionList().subList(0, count);
        // Create a QuestionList with limit equal to "count"
        return new UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionList(subset, count);
    }

    /**
     * Versión usando la ruta por defecto del archivo.
     */
    public QuestionList loadQuestions(int count) throws IOException {
        return this.loadQuestions(defaultQuestionsFile, count);
    }

    /**
     * Carga 27 preguntas filtradas por nivel de dificultad.
     * Como el JSON contiene 81 preguntas organizadas en grupos de 3 por letra (easy, medium, hard),
     * este método selecciona una pregunta por letra según el nivel especificado.
     *
     * @param level Nivel de dificultad deseado (EASY, MEDIUM, HARD)
     * @return QuestionList con 27 preguntas (una por letra del alfabeto)
     * @throws IOException Si hay problemas leyendo el archivo
     */
    public QuestionList loadQuestionsByLevel(QuestionLevel level) throws IOException {
        return loadQuestionsByLevel(defaultQuestionsFile, level);
    }

    /**
     * Carga 27 preguntas filtradas por nivel de dificultad desde un archivo específico.
     * 
     * La estructura del JSON debe ser: 81 preguntas organizadas en grupos de 3:
     * - Índices 0-2: Letra A (easy, medium, hard)
     * - Índices 3-5: Letra B (easy, medium, hard)
     * - ...
     * - Índices 78-80: Letra Z (easy, medium, hard)
     *
     * @param filePath Ruta del archivo JSON
     * @param level Nivel de dificultad deseado
     * @return QuestionList con 27 preguntas (una por letra)
     * @throws IOException Si hay problemas leyendo el archivo
     */
    public QuestionList loadQuestionsByLevel(String filePath, QuestionLevel level) throws IOException {
        QuestionList all = loadQuestions(filePath);
        List<Question> allQuestions = all.getQuestionList();
        
        if (allQuestions.size() < 81) {
            throw new IllegalArgumentException(
                    "El archivo debe contener al menos 81 preguntas (27 letras × 3 niveles), pero solo contiene " 
                    + allQuestions.size());
        }

        List<Question> filtered = new ArrayList<>();
        
        // Determinar el offset según el nivel: trivial=0, easy=1, difficult=2
        int offset;
        switch (level) {
            case TRIVIAL:
                offset = 0;
                break;
            case EASY:
                offset = 1;
                break;
            case DIFFICULT:
                offset = 2;
                break;
            default:
                offset = 1; // Por defecto EASY
        }

        // Seleccionar una pregunta por cada letra (27 letras)
        for (int letterIndex = 0; letterIndex < 27; letterIndex++) {
            int questionIndex = letterIndex * 3 + offset;
            if (questionIndex < allQuestions.size()) {
                filtered.add(allQuestions.get(questionIndex));
            }
        }

        if (filtered.size() != 27) {
            throw new IllegalStateException(
                    "Error al filtrar preguntas: se esperaban 27 preguntas pero se obtuvieron " + filtered.size());
        }

        return new QuestionList(filtered, 27);
    }
}
