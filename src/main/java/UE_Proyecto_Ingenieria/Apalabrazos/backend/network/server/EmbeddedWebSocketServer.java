package UE_Proyecto_Ingenieria.Apalabrazos.backend.network.server;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.ConnectionHandler;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameSessionManager;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Servidor WebSocket embebido usando Javalin.
 * Framework web ligero con WebSocket nativo integrado.
 *
 * Uso:
 * {@code
 * EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(8080);
 * server.start();
 *
 * // Clientes pueden conectar a: ws://localhost:8080/ws/game/Alice
 *
 * server.stop();
 * }
 */
public class EmbeddedWebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedWebSocketServer.class);

    private final int port;
    private Javalin app;
    private final Map<String, UUID> sessionMap = new HashMap<>();

    /**
     * Crear servidor WebSocket embebido
     * @param port Puerto donde escuchar (ej: 8080)
     */
    public EmbeddedWebSocketServer(int port) {
        this.port = port;
    }

    /**
     * Iniciar el servidor WebSocket
     */
    public void start() {
        try {
            log.info("Iniciando Servidor WebSocket Javalin...");

            app = Javalin.create(config -> {
                // Configurar archivos estáticos
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/public";
                    staticFiles.location = Location.CLASSPATH;
                });
            }).start(port);

            // Registrar endpoint WebSocket
            app.ws("/ws/game/:username", ws -> {
                ws.onConnect(ctx -> onConnect(ctx));
                ws.onMessage(ctx -> {
                    // En Javalin 6, el mensaje se obtiene del contexto
                    // Implementación simplificada para ahora
                    log.debug("Mensaje recibido");
                });
                ws.onClose(ctx -> onClose(ctx));
                ws.onError(ctx -> {
                    log.error("Error WebSocket");
                });
            });

            log.info("✓ Servidor WebSocket corriendo en puerto {}", port);
            log.info("  Endpoint WebSocket: ws://localhost:{}/ws/game/{username}", port);
            log.info("  Ejemplo WebSocket: ws://localhost:{}/ws/game/Alice", port);
            log.info("  Archivos estáticos: http://localhost:{}/", port);
            log.info("  Estado: ESCUCHANDO");

        } catch (Exception e) {
            log.error("✗ Error iniciando servidor WebSocket: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo iniciar servidor WebSocket", e);
        }
    }

    /**
     * Detener el servidor WebSocket
     */
    public void stop() {
        if (app != null) {
            try {
                app.stop();
                sessionMap.clear();
                log.info("✓ Servidor WebSocket detenido");
            } catch (Exception e) {
                log.error("Error deteniendo servidor: {}", e.getMessage());
            }
        }
    }

    /**
     * Manejar conexión de cliente
     */
    private void onConnect(WsContext ctx) {
        try {
            String username = ctx.pathParam("username");
            String sessionId = ctx.sessionId();

            log.info("→ Cliente conectando: {} (sessionId: {})", username, sessionId);

            // Generar UUID único para esta conexión
            UUID uuid = UUID.randomUUID();
            sessionMap.put(sessionId, uuid);

            // Simular handler (en una implementación real, usaría ConnectionHandler)
            log.info("✓ Cliente conectado: {} (UUID: {})", username, uuid);

        } catch (Exception e) {
            log.error("Error en onConnect: {}", e.getMessage(), e);
        }
    }

    /**
     * Manejar mensaje del cliente
     */
    private void onMessage(WsContext ctx) {
        try {
            String username = ctx.pathParam("username");
            UUID uuid = sessionMap.get(ctx.sessionId());

            log.debug("← Mensaje de {}", username);

            // Aquí procesarías el mensaje con GameSessionManager
            // ctx.send(response);
            // ctx.sendToAll(broadcastMessage);

        } catch (Exception e) {
            log.error("Error procesando mensaje: {}", e.getMessage(), e);
        }
    }

    /**
     * Manejar cierre de conexión
     */
    private void onClose(WsContext ctx) {
        try {
            String username = ctx.pathParam("username");
            UUID uuid = sessionMap.remove(ctx.sessionId());

            log.info("→ Cliente desconectando: {} (UUID: {})", username, uuid);

            // Aquí limpiarías recursos del jugador

            log.info("✓ Cliente desconectado: {}", username);

        } catch (Exception e) {
            log.error("Error en onClose: {}", e.getMessage(), e);
        }
    }

    /**
     * Manejar errores
     */
    private void onError(WsContext ctx) {
        try {
            String username = ctx.pathParam("username");
            log.error("✗ Error WebSocket para {}", username);
        } catch (Exception e) {
            log.error("Error en onError: {}", e.getMessage());
        }
    }

    /**
     * Obtener UUID de sesión
     */
    public UUID getSessionUUID(String sessionId) {
        return sessionMap.get(sessionId);
    }

    /**
     * Método main para ejecutar el servidor directamente
     */
    public static void main(String[] args) {
        EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(8080);

        // Hook para shutdown graceful
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal recibido, deteniendo servidor...");
            server.stop();
        }));

        server.start();

        try {
            log.info("Presiona Ctrl+C para detener el servidor...");
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.error("Interrupción: {}", e.getMessage());
        }
    }
}
