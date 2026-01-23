package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event fired when someone requests to start a game.
 * Contains the roomId and username of who is requesting to start the game.
 */
public class GameStartedRequestEvent extends GameEvent {
    private final String roomId;
    private final String username;

    public GameStartedRequestEvent(String roomId, String username) {
        super();
        this.roomId = roomId;
        this.username = username;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getUsername() {
        return username;
    }
}
