package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GameRecord;

/**
 * Event fired when the entire game is finished.
 */
public class GameFinishedEvent extends GameEvent {
    private final GameRecord playerOneRecord;
    private final GameRecord playerTwoRecord;

    public GameFinishedEvent(GameRecord playerOneRecord, GameRecord playerTwoRecord) {
        super();
        this.playerOneRecord = playerOneRecord;
        this.playerTwoRecord = playerTwoRecord;
    }

    public GameRecord getPlayerOneRecord() {
        return playerOneRecord;
    }

    public GameRecord getPlayerTwoRecord() {
        return playerTwoRecord;
    }
}
