package Apalabrazos.backend.events;

import java.util.concurrent.CompletableFuture;

/**
 * Global event bus instance compartido en toda la aplicación.
 * Versión asíncrona del GlobalEventBus original.
 *
 * CAMBIOS:
 * - EventBus → AsyncEventBus
 * - Todos los eventos se procesan asíncronamente
 *
 * <p>Este bus solo debería transportar eventos de coordinación backend descritos
 * en {@link GlobalBusEventCatalog}. Para eventos dirigidos al cliente o al
 * GameController se debe usar el bus externo de cada {@code GameService}.</p>
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

    public static void addListener(EventListener listener) {
        instance.addListener(listener);
    }

    public static void removeListener(EventListener listener) {
        instance.removeListener(listener);
    }

    public static CompletableFuture<Void> publish(GameEvent event) {
        return instance.publish(event);
    }

    public static void publishAndWait(GameEvent event) {
        instance.publishAndWait(event);
    }

    public static void publishAndForget(GameEvent event) {
        instance.publishAndForget(event);
    }

    // Constructor privado para prevenir instanciación
    private GlobalAsyncEventBus() {
    }
}
