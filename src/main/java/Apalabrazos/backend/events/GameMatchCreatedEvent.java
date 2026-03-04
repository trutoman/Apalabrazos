package Apalabrazos.backend.events;

import Apalabrazos.backend.service.GameService;

/**
 * Event published when a new game session has been successfully created in GameSessionManager.
 * Contains the session ID and metadata about the newly created session.
 */
public class GameMatchCreatedEvent extends GameEvent {

    private final String tempRoomCode;
    private final String matchId;
    private final GameService gameService;

    public GameMatchCreatedEvent(String tempRoomCode, String matchId, GameService gameService) {
        this.tempRoomCode = tempRoomCode;
        this.matchId = matchId;
        this.gameService = gameService;
    }

    public String getTempRoomCode() {
        return tempRoomCode;
    }

    public String getMatchId() {
        return matchId;
    }

    public GameService getGameService() {
        return gameService;
    }
}
