package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
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

    /**
     * Default constructor with default maximum length
     */
    public QuestionList() {
        this.max_length = LENGTH;
    }

    /**
     * Constructor with custom maximum length
     * @param questions Initial list of questions
     * @param max_length Maximum length for the list (defaults to LENGTH if null)
     */
    @JsonCreator
    public QuestionList(@JsonProperty("questionList") List<Question> questions,
                        @JsonProperty("max_length") Integer max_length) {
        this.max_length = (max_length == null) ? LENGTH : max_length.intValue();
        setQuestionList(questions);
    }

    /**
     * Get the list of questions
     * @return Immutable copy of the question list
     */
    public List<Question> getQuestionList() {
        // Devuelve una copia para no exponer la lista interna.
        return List.copyOf(questionList);
    }

    /**
     * Set the list of questions
     * @param questions List of questions (cannot exceed max_length)
     */
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

    /**
     * Get the question at the specified index
     * @param index The index of the question
     * @return The question at the specified index
     */
    public Question getQuestionAt(int index) {
        if (index < 0 || index >= questionList.size()) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index
                    + ". Valid range is 0.." + (questionList.size() - 1));
        }
        Question question = questionList.get(index);
        return question;
    }

    /**
     * Add a question to the list if maximum length not exceeded
     * @param q The question to add
     */
    public void addQuestion(Question q) {
        Objects.requireNonNull(q, "question cannot be null");
        if (this.questionList.size() >= this.max_length) {
            throw new IllegalStateException("Cannot add more than " + this.max_length + " questions");
        }
        this.questionList.add(q);
    }

    /**
     * Get the current number of questions in the list
     * @return The number of questions currently stored
     */
    @JsonIgnore
    public int getCurrentLength() {
        return this.questionList.size();
    }

    /**
     * Get the maximum length configured for this instance
     * @return The maximum length (defaults to LENGTH)
     */
    @JsonProperty("max_length")
    public int getMax_length() {
        return this.max_length;
    }
}
