package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event fired periodically to update timer.
 */
public class TimerTickEvent extends GameEvent {
    private final int playerIndex;
    private final int remainingSeconds;

    public TimerTickEvent(int playerIndex, int remainingSeconds) {
        super();
        this.playerIndex = playerIndex;
        this.remainingSeconds = remainingSeconds;
    }

    public int getPlayerIndex() {
        return playerIndex;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }
}
