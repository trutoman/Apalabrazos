package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event published when a new game session has been successfully created in GameSessionManager.
 * Contains the session ID and metadata about the newly created session.
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
