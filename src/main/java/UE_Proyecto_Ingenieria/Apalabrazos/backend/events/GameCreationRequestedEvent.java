package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;

/**
 * Event published when a player requests to create a new multiplayer game.
 */
public class GameCreationRequestedEvent extends GameEvent {

    private final GamePlayerConfig config;

    public GameCreationRequestedEvent(GamePlayerConfig config) {
        this.config = config;
    }

    public GamePlayerConfig getConfig() {
        return config;
    }
}
