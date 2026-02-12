package UE_Proyecto_Ingenieria.Apalabrazos.backend.network.server;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.dto.RegisterRequest;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.User;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.ConnectionHandler;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.repository.UserRepository;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameSessionManager;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * // NOTE: If you see "missing type String" errors here, it is an IDE cache
 * issue.
 * // Maven compile works fine. This comment forces a re-index.
 */
public class EmbeddedWebSocketServer {

    private static final Logger log = LoggerFactory
            .getLogger("UE_Proyecto_Ingenieria.Apalabrazos.backend.network.server.EmbeddedWebSocketServer");

    private final int port;
    private Javalin app;
    private final JavalinConnectionHandler connectionHandler = new JavalinConnectionHandler();
    private final UserRepository userRepository = new UserRepository();

    /**
     * Crear servidor WebSocket embebido
     *
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
            log.info("Iniciando Servidor HTTP Javalin...");

            app = Javalin.create(config -> {
                // Configurar archivos estáticos
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/public";
                    staticFiles.location = Location.CLASSPATH;
                });
            }).start(port);

            // Endpoint de Login API
            app.post("/api/login", ctx -> {
                // Por ahora aceptamos cualquier login
                // En el futuro aqui validariamos con base de datos
                ctx.json(new java.util.HashMap<String, String>() {
                    {
                        put("status", "ok");
                        put("token", "dummy-token");
                    }
                });
                log.info("Login request received: " + ctx.body());
            });

            // Endpoint de Registro API
            app.post("/api/register", ctx -> {
                try {
                    RegisterRequest req = ctx.bodyAsClass(RegisterRequest.class);

                    // Validation
                    java.util.List<String> errors = req.validate();
                    if (!errors.isEmpty()) {
                        ctx.status(400).json(new java.util.HashMap<String, Object>() {
                            {
                                put("status", "error");
                                put("errors", errors);
                            }
                        });
                        log.warn("Register validation failed: {}", errors);
                        return;
                    }

                    log.info("Received REGISTER request -> User: {}, Email: {}, Password: [PROTECTED]", req.username,
                            req.email);

                    // Create User object and save to DB
                    User user = new User(req.username, req.email, req.password);
                    try {
                        userRepository.save(user);
                        log.info("User registered and saved to DB: {}", req.username);
                    } catch (Exception e) {
                        log.error("Failed to save user to DB: {}", e.getMessage());
                        ctx.status(500).result("Internal Server Error: Failed to save user");
                        return;
                    }

                    ctx.json(new java.util.HashMap<String, String>() {
                        {
                            put("status", "ok");
                            put("message", "User registered successfully");
                        }
                    });
                } catch (Exception e) {
                    log.error("Error parsing register request: {}", e.getMessage());
                    ctx.status(400).result("Invalid JSON");
                }
            });

            log.info("Iniciando Servidor WebSocket Javalin...");
            // Registrar endpoint WebSocket
            app.ws("/ws/game/{username}", ws -> {
                ws.onConnect(connectionHandler::onConnect);
                ws.onMessage(connectionHandler::onMessage);
                ws.onClose(connectionHandler::onClose);
                ws.onError(connectionHandler::onError);
            });

            log.info("✓ Websocker server running on port {}", port);
            log.info("  Endpoint WebSocket: ws://localhost:{}/ws/game/{username}", port);
            log.info("  Static files: http://localhost:{}/", port);
            log.info("  Status: LISTENING");

        } catch (Exception e) {
            log.error("✗ Error starting websocket server: {}", e.getMessage(), e);
            throw new RuntimeException("Could not start websocket server", e);
        }
    }

    /**
     * Detener el servidor WebSocket
     */
    public void stop() {
        if (app != null) {
            try {
                app.stop();
                // No necesitamos limpiar sessionMap porque ahora lo gestiona GameSessionManager
                // a través del Handler
                log.info("✓ Websocker server stopped");
            } catch (Exception e) {
                log.error("Error stopping websocket server: {}", e.getMessage());
            }
        }
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
