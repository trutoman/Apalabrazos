package UE_Proyecto_Ingenieria.Apalabrazos;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.server.EmbeddedWebSocketServer;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
        GameSessionManager gameSessionManager = GameSessionManager.getInstance();
        log.info("GameSessionManager singleton initialized and ready");

        EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(8080);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal recibido, deteniendo servidor...");
            server.stop();
        }));

        server.start();

        try {
            log.info("Presiona Ctrl+C para detener la aplicacion...");
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.error("Interrupcion: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}