package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event published when a new game session has been successfully created in GameSessionManager.
 * Carries the temporary room code used in the lobby and the final session ID, so listeners can correlate.
 */
public class GameSessionCreatedEvent extends GameEvent {

    private final String tempRoomCode;
    private final String sessionId;

    public GameSessionCreatedEvent(String tempRoomCode, String sessionId) {
        this.tempRoomCode = tempRoomCode;
        this.sessionId = sessionId;
    }

    public String getTempRoomCode() {
        return tempRoomCode;
    }

    public String getSessionId() {
        return sessionId;
    }
}
