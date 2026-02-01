package UE_Proyecto_Ingenieria.Apalabrazos.backend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.AlphabetMap;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionList;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Clase encargada de cargar las preguntas desde un archivo JSON.
 * Utiliza la librería Jackson para convertir el JSON en objetos Java.
 */
public class QuestionFileLoader {

    // Ruta por defecto del archivo de preguntas
    private String defaultQuestionsFile = "src/main/resources/UE_Proyecto_Ingenieria/Apalabrazos/data/questions2.json";

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

    public List<Question> selectQuestionByLetter (QuestionList all) {
        java.util.List<UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question> selectedQuestions = new java.util.ArrayList<>();
        java.util.List<Integer> orderedKeys = new java.util.ArrayList<>(AlphabetMap.MAP.keySet());
        java.util.Collections.sort(orderedKeys);
        for (Integer letter : orderedKeys) {
            String letterStr = AlphabetMap.MAP.get(letter);
            for (UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question q : all.getQuestionList()) {
                if (q.getQuestionLetter().toLowerCase().equals(letterStr.toLowerCase())) {
                    selectedQuestions.add(q);
                    break;
                }
            }
        }
        return selectedQuestions;
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
        // java.util.List<UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question> subset =
        //        all.getQuestionList().subList(0, count);
        // Create a QuestionList with limit equal to "count"

        java.util.List<UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question> subset = selectQuestionByLetter(all);
        return new UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionList(subset, count);
    }

    /**
     * Versión usando la ruta por defecto del archivo.
     */
    public QuestionList loadQuestions(int count) throws IOException {
        return this.loadQuestions(defaultQuestionsFile, count);
    }
}
