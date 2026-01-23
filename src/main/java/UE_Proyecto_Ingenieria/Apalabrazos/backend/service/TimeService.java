package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GlobalEventBus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.TimerTickEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventBus;

/**
 * Usa un único Thread con un bucle y sleep de 1 segundo.
 */
public class TimeService {

    private final EventBus eventBus;
    private Thread worker;
    private volatile boolean running = false;
    private int elapsedSeconds = 0;

    public TimeService() {
        this.eventBus = GlobalEventBus.getInstance();
    }

    // Inicia el hilo si aún no está iniciado
    public synchronized void start() {
        if (running) return;
        running = true;
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
                elapsedSeconds++;
                eventBus.publish(new TimerTickEvent(elapsedSeconds));
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
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
    }
}
