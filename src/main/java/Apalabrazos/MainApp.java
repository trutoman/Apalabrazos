package Apalabrazos;

import Apalabrazos.backend.network.server.EmbeddedWebSocketServer;
import Apalabrazos.backend.service.MatchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
        EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(8080);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping server...");
            MatchManager.getInstance().stopAiQuestionService();
            server.stop();
        }));

        server.start();

        try {
            log.info("Press Ctrl+C to stop the application...");
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.error("Interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}