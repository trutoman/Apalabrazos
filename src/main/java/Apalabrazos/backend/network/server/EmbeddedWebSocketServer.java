package Apalabrazos.backend.network.server;

import Apalabrazos.backend.dto.LoginRequest;
import Apalabrazos.backend.dto.RegisterRequest;
import Apalabrazos.backend.model.Player;
import Apalabrazos.backend.model.User;
import Apalabrazos.backend.network.ConnectionHandler;
import Apalabrazos.backend.repository.UserRepository;
import Apalabrazos.backend.service.MatchesManager;
import Apalabrazos.backend.tools.JwtService;
import Apalabrazos.backend.tools.PasswordHasher;
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
            .getLogger("Apalabrazos.backend.network.server.EmbeddedWebSocketServer");

    private final int port;
    private Javalin app;
    private final JavalinConnectionHandler connectionHandler = new JavalinConnectionHandler();
    private final UserRepository userRepository = new UserRepository();
    private final JwtService jwtService = new JwtService();

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
     * Orchestrates the initialization of the Javalin application with all routes and endpoints.
     */
    public void start() {
        try {
            log.info("[SERVER] Starting Javalin HTTP server...");
            initializeJavalinApp();
            registerApiEndpoints();
            registerWebSocketEndpoints();
            logServerStartupInfo();
        } catch (Exception e) {
            log.error("✗ Error starting WebSocket server: {}", e.getMessage(), e);
            throw new RuntimeException("Could not start websocket server", e);
        }
    }

    /**
     * Initializes the Javalin application with server configuration.
     * Responsibility: Server configuration and static files setup.
     */
    private void initializeJavalinApp() {
        app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
                log.debug("[SERVER] Static files configured: /public");
            });
        }).start(port);
        log.info("[SERVER] HTTP server started on port {}", port);
    }

    /**
     * Registers all REST API endpoints (login and register).
     * Responsibility: API endpoint registration and initialization.
     */
    private void registerApiEndpoints() {
        registerLoginEndpoint();
        registerRegisterEndpoint();
    }

    /**
     * Registers the POST /api/login endpoint.
     * Responsibility: Login endpoint implementation and authentication logic.
     */
    private void registerLoginEndpoint() {
        app.post("/api/login", ctx -> {
            try {
                LoginRequest req = ctx.bodyAsClass(LoginRequest.class);

                // Validation
                java.util.List<String> errors = req.validate();
                if (!errors.isEmpty()) {
                    ctx.status(400).json(new java.util.HashMap<String, Object>() {
                        {
                            put("status", "error");
                            put("message", "Validation failed");
                            put("errors", errors);
                        }
                    });
                    log.warn("Login validation failed: {}", errors);
                    return;
                }

                log.info("[LOGIN] Request for email: {}", req.email);

                // Find user by email
                User user = userRepository.findByEmail(req.email);
                if (user == null) {
                    ctx.status(401).json(new java.util.HashMap<String, String>() {
                        {
                            put("status", "error");
                            put("message", "Invalid email or password");
                        }
                    });
                    log.warn("Login failed: User not found with email {}", req.email);
                    return;
                }

                // Verify password
                String hashedInputPassword = PasswordHasher.hashPassword(req.pass, user.salt);
                if (!hashedInputPassword.equals(user.password)) {
                    ctx.status(401).json(new java.util.HashMap<String, String>() {
                        {
                            put("status", "error");
                            put("message", "Invalid email or password");
                        }
                    });
                    log.warn("Login failed: Invalid password for user {}", req.email);
                    return;
                }

                // Login successful - generate JWT token
                String token = jwtService.generateToken(user);

                ctx.json(new java.util.HashMap<String, String>() {
                    {
                        put("status", "ok");
                        put("token", token);
                    }
                });
                log.info("Login successful for user: {}", user.username);

            } catch (Exception e) {
                log.error("Error processing login request: {}", e.getMessage());
                ctx.status(400).json(new java.util.HashMap<String, String>() {
                    {
                        put("status", "error");
                        put("message", "Invalid request");
                    }
                });
            }
        });
    }

    /**
     * Registers the POST /api/register endpoint.
     * Responsibility: Registration endpoint implementation and user creation logic.
     */
    private void registerRegisterEndpoint() {
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
                    ctx.status(500).json(new java.util.HashMap<String, String>() {
                        {
                            put("status", "error");
                            put("message", "Internal Server Error: Failed to save user");
                        }
                    });
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
                ctx.status(400).json(new java.util.HashMap<String, String>() {
                    {
                        put("status", "error");
                        put("message", "Invalid JSON");
                    }
                });
            }
        });
    }

    /**
     * Registers the WebSocket endpoint /ws/game/{userId}.
     * Responsibility: WebSocket endpoint registration and connection lifecycle management.
     */
    private void registerWebSocketEndpoints() {
        log.info("Starting Javalin WebSocket server...");
        app.ws("/ws/game/{userId}", ws -> {
            log.info("[WEBSOCKET] Registering endpoint: /ws/game/{userId}");
            ws.onConnect(ctx -> {
                log.info("[WEBSOCKET-CONNECT] Connection received for: {}", ctx.pathParam("userId"));
                connectionHandler.onConnect(ctx);
            });
            ws.onMessage(ctx -> {
                log.debug("[WEBSOCKET-MESSAGE] Message received");
                connectionHandler.onMessage(ctx);
            });
            ws.onClose(ctx -> {
                log.info("[WEBSOCKET-CLOSE] Connection closed");
                connectionHandler.onClose(ctx);
            });
            ws.onError(ctx -> {
                log.error("[WEBSOCKET-ERROR] Error occurred");
                connectionHandler.onError(ctx);
            });
        });
    }

    /**
     * Logs startup information for the server.
     * Responsibility: Informative logging only.
     */
    private void logServerStartupInfo() {
        log.info("✓ WebSocket server running on port {}", port);
        log.info("  Endpoint WebSocket: ws://localhost:{}/ws/game/{username}", port);
        log.info("  Static files: http://localhost:{}/", port);
        log.info("  Status: LISTENING");
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
