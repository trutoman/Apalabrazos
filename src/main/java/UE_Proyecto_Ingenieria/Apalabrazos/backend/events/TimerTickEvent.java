package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Evento publicado cada segundo por el servicio de tiempo.
 * Representa que ha pasado un segundo desde el último tick.
 * Contiene el contador de segundos transcurridos desde el inicio.
 */
public class TimerTickEvent extends GameEvent {
    private final int elapsedSeconds;

    public TimerTickEvent(int elapsedSeconds) {
        super();
        this.elapsedSeconds = elapsedSeconds;
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
    }
}
