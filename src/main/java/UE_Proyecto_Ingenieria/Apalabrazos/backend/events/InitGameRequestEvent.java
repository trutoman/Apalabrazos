package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event published by GameController when the player clicks the start button.
 * Replaces the direct call to gameService.initGame().
 */
public class InitGameRequestEvent extends GameEvent {

    private final String playerId;
    private final String roomId;

    public InitGameRequestEvent(String playerId, String roomId) {
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
