package UE_Proyecto_Ingenieria.Apalabrazos.backend.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionList;

import java.io.File;
import java.io.IOException;

/**
 * Clase encargada de cargar las preguntas desde un archivo JSON.
 * Utiliza la librería Jackson para convertir el JSON en objetos Java.
 */
public class QuestionFileLoader {

    // Ruta por defecto del archivo de preguntas
    private String archivoPreguntas = "src/main/resources/UE_Proyecto_Ingenieria/Apalabrazos/data/questions.json";

    /**
     * Carga las preguntas desde el archivo por defecto.
     *
     * @return Lista de preguntas cargadas desde el archivo JSON
     * @throws IOException Si hay un error al leer el archivo (no existe, permisos, etc.)
     */
    public QuestionList loadQuestions() throws IOException {
        // Llamamos al otro método usando la ruta por defecto
        return loadQuestions(archivoPreguntas);
    }

    /**
     * Carga las preguntas desde un archivo específico.
     *
     * @param rutaArchivo Ruta completa del archivo JSON a cargar
     * @return Lista de preguntas cargadas desde el archivo JSON
     * @throws IOException Si hay un error al leer el archivo (no existe, permisos, etc.)
     */
    public QuestionList loadQuestions(String rutaArchivo) throws IOException {
        // Creamos el objeto que convierte JSON a Java
        ObjectMapper conversorJSON = new ObjectMapper();
        File archivo = new File(rutaArchivo);
        QuestionList preguntas = conversorJSON.readValue(archivo, QuestionList.class);
        // Devolvemos las preguntas cargadas
        return preguntas;
    }
}
