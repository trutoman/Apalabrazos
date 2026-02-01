package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GlobalEventBus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.TimerTickEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Usa un único Thread con un bucle y sleep de 1 segundo.
 */
public class TimeService {

    private static final Logger log = LoggerFactory.getLogger(TimeService.class);

    private final EventBus eventBus;
    private Thread worker;
    private volatile boolean running = false;

    public TimeService() {
        this.eventBus = GlobalEventBus.getInstance();
    }

    // Inicia el hilo si aún no está iniciado
    public synchronized void start() {
        if (running) return;
        running = true;
        log.info("TimeService iniciado");
        worker = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Si interrumpen el hilo, salimos del bucle
                    break;
                }

                if (!running)
                    break;
                log.debug("TimerTickEvent emitido");
                eventBus.publish(new TimerTickEvent(0)); // GameService gestionará el valor real
            }
        }, "TimeService_Thread");
        worker.setDaemon(true); // No bloquea salida de la app
        worker.start();
    }

    // Detiene el hilo
    public synchronized void stop() {
        if (!running)
            return;
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        log.info("TimeService detenido");
    }
}
