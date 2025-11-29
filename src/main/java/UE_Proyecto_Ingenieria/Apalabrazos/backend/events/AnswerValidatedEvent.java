package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionStatus;

/**
 * Event fired when an answer is validated.
 */
public class AnswerValidatedEvent extends GameEvent {
    private final int playerIndex;
    private final char letter;
    private final String answer;
    private final QuestionStatus status;
    private final String correctAnswer;

    public AnswerValidatedEvent(int playerIndex, char letter, String answer,
                                QuestionStatus status, String correctAnswer) {
        super();
        this.playerIndex = playerIndex;
        this.letter = letter;
        this.answer = answer;
        this.status = status;
        this.correctAnswer = correctAnswer;
    }

    public int getPlayerIndex() {
        return playerIndex;
    }

    public char getLetter() {
        return letter;
    }

    public String getAnswer() {
        return answer;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public boolean isCorrect() {
        return status == QuestionStatus.RESPONDED_OK;
    }
}
