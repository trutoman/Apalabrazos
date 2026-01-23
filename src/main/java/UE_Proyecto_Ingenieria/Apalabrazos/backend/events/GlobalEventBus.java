package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Global event bus instance shared across the entire application.
 * Provides a centralized communication hub for all services and controllers.
 */
public class GlobalEventBus {
    private static final EventBus instance = new EventBus();

    /**
     * Get the global shared event bus
     * @return The singleton EventBus instance
     */
    public static EventBus getInstance() {
        return instance;
    }

    // Private constructor to prevent instantiation
    private GlobalEventBus() {
    }
}
