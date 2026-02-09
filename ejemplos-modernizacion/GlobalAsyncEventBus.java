package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Global event bus instance compartido en toda la aplicación.
 * Versión asíncrona del GlobalEventBus original.
 *
 * CAMBIOS:
 * - EventBus → AsyncEventBus
 * - Todos los eventos se procesan asíncronamente
 */
public class GlobalAsyncEventBus {

    private static final AsyncEventBus instance = new AsyncEventBus();

    /**
     * Obtener la instancia global del event bus asíncrono
     * @return La instancia singleton de AsyncEventBus
     */
    public static AsyncEventBus getInstance() {
        return instance;
    }

    // Constructor privado para prevenir instanciación
    private GlobalAsyncEventBus() {
    }
}
