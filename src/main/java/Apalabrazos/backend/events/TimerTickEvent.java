package Apalabrazos.backend.events;

/**
 * Evento publicado cada segundo por el servicio de tiempo.
 * Representa que ha pasado un segundo desde el último tick.
 * Contiene el contador de segundos restantes en la partida.
 */
public class TimerTickEvent extends GameEvent {
    private final int remainingSeconds;
    private final String matchId;

    public TimerTickEvent(int remainingSeconds) {
        this(remainingSeconds, null);
    }

    public TimerTickEvent(int remainingSeconds, String matchId) {
        super();
        this.remainingSeconds = remainingSeconds;
        this.matchId = matchId;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public String getMatchId() {
        return matchId;
    }

    @Deprecated
    public int getElapsedSeconds() {
        return remainingSeconds;
    }
}
