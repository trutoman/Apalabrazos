package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Evento publicado cada segundo por el servicio de tiempo.
 * Representa que ha pasado un segundo desde el último tick.
 * Contiene el contador de segundos restantes en la partida.
 */
public class TimerTickEvent extends GameEvent {
    private final int remainingSeconds;

    public TimerTickEvent(int remainingSeconds) {
        super();
        this.remainingSeconds = remainingSeconds;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    @Deprecated
    public int getElapsedSeconds() {
        return remainingSeconds;
    }
}
