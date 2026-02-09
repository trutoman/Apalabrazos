package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EventBus asíncrono con Virtual Threads de Java 21.
 * Permite publicar eventos que se procesan en paralelo sin bloquear el hilo principal.
 *
 * VENTAJAS sobre EventBus síncrono:
 * - Los listeners se ejecutan en paralelo (no secuencialmente)
 * - publish() no bloquea al publicador
 * - Soporta millones de eventos concurrentes sin overhead
 * - Mejor rendimiento y escalabilidad
 */
public class AsyncEventBus {

    private static final Logger log = LoggerFactory.getLogger(AsyncEventBus.class);

    // Thread-safe: permite agregar/quitar listeners durante la ejecución
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    // Virtual Thread Executor: crea un virtual thread por cada tarea
    // Virtual threads son extremadamente ligeros (millones sin problema)
    private final ExecutorService executor;

    public AsyncEventBus() {
        // Java 21: newVirtualThreadPerTaskExecutor()
        // Cada listener.onEvent() se ejecuta en su propio virtual thread
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Registrar un listener para recibir eventos de forma asíncrona
     */
    public void addListener(EventListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            log.debug("Listener añadido: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * Quitar un listener
     */
    public void removeListener(EventListener listener) {
        listeners.remove(listener);
        log.debug("Listener removido: {}", listener.getClass().getSimpleName());
    }

    /**
     * Publicar un evento de forma ASÍNCRONA.
     *
     * DIFERENCIA CLAVE:
     * - EventBus antiguo: publish() bloquea hasta que TODOS los listeners terminen
     * - AsyncEventBus: publish() retorna inmediatamente, listeners ejecutan en paralelo
     *
     * @param event El evento a publicar
     * @return CompletableFuture que completa cuando TODOS los listeners terminan
     */
    public CompletableFuture<Void> publish(GameEvent event) {
        log.debug("Publicando evento: {} a {} listeners",
                 event.getClass().getSimpleName(),
                 listeners.size());

        // Crear un CompletableFuture por cada listener
        List<CompletableFuture<Void>> futures = listeners.stream()
            .map(listener -> CompletableFuture.runAsync(() -> {
                try {
                    // Se ejecuta en un virtual thread separado
                    listener.onEvent(event);
                } catch (Exception e) {
                    log.error("Error procesando evento {} en listener {}: {}",
                             event.getClass().getSimpleName(),
                             listener.getClass().getSimpleName(),
                             e.getMessage(), e);
                }
            }, executor))
            .toList();

        // Retorna un Future que completa cuando TODOS los listeners terminan
        // Si no necesitas esperar, simplemente ignora el Future
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Publicar evento y esperar a que todos los listeners terminen (fire-and-forget)
     * Útil cuando no te importa el resultado
     */
    public void publishAndForget(GameEvent event) {
        publish(event); // No esperamos al CompletableFuture
    }

    /**
     * Publicar evento y esperar bloqueando hasta que todos terminen
     * Útil para testing o casos donde necesitas garantías de orden
     */
    public void publishAndWait(GameEvent event) {
        try {
            publish(event).join(); // Bloquea hasta completar
        } catch (Exception e) {
            log.error("Error esperando completar publicación de evento: {}", e.getMessage());
        }
    }

    /**
     * Limpiar todos los listeners y cerrar el executor
     */
    public void shutdown() {
        listeners.clear();
        executor.shutdown();
        log.info("AsyncEventBus cerrado");
    }
}
