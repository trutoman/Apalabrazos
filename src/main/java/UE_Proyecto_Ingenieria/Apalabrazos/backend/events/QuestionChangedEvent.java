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

    public QuestionChangedEvent(int questionIndex, QuestionStatus status) {
        super();
        this.questionIndex = questionIndex;
        this.status = status;
        this.playerId = null;
        this.nextQuestion = null;
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
    }

    /**
     * Constructor que incluye la siguiente pregunta a mostrar.
     */
    public QuestionChangedEvent(int questionIndex, QuestionStatus status, String playerId, Question nextQuestion) {
        super();
        this.questionIndex = questionIndex;
        this.status = status;
        this.playerId = playerId;
        this.nextQuestion = nextQuestion;
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
}
