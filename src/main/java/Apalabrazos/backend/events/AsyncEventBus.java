package Apalabrazos.backend.events;

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
            log.debug("[ASYNC-BUS] Listener added: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * Quitar un listener
     */
    public void removeListener(EventListener listener) {
        listeners.remove(listener);
        log.debug("[ASYNC-BUS] Listener removed: {}", listener.getClass().getSimpleName());
    }

    /**
     * Publicar un evento de forma ASÍNCRONA.
     * @param event El evento a publicar
     * @return CompletableFuture que completa cuando TODOS los listeners terminan
     */
    public CompletableFuture<Void> publish(GameEvent event) {
        log.debug("[ASYNC-BUS][DISPATCH] Publishing event: {} to {} listener(s)",
                 event.getClass().getSimpleName(),
                 listeners.size());

        // Crear un CompletableFuture por cada listener
        List<CompletableFuture<Void>> futures = listeners.stream()
            .map(listener -> CompletableFuture.runAsync(() -> {
                try {
                    // Se ejecuta en un virtual thread separado
                    listener.onEvent(event);
                } catch (Exception e) {
                    log.error("[ASYNC-BUS] ❌ Error processing event {} in listener {}: {}",
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
            log.error("[ASYNC-BUS] ❌ Error waiting for event publication to complete: {}", e.getMessage());
        }
    }

    /**
     * Limpiar todos los listeners y cerrar el executor
     */
    public void shutdown() {
        listeners.clear();
        executor.shutdown();
        log.info("[ASYNC-BUS] AsyncEventBus shut down");
    }
}
