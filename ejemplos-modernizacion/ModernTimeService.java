package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.AsyncEventBus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.TimerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TimeService modernizado usando ScheduledExecutorService + Virtual Threads.
 *
 * MEJORAS SOBRE TimeService ANTIGUO:
 *
 * ANTES (Thread.sleep):
 * - Consume CPU constantemente verificando el flag 'running'
 * - Poca precisión (sleep no garantiza exactamente 1 segundo)
 * - Gestión manual de threads (propenso a errores)
 * - No puedes tener múltiples timers fácilmente
 *
 * AHORA (ScheduledExecutorService):
 * - No consume CPU entre ticks (scheduler del OS lo maneja)
 * - Alta precisión temporal
 * - Gestión automática de threads
 * - Escalable a múltiples timers
 * - Usa Virtual Threads (ligero y eficiente)
 */
public class ModernTimeService {

    private static final Logger log = LoggerFactory.getLogger(ModernTimeService.class);

    private final AsyncEventBus eventBus;

    // ScheduledExecutorService: para tareas periódicas
    // Mejor que Thread.sleep porque:
    // 1. No bloquea threads
    // 2. Mayor precisión
    // 3. Gestión automática de recursos
    private final ScheduledExecutorService scheduler;

    // Future que representa la tarea de timer en ejecución
    private ScheduledFuture<?> timerTask;

    // Thread-safe counter (importante para acceso concurrente)
    private final AtomicInteger elapsedSeconds = new AtomicInteger(0);

    private volatile boolean running = false;

    public ModernTimeService(AsyncEventBus eventBus) {
        this.eventBus = eventBus;

        // Crear scheduler con Virtual Thread
        // Java 21: Thread.ofVirtual().factory()
        // Esto crea el scheduler usando virtual threads en lugar de platform threads
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().factory()
        );

        log.info("ModernTimeService creado con Virtual Thread scheduler");
    }

    /**
     * Iniciar el timer con ejecución periódica
     */
    public synchronized void start() {
        if (running) {
            log.warn("TimeService ya está ejecutándose");
            return;
        }

        running = true;
        elapsedSeconds.set(0);

        log.info("Iniciando ModernTimeService");

        // scheduleAtFixedRate: ejecuta la tarea cada X tiempo
        // Parámetros:
        // - this::tick: método a ejecutar
        // - 0: delay inicial (empieza inmediatamente)
        // - 1: periodo entre ejecuciones
        // - TimeUnit.SECONDS: unidad de tiempo
        timerTask = scheduler.scheduleAtFixedRate(
            this::tick,           // Tarea a ejecutar
            0,                    // Sin delay inicial
            1,                    // Cada 1 segundo
            TimeUnit.SECONDS
        );
    }

    /**
     * Método ejecutado cada segundo por el scheduler
     * Se ejecuta en un virtual thread automáticamente
     */
    private void tick() {
        if (!running) {
            return; // Verificación adicional de seguridad
        }

        int seconds = elapsedSeconds.incrementAndGet();

        log.debug("Timer tick: {} segundos transcurridos", seconds);

        // Publicar evento asíncronamente
        // Como eventBus es async, esto no bloquea al timer
        eventBus.publishAndForget(new TimerTickEvent(seconds));
    }

    /**
     * Detener el timer
     */
    public synchronized void stop() {
        if (!running) {
            log.warn("TimeService ya está detenido");
            return;
        }

        running = false;

        if (timerTask != null && !timerTask.isDone()) {
            // Cancelar la tarea programada
            // false: no interrumpir si ya está ejecutándose
            timerTask.cancel(false);
            log.info("Tarea de timer cancelada");
        }

        log.info("ModernTimeService detenido en {} segundos", elapsedSeconds.get());
    }

    /**
     * Cerrar completamente el servicio y liberar recursos
     */
    public void shutdown() {
        stop();

        // Cerrar el scheduler de forma ordenada
        scheduler.shutdown();

        try {
            // Esperar hasta 5 segundos a que terminen tareas pendientes
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                // Si no termina en 5 segundos, forzar cierre
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("ModernTimeService cerrado completamente");
    }

    /**
     * Resetear el contador sin detener el timer
     */
    public void reset() {
        elapsedSeconds.set(0);
        log.info("Contador de tiempo reseteado");
    }

    /**
     * Obtener segundos transcurridos
     */
    public int getElapsedSeconds() {
        return elapsedSeconds.get();
    }

    /**
     * Verificar si está en ejecución
     */
    public boolean isRunning() {
        return running;
    }
}

/* =====================================================================
 * EJEMPLO DE USO:
 * =====================================================================
 *
 * AsyncEventBus eventBus = new AsyncEventBus();
 * ModernTimeService timeService = new ModernTimeService(eventBus);
 *
 * // Agregar listener para recibir ticks
 * eventBus.addListener(event -> {
 *     if (event instanceof TimerTickEvent tick) {
 *         System.out.println("Han pasado " + tick.getSeconds() + " segundos");
 *     }
 * });
 *
 * // Iniciar
 * timeService.start();
 *
 * // ... el timer emitirá eventos cada segundo automáticamente
 *
 * // Detener cuando termines
 * timeService.shutdown();
 *
 * =====================================================================
 */
