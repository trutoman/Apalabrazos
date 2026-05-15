package Apalabrazos.backend.service;

import Apalabrazos.backend.events.GlobalAsyncEventBus;
import Apalabrazos.backend.events.TimerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Usa un único Thread con un bucle y sleep de 1 segundo.
 */
public class TimeService {

    private static final Logger log = LoggerFactory.getLogger(TimeService.class);

    private final String matchId;
    private Thread worker;
    private volatile boolean running = false;

    public TimeService() {
        this(null);
    }

    public TimeService(String matchId) {
        this.matchId = matchId;
    }

    // Inicia el hilo si aún no está iniciado
    public synchronized void start() {
        if (running) return;
        running = true;
        log.info("TimeService started");
        worker = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.warn("TimeService worker interrupted for matchId={}", matchId, e);
                    break;
                }

                if (!running)
                    break;
                log.debug("TimerTickEvent published");
                GlobalAsyncEventBus.publish(new TimerTickEvent(0, matchId)); // GameService gestionará el valor real
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
        log.info("TimeService stopped");
    }
}
