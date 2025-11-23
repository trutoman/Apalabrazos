package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Representa una lista depregunta del juego Apalabrazos.
 */
public class QuestionList implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Longitud fija permitida para la lista de preguntas (0..26). */
    public static final int LENGTH = 27;

    /**
     * Longitud máxima permitida para esta instancia. Se fija en el constructor
     * y no tiene setter. Si no se pasa, toma el valor por defecto `LENGTH`.
     */
    private final int max_length;

    private final List<Question> questionList = new ArrayList<>();

    public QuestionList() {
        this.max_length = LENGTH;
    }

    @JsonCreator
    public QuestionList(@JsonProperty("questionList") List<Question> questions,
                        @JsonProperty("max_length") Integer max_length) {
        this.max_length = (max_length == null) ? LENGTH : max_length.intValue();
        setQuestionList(questions);
    }

    public List<Question> getQuestionList() {
        // Devuelve una copia para no exponer la lista interna.
        return List.copyOf(questionList);
    }

    public void setQuestionList(List<Question> questions) {
        this.questionList.clear();
        if (questions == null) {
            throw new IllegalArgumentException("questions list cannot be null");
        }
        if (questions.size() > this.max_length) {
            throw new IllegalArgumentException(
                    "questions list cannot have more than " + this.max_length + " items");
        }
        this.questionList.addAll(questions);
    }

    public Question getQuestionAt(int index) {
        if (index < 0 || index >= questionList.size()) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index
                    + ". Valid range is 0.." + (questionList.size() - 1));
        }
        Question question = questionList.get(index);
        return question;
    }

    /**
     * Añade una pregunta siempre que no  exceda la longitud máxima `LENGTH`.
     *
     * @param q pregunta a añadir
     */

    public void addQuestion(Question q) {
        // sugerencia LLM en una sola linea se encarga en caso de null de lanzar la excepcion adecuada
        Objects.requireNonNull(q, "question cannot be null");
        if (this.questionList.size() >= this.max_length) {
            throw new IllegalStateException("Cannot add more than " + this.max_length + " questions");
        }
        this.questionList.add(q);
    }

    /**
     * Devuelve la longitud actual de la lista de preguntas (número de elementos
     * almacenados en esta instancia).
     *
     * @return número de preguntas actualmente en la lista
     */
    @JsonIgnore
    public int getCurrentLength() {
        return this.questionList.size();
    }

    /**
     * Devuelve la longitud máxima configurada para esta instancia.
     *
     * @return longitud máxima (por defecto `LENGTH`)
     */
    @JsonProperty("max_length")
    public int getMax_length() {
        return this.max_length;
    }
}
