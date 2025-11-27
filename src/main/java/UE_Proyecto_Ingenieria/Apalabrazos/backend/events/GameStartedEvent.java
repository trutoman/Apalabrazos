package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;

/**
 * Event fired when a new game is started.
 */
public class GameStartedEvent extends GameEvent {
    private final GamePlayerConfig gamePlayerConfig;

    public GameStartedEvent(GamePlayerConfig gamePlayerConfig) {
        super();
        this.gamePlayerConfig = gamePlayerConfig;
    }

    public GamePlayerConfig getGamePlayerConfig() {
        return gamePlayerConfig;
    }
}
