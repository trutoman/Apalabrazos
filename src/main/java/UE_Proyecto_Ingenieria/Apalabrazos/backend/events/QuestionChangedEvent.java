package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionStatus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question;

/**
 * Event fired when the current question changes.
 */
public class QuestionChangedEvent extends GameEvent {
    private final int questionIndex;
    private final QuestionStatus status;
    private final String playerId; // destinatario del evento
    private final Question nextQuestion; // siguiente pregunta a mostrar, null si no hay siguiente
    private final int totalCorrect; // total de respuestas correctas del jugador
    private final int totalIncorrect; // total de respuestas incorrectas del jugador

    public QuestionChangedEvent(int questionIndex, QuestionStatus status) {
        super();
        this.questionIndex = questionIndex;
        this.status = status;
        this.playerId = null;
        this.nextQuestion = null;
        this.totalCorrect = 0;
        this.totalIncorrect = 0;
    }

    /**
     * Constructor que permite direccionar el evento a un jugador concreto.
     */
    public QuestionChangedEvent(int questionIndex, QuestionStatus status, String playerId) {
        super();
        this.questionIndex = questionIndex;
        this.status = status;
        this.playerId = playerId;
        this.nextQuestion = null;
        this.totalCorrect = 0;
        this.totalIncorrect = 0;
    }

    /**
     * Constructor que incluye la siguiente pregunta a mostrar y los totales de aciertos/fallos.
     */
    public QuestionChangedEvent(int questionIndex, QuestionStatus status, String playerId, Question nextQuestion, int totalCorrect, int totalIncorrect) {
        super();
        this.questionIndex = questionIndex;
        this.status = status;
        this.playerId = playerId;
        this.nextQuestion = nextQuestion;
        this.totalCorrect = totalCorrect;
        this.totalIncorrect = totalIncorrect;
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public String getPlayerId() {
        return playerId;
    }

    public Question getNextQuestion() {
        return nextQuestion;
    }

    public int getTotalCorrect() {
        return totalCorrect;
    }

    public int getTotalIncorrect() {
        return totalIncorrect;
    }
}
