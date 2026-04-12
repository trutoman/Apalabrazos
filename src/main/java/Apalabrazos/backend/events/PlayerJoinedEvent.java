package Apalabrazos.backend.events;

/**
 * Evento emitido cuando un jugador solicita unirse a la partida.
 * Transporta el identificador del jugador, la sala destino y, opcionalmente,
 * el nombre visible del jugador para que el backend aplique el mismo flujo de
 * unión tanto al creador como a cualquier otro usuario.
 */
public class PlayerJoinedEvent extends GameEvent {
    private final String playerID;
    private final String roomCode;
    private final String playerName;

    public PlayerJoinedEvent(String playerID, String roomCode) {
        this(playerID, roomCode, null);
    }

    public PlayerJoinedEvent(String playerID, String roomCode, String playerName) {
        super();
        this.playerID = playerID;
        this.roomCode = roomCode;
        this.playerName = playerName;
    }

    public String getPlayerID() {
        return playerID;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getPlayerName() {
        return playerName;
    }
}
