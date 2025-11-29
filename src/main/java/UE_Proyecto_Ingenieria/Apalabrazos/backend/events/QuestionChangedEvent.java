package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event fired when the current question changes.
 */
public class QuestionChangedEvent extends GameEvent {
    private final int playerIndex;
    private final int questionIndex;
    private final char letter;

    public QuestionChangedEvent(int playerIndex, int questionIndex, char letter) {
        super();
        this.playerIndex = playerIndex;
        this.questionIndex = questionIndex;
        this.letter = letter;
    }

    public int getPlayerIndex() {
        return playerIndex;
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public char getLetter() {
        return letter;
    }
}
