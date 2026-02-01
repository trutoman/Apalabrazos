package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a question in the Apalabrazos game.
 * Contains the question text, list of possible answers, correct answer index, status and level.
 */
public class Question implements Serializable {
    private static final long serialVersionUID = 1L;

    private String questionText;
    private final List<String> questionResponsesList = new ArrayList<>();
    private int correctQuestionIndex = -1;
    private QuestionStatus questionStatus = QuestionStatus.INIT;
    private QuestionLevel questionLevel = QuestionLevel.EASY;
    private String questionLetter = "a";

    /**
     * Default constructor
     */
    public Question() {
    }

    /**
     * Constructor with all fields
     * @param questionText The question text (max 128 characters)
     * @param responses List of 4 possible answers
     * @param correctIndex Index of the correct answer (0-3)
     * @param status The question status
     * @param level The question difficulty level
     */
    @JsonCreator
    public Question(@JsonProperty("questionText") String questionText,
                    @JsonProperty("questionResponsesList") List<String> responses,
                    @JsonProperty("correctQuestionIndex") int correctIndex,
                    @JsonProperty(value = "questionStatus", required = true) QuestionStatus status,
                    @JsonProperty(value = "questionLevel", required = true) QuestionLevel level,
                    @JsonProperty(value = "questionLetter", required = true) String questionLetter) {
        setQuestionText(questionText);
        setQuestionResponsesList(responses);
        setCorrectQuestionIndex(correctIndex);
        if (status == null) {
            throw new IllegalArgumentException("questionStatus cannot be null");
        }
        this.questionStatus = status;
        if (level == null) {
            throw new IllegalArgumentException("questionLevel cannot be null");
        }
        this.questionLevel = level;
        this.questionLetter = questionLetter;
    }

    /**
     * Constructor without status or level (defaults to INIT and EASY)
     * @param questionText The question text
     * @param responses List of 4 possible answers
     * @param correctIndex Index of the correct answer (0-3)
     */
    public Question(String questionText, List<String> responses, int correctIndex) {
        this(questionText, responses, correctIndex, QuestionStatus.INIT, QuestionLevel.EASY, "a");
    }

    /**
     * Get the question text
     * @return The question text
     */
    @JsonProperty("questionText")
    public String getQuestionText() {
        return questionText;
    }

    /**
     * Set the question text
     * @param questionText The question text (max 128 characters)
     */
    @JsonProperty("questionText")
    public void setQuestionText(String questionText) {
        if (questionText == null) {
            throw new IllegalArgumentException("questionText cannot be null");
        }
        if (questionText.length() > 128) {
            throw new IllegalArgumentException("questionText max 128 characters");
        }
        this.questionText = questionText;
    }

    /**
     * Get the list of possible answers
     * @return Unmodifiable list of answers
     */
    @JsonProperty("questionResponsesList")
    public List<String> getQuestionResponsesList() {
        return Collections.unmodifiableList(questionResponsesList);
    }

    /**
     * Set the list of possible answers
     * @param responses List of exactly 4 answers (max 128 characters each)
     */
    @JsonProperty("questionResponsesList")
    public void setQuestionResponsesList(List<String> responses) {
        this.questionResponsesList.clear();
        if (responses == null) {
            throw new IllegalArgumentException("responses list cannot be null");
        }
        if (responses.size() != 4) {
            throw new IllegalArgumentException("responses list must contain exactly 4 responses");
        }
        // valida cada string respuesta
        for (String r : responses) {
            if (r == null) {
                throw new IllegalArgumentException("response entries cannot be null");
            }
            if (r.length() > 128) {
                throw new IllegalArgumentException("each response text cannot exceed 128 characters");
            }
        }
        this.questionResponsesList.addAll(responses);
    }

    /**
     * Get the index of the correct answer
     * @return The correct answer index (0-3)
     */
    @JsonProperty("correctQuestionIndex")
    public int getCorrectQuestionIndex() {
        return correctQuestionIndex;
    }

    /**
     * Set the index of the correct answer
     * @param correctQuestionIndex The correct answer index (0-3)
     */
    @JsonProperty("correctQuestionIndex")
    public void setCorrectQuestionIndex(int correctQuestionIndex) {
        if (correctQuestionIndex < 0 || correctQuestionIndex >= questionResponsesList.size()) {
            throw new IndexOutOfBoundsException("correctQuestionIndex out of range");
        }
        this.correctQuestionIndex = correctQuestionIndex;
    }

    /**
     * Get the question status
     * @return The current status
     */
    @JsonProperty("questionStatus")
    public QuestionStatus getQuestionStatus() {
        return questionStatus;
    }

    /**
     * Set the question status
     * @param questionStatus The new status
     */
    @JsonProperty("questionStatus")
    public void setQuestionStatus(QuestionStatus questionStatus) {
        if (questionStatus == null) {
            throw new IllegalArgumentException("questionStatus cannot be null");
        }
        this.questionStatus = questionStatus;
    }

    /**
     * Get the question difficulty level
     * @return The difficulty level
     */
    @JsonProperty("questionLevel")
    public QuestionLevel getQuestionLevel() {
        return questionLevel;
    }

    /**
     * Set the question difficulty level
     * @param questionLevel The new difficulty level
     */
    @JsonProperty("questionLevel")
    public void setQuestionLevel(QuestionLevel questionLevel) {
        if (questionLevel == null) {
            throw new IllegalArgumentException("questionLevel cannot be null");
        }
        this.questionLevel = questionLevel;
    }

    /**
     * Get the question letter
     * @return The question letter
     */
    @JsonProperty("questionLetter")
    public String getQuestionLetter() {
        return questionLetter;
    }

    /**
     * Set the question letter
     * @param questionLetter The letter associated with the question
     */
    @JsonProperty("questionLetter")
    public void setQuestionLetter(String questionLetter) {
        if (questionLetter == null || questionLetter.isEmpty()) {
            throw new IllegalArgumentException("questionLetter cannot be null or empty");
        }
        this.questionLetter = questionLetter;
    }

    /**
     * Comprueba si el índice dado corresponde a la respuesta correcta.
     */
    public boolean isCorrectIndex(int index) {
        return index == correctQuestionIndex;
    }

    /**
     * Get the correct answer text
     * @return The correct answer or null if not set
     */
    @JsonIgnore
    public String getCorrectResponse() {
        if (correctQuestionIndex < 0) {
            return null;
        }
        return questionResponsesList.get(correctQuestionIndex);
    }

    // Com probablemente en un futuro querremos comparar objetos Question,
    // y tambien utilizaremos esta clase dentro de colecciones que requieren
    // equals y hashCode correctamente implementados, como HashMap o HashSet.
    // Dejamos implementados ya estos metodos

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Question question = (Question) o;
        return correctQuestionIndex == question.correctQuestionIndex &&
                Objects.equals(questionText, question.questionText) &&
                Objects.equals(questionResponsesList, question.questionResponsesList) &&
                questionStatus == question.questionStatus &&
                questionLevel == question.questionLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionText, questionResponsesList, correctQuestionIndex, questionStatus, questionLevel);
    }

    // El metodo toString lo utilizaremos durante el desarrollo y depuracion del codigo.
    @Override
    public String toString() {
        return "Question{" +
                "questionText='" + questionText + '\'' +
                ", responses=" + questionResponsesList +
                ", correctIndex=" + correctQuestionIndex +
                ", status=" + questionStatus +
                ", level=" + questionLevel +
                '}';
    }
}
