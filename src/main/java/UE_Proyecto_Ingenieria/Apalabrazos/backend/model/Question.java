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
 * Representa una pregunta del juego Apalabrazos.
 * Contiene el texto de la pregunta, la lista de respuestas posibles,
 * el índice de la respuesta correcta, el estado y el nivel.
 */
public class Question implements Serializable {
    private static final long serialVersionUID = 1L;

    private String questionText; // max 128 chars
    private final List<String> questionResponsesList = new ArrayList<>();
    private int correctQuestionIndex = -1;
    private QuestionStatus questionStatus = QuestionStatus.INIT;
    private QuestionLevel questionLevel = QuestionLevel.EASY;

    public Question() {
        // constructor por defecto
    }

    // Constructor basico con todos los campos
    @JsonCreator
    public Question(@JsonProperty("questionText") String questionText,
                    @JsonProperty("questionResponsesList") List<String> responses,
                    @JsonProperty("correctQuestionIndex") int correctIndex,
                    @JsonProperty(value = "questionStatus", required = true) QuestionStatus status,
                    @JsonProperty(value = "questionLevel", required = true) QuestionLevel level) {
        setQuestionText(questionText);
        setQuestionResponsesList(responses);
        setCorrectQuestionIndex(correctIndex);
        if (status == null)
            throw new IllegalArgumentException("questionStatus cannot be null");
        this.questionStatus = status;
        if (level == null)
            throw new IllegalArgumentException("questionLevel cannot be null");
        this.questionLevel = level;
    }

    // Constructor basico sin estado ni nivel (por defecto INIT y EASY)
    public Question(String questionText, List<String> responses, int correctIndex) {
        this(questionText, responses, correctIndex, QuestionStatus.INIT, QuestionLevel.EASY);
    }

    // Getters y Setters con validaciones
    // Utilizamos public void y lanazando excepciones en caso de error para compatibilidad
    // con otros frameworks como JavaBeans. y Spring,
    // visto que quizas en el futuro usaremos Sproing para gestionar estos objetos.
    // aceptamos esta recomendacion en lugar de usar constructores con validaciones y
    // valores de retorno booleanos que evita el uso de excepciones y hace todo mas semcillo.

    @JsonProperty("questionText")
    public String getQuestionText() {
        return questionText;
    }

    @JsonProperty("questionText")
    public void setQuestionText(String questionText) {
        if (questionText == null)
            throw new IllegalArgumentException("questionText cannot be null");
        if (questionText.length() > 128)
            throw new IllegalArgumentException("questionText max 128 characters");
        this.questionText = questionText;
    }

    @JsonProperty("questionResponsesList")
    public List<String> getQuestionResponsesList() {
        return Collections.unmodifiableList(questionResponsesList);
    }

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
            if (r == null)
                throw new IllegalArgumentException("response entries cannot be null");
            if (r.length() > 128)
                throw new IllegalArgumentException("each response text cannot exceed 128 characters");
        }
        this.questionResponsesList.addAll(responses);
    }

    @JsonProperty("correctQuestionIndex")
    public int getCorrectQuestionIndex() {
        return correctQuestionIndex;
    }

    @JsonProperty("correctQuestionIndex")
    public void setCorrectQuestionIndex(int correctQuestionIndex) {
        if (correctQuestionIndex < 0 || correctQuestionIndex >= questionResponsesList.size())
            throw new IndexOutOfBoundsException("correctQuestionIndex out of range");
        this.correctQuestionIndex = correctQuestionIndex;
    }

    @JsonProperty("questionStatus")
    public QuestionStatus getQuestionStatus() {
        return questionStatus;
    }

    @JsonProperty("questionStatus")
    public void setQuestionStatus(QuestionStatus questionStatus) {
        if (questionStatus == null)
            throw new IllegalArgumentException("questionStatus cannot be null");
        this.questionStatus = questionStatus;
    }

    @JsonProperty("questionLevel")
    public QuestionLevel getQuestionLevel() {
        return questionLevel;
    }

    @JsonProperty("questionLevel")
    public void setQuestionLevel(QuestionLevel questionLevel) {
        if (questionLevel == null)
            throw new IllegalArgumentException("questionLevel cannot be null");
        this.questionLevel = questionLevel;
    }

    /**
     * Comprueba si el índice dado corresponde a la respuesta correcta.
     */
    public boolean isCorrectIndex(int index) {
        return index == correctQuestionIndex;
    }

    /**
     * Devuelve la respuesta correcta.
     */
    @JsonIgnore
    public String getCorrectResponse() {
        if (correctQuestionIndex < 0)
            return null;
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
