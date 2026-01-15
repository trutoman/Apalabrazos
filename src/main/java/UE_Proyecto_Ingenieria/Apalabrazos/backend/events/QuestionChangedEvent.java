package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionStatus;

/**
 * Event fired when the current question changes.
 */
public class QuestionChangedEvent extends GameEvent {
    private final int questionIndex;
    private final QuestionStatus status;
    private final Question question;

    public QuestionChangedEvent(int questionIndex, QuestionStatus status, Question question) {
        super();
        this.questionIndex = questionIndex;
        this.status = status;
        this.question = question;
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public Question getQuestion() {
        return question;
    }
}
