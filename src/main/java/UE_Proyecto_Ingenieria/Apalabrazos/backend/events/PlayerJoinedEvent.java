package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;

/**
 * Evento emitido cuando un jugador solicita unirse a la partida.
 * Contiene el objeto Player completo para validar por playerID.
 */
public class PlayerJoinedEvent extends GameEvent {
    private final Player player;

    public PlayerJoinedEvent(Player player) {
        super();
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }
}
