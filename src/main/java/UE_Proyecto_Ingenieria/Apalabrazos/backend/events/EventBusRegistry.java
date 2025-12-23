package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Registry to manage the global EventBus and per-session EventBus instances.
 */
public class EventBusRegistry {
    private static final EventBusRegistry INSTANCE = new EventBusRegistry();

    private final EventBus globalBus;
    private final Map<String, EventBus> sessionBuses;

    private EventBusRegistry() {
        this.globalBus = EventBus.getInstance();
        this.sessionBuses = new ConcurrentHashMap<>();
    }

    public static EventBusRegistry getInstance() {
        return INSTANCE;
    }

    public EventBus getGlobalBus() {
        return globalBus;
    }

    /**
     * Get or create the EventBus for a given session.
     */
    public EventBus getOrCreateSessionBus(String sessionId) {
        return sessionBuses.computeIfAbsent(sessionId, id -> EventBus.newBus());
    }

    /**
     * Register an already created session bus under a sessionId.
     */
    public void registerSessionBus(String sessionId, EventBus bus) {
        sessionBuses.put(sessionId, bus);
    }

    /**
     * Retrieve the session bus if present.
     */
    public EventBus getSessionBus(String sessionId) {
        return sessionBuses.get(sessionId);
    }

    /**
     * Remove and discard a session bus.
     */
    public void removeSessionBus(String sessionId) {
        sessionBuses.remove(sessionId);
    }
}
