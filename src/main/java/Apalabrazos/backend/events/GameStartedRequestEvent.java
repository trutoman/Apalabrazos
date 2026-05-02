package Apalabrazos.backend.events;

/**
 * Event fired when someone requests to start a game.
 * Contains the roomId and playerId of who is requesting to start the game.
 */
public class GameStartedRequestEvent extends GameEvent {
    private final String roomId;
    private final String playerId;

    public GameStartedRequestEvent(String roomId, String playerId) {
        super();
        this.roomId = roomId;
        this.playerId = playerId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getPlayerId() {
        return playerId;
    }
}
