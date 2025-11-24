package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event fired when a new game is started.
 */
public class GameStartedEvent extends GameEvent {
    private final String playerOneName;
    private final String playerTwoName;

    public GameStartedEvent(String playerOneName, String playerTwoName) {
        super();
        this.playerOneName = playerOneName;
        this.playerTwoName = playerTwoName;
    }

    public String getPlayerOneName() {
        return playerOneName;
    }

    public String getPlayerTwoName() {
        return playerTwoName;
    }
}
