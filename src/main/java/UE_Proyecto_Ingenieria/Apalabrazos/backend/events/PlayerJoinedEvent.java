package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;

/**
 * Evento emitido cuando un jugador solicita unirse a la partida.
 * Contiene el objeto Player completo para validar por playerID.
 */
public class PlayerJoinedEvent extends GameEvent {
    private final String playerID;
    private final String roomCode;

    public PlayerJoinedEvent(String playerID, String roomCode) {
        super();
        this.playerID = playerID;
        this.roomCode = roomCode;
    }

    public String getPlayerID() {
        return playerID;
    }

    public String getRoomCode() {
        return roomCode;
    }
}
