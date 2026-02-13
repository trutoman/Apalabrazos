package UE_Proyecto_Ingenieria.Apalabrazos.backend.network.server;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.dto.RegisterRequest;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.User;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.ConnectionHandler;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.repository.UserRepository;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameSessionManager;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.tools.PasswordHasher;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded WebSocket server built with Javalin.
 * Lightweight web framework with native WebSocket support.
 *
 * Usage:
 * {@code
 * EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(8080);
 * server.start();
 *
 * // Clients can connect to: ws://localhost:8080/ws/game/Alice
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
     * Creates an embedded WebSocket server.
     *
     * @param port Port to listen on (e.g., 8080)
     */
    public EmbeddedWebSocketServer(int port) {
        this.port = port;
    }

    /**
     * Starts the WebSocket server.
     */
    public void start() {
        try {
            log.info("Starting Javalin HTTP server...");

            app = Javalin.create(config -> {
                // Configure static files
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/public";
                    staticFiles.location = Location.CLASSPATH;
                });
            }).start(port);

            // Login API endpoint
            app.post("/api/login", ctx -> {
                // For now, any login is accepted
                // In the future this should be validated against the database
                ctx.json(new java.util.HashMap<String, String>() {
                    {
                        put("status", "ok");
                        put("token", "dummy-token");
                    }
                });
                log.info("Login request received: " + ctx.body());
            });

            // Register API endpoint
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

                    // Generate salt and hash password
                    String salt = PasswordHasher.generateSalt();
                    String hashedPassword = PasswordHasher.hashPassword(req.password, salt);

                    // Create User object with hashed password and salt
                    User user = new User(req.username, req.email, hashedPassword, salt);
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

            log.info("Starting Javalin WebSocket server...");
            // Register WebSocket endpoint
            app.ws("/ws/game/{username}", ws -> {
                ws.onConnect(connectionHandler::onConnect);
                ws.onMessage(connectionHandler::onMessage);
                ws.onClose(connectionHandler::onClose);
                ws.onError(connectionHandler::onError);
            });

            log.info("✓ WebSocket server running on port {}", port);
            log.info("  Endpoint WebSocket: ws://localhost:{}/ws/game/{username}", port);
            log.info("  Static files: http://localhost:{}/", port);
            log.info("  Status: LISTENING");

        } catch (Exception e) {
            log.error("✗ Error starting WebSocket server: {}", e.getMessage(), e);
            throw new RuntimeException("Could not start websocket server", e);
        }
    }

    /**
     * Stops the WebSocket server.
     */
    public void stop() {
        if (app != null) {
            try {
                app.stop();
                // No need to clear sessionMap because GameSessionManager now handles it
                // through the handler
                log.info("✓ WebSocket server stopped");
            } catch (Exception e) {
                log.error("Error stopping WebSocket server: {}", e.getMessage());
            }
        }
    }

    /**
     * Main method to run the server directly.
     */
    public static void main(String[] args) {
        EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(8080);

        // Hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping server...");
            server.stop();
        }));

        server.start();

        try {
            log.info("Press Ctrl+C to stop the server...");
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.error("Interrupción: {}", e.getMessage());
        }
    }
}
