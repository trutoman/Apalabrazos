package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;

/**
 * Event published when a player requests to create a new multiplayer game.
 */
public class GameCreationRequestedEvent extends GameEvent {

    private final GamePlayerConfig config;
    private final String tempRoomCode;

    public GameCreationRequestedEvent(GamePlayerConfig config, String tempRoomCode) {
        this.config = config;
        this.tempRoomCode = tempRoomCode;
    }

    public GamePlayerConfig getConfig() {
        return config;
    }

    public String getTempRoomCode() {
        return tempRoomCode;
    }
}
