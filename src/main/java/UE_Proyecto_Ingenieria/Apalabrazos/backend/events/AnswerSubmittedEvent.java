package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event fired when a player submits an answer.
 */
public class AnswerSubmittedEvent extends GameEvent {
    private final int playerIndex; // 0 or 1
    private final char letter;
    private final String answer;

    public AnswerSubmittedEvent(int playerIndex, char letter, String answer) {
        super();
        this.playerIndex = playerIndex;
        this.letter = letter;
        this.answer = answer;
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
}
