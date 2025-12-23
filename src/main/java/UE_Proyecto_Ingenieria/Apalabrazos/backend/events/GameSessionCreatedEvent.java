package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event published when a new game session has been successfully created in GameSessionManager.
 * Contains the session ID of the newly created session.
 */
public class GameSessionCreatedEvent extends GameEvent {

    private final String sessionId;

    public GameSessionCreatedEvent(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
