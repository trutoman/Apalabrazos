package Apalabrazos.backend.events;

import Apalabrazos.backend.model.GameRecord;

/**
 * Event fired when the entire game is finished.
 */
public class GameFinishedEvent extends GameEvent {
    private final GameRecord playerOneRecord;
    private final GameRecord playerTwoRecord;
    private final String matchId;

    public GameFinishedEvent(GameRecord playerOneRecord, GameRecord playerTwoRecord) {
        this(playerOneRecord, playerTwoRecord, null);
    }

    public GameFinishedEvent(GameRecord playerOneRecord, GameRecord playerTwoRecord, String matchId) {
        super();
        this.playerOneRecord = playerOneRecord;
        this.playerTwoRecord = playerTwoRecord;
        this.matchId = matchId;
    }

    public GameRecord getPlayerOneRecord() {
        return playerOneRecord;
    }

    public GameRecord getPlayerTwoRecord() {
        return playerTwoRecord;
    }

    public String getMatchId() {
        return matchId;
    }
}
