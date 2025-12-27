package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameService;

/**
 * Event published when a new game session has been successfully created in GameSessionManager.
 * Contains the session ID and metadata about the newly created session.
 */
public class GameSessionCreatedEvent extends GameEvent {

    private final String tempRoomCode;
    private final String sessionId;
    private final GameService gameService;

    public GameSessionCreatedEvent(String tempRoomCode, String sessionId, GameService gameService) {
        this.tempRoomCode = tempRoomCode;
        this.sessionId = sessionId;
        this.gameService = gameService;
    }

    public String getTempRoomCode() {
        return tempRoomCode;
    }

    public String getSessionId() {
        return sessionId;
    }

    public GameService getGameService() {
        return gameService;
    }
