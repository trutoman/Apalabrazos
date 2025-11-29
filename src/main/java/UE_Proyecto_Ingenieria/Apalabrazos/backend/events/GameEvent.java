package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Base class for all game events.
 * Events are immutable data objects that represent something that happened.
 */
public abstract class GameEvent {
    private final long timestamp;

    protected GameEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
