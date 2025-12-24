package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event published when information about a game session is requested.
 * Typically triggered when a user selects a game in the lobby.
 */
public class GetGameSessionInfoEvent extends GameEvent {

    private final String roomCode;

    public GetGameSessionInfoEvent(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getRoomCode() {
        return roomCode;
    }
}
