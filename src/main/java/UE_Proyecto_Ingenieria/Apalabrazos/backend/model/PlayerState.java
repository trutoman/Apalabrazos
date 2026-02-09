package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

/**
 * Representa el estado lógico de un jugador en el sistema.
 * El jugador transita por estos estados durante su sesión.
 */
public enum PlayerState {
    /**
     * Jugador conectado pero en el lobby, sin unirse a ninguna partida
     */
    LOBBY,

    /**
     * Jugador buscando partida o esperando que comience
     */
    MATCHMAKING,

    /**
     * Jugador actualmente en una partida en curso
     */
    PLAYING,

    /**
     * Jugador desconectado pero su sesión sigue activa (puede reconectar)
     */
    DISCONNECTED,
}
