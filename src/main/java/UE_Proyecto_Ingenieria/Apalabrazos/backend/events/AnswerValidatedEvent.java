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
    private final int totalCorrect;
    private final int totalIncorrect;

    public AnswerValidatedEvent(int playerIndex, char letter, String answer,
                                QuestionStatus status, String correctAnswer,
                                int totalCorrect, int totalIncorrect) {
        super();
        this.playerIndex = playerIndex;
        this.letter = letter;
        this.answer = answer;
        this.status = status;
        this.correctAnswer = correctAnswer;
        this.totalCorrect = totalCorrect;
        this.totalIncorrect = totalIncorrect;
    }

    // Constructor de compatibilidad temporal para evitar romper otros tests si los hubiera
    // aunque lo ideal es migrar todo.
    public AnswerValidatedEvent(int playerIndex, char letter, String answer,
                                QuestionStatus status, String correctAnswer) {
        this(playerIndex, letter, answer, status, correctAnswer, 0, 0);
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

    public int getTotalCorrect() {
        return totalCorrect;
    }

    public int getTotalIncorrect() {
        return totalIncorrect;
    }

    public boolean isCorrect() {
        return status == QuestionStatus.RESPONDED_OK;
    }
}
