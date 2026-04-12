package Apalabrazos;

import Apalabrazos.backend.network.server.EmbeddedWebSocketServer;
import Apalabrazos.backend.service.MatchesManager;
import Apalabrazos.backend.tools.AIQuestionScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
        MatchesManager gameSessionManager = MatchesManager.getInstance();
        log.info("GameSessionManager singleton initialized and ready");

        EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(8080);

        // AI Question Scheduler: genera preguntas con IA periódicamente
        AIQuestionScheduler aiScheduler = new AIQuestionScheduler();
        server.setAiQuestionScheduler(aiScheduler);
        aiScheduler.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal recibido, deteniendo servidor...");
            aiScheduler.stop();
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