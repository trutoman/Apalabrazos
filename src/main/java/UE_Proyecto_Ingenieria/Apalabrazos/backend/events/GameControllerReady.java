package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event published by GameController when it has finished initialization
 * and is ready to receive events from GameService.
 * This helps synchronize the state machine between controller and service.
 */
public class GameControllerReady extends GameEvent {
    private final String playerId;
    private final String roomId;

    public GameControllerReady(String playerId, String roomId) {
        this.playerId = playerId;
        this.roomId = roomId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getRoomId() {
        return roomId;
    }
}
