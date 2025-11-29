package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event fired when a player's turn ends.
 */
public class TurnEndedEvent extends GameEvent {
    private final int playerIndex;
    private final int correctAnswers;
    private final int totalQuestions;

    public TurnEndedEvent(int playerIndex, int correctAnswers, int totalQuestions) {
        super();
        this.playerIndex = playerIndex;
        this.correctAnswers = correctAnswers;
        this.totalQuestions = totalQuestions;
    }

    public int getPlayerIndex() {
        return playerIndex;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }
}
