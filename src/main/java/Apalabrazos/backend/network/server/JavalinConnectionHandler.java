package Apalabrazos.backend.network.server;

import Apalabrazos.backend.lobby.LobbyRoom;
import Apalabrazos.backend.network.ConnectionHandler;
import Apalabrazos.backend.tools.JwtService;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Adaptador de ConnectionHandler para Javalin.
 */
public class JavalinConnectionHandler extends ConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(JavalinConnectionHandler.class);
    private final JwtService jwtService = new JwtService();

    public void onConnect(WsConnectContext ctx) {
        try {
            log.info("[CONNECT] 🔗 Intento de conexión WebSocket desde: {}", ctx.session.getRemoteAddress());

            // Extract token from query parameter
            String token = ctx.queryParam("token");
            log.debug("[CONNECT] Token recibido: {} (primeros 20 chars)",
                    token != null ? token.substring(0, Math.min(20, token.length())) : "null");

            // Validar token
            if (token == null || token.trim().isEmpty()) {
                log.warn("[CONNECT] ❌ Conexión rechazada: No token provided");
                ctx.closeSession(4001, "Authentication required");
                return;
            }

            // Set idle timeout to 30 minutes (default is often 30s)
            ctx.session.setIdleTimeout(java.time.Duration.ofMinutes(30));

            DecodedJWT jwt = jwtService.verifyToken(token);
            if (jwt == null) {
                log.warn("[CONNECT] ❌ Conexión rechazada: Invalid token");
                ctx.closeSession(4002, "Invalid authentication token");
                return;
            }
            log.debug("[CONNECT] ✓ Token validado correctamente");

            String username = ctx.pathParam("username");
            String tokenUsername = jwtService.extractUsername(jwt);
            log.debug("[CONNECT] Username en URL: {}, Username en token: {}", username, tokenUsername);

            if (tokenUsername == null || !tokenUsername.equalsIgnoreCase(username)) {
                log.warn("[CONNECT] ❌ Conexión rechazada: Username mismatch (URL: {}, Token: {})",
                        username, tokenUsername);
                ctx.closeSession(4003, "User mismatch");
                return;
            }

            log.info("[CONNECT] ✅ Conexión autenticada para usuario: {}", username);
            onClientConnect(ctx, username);
        } catch (Exception e) {
            log.error("[CONNECT] ❌ Error en autenticación de conexión: {}", e.getMessage(), e);
            try {
                ctx.closeSession(4500, "Server error during authentication");
            } catch (Exception closeErr) {
                log.error("[CONNECT] Error al cerrar sesión: {}", closeErr.getMessage());
            }
        }
    }

    public void onMessage(WsMessageContext ctx) {
        try {
            UUID sessionId = ctx.attribute("session-uuid");
            if (sessionId == null) {
                log.error("[MESSAGE] ❌ Sesión no encontrada en atributos para cliente");
                return;
            }

            String message = ctx.message();
            log.debug("[MESSAGE] 📨 Mensaje recibido de sesión {}: {}", sessionId, message);

            // Parse JSON to detect message type
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(message);

                if (node.has("type") && "chat".equalsIgnoreCase(node.get("type").asText())) {
                    // Get originator username from the Player registry
                    String username = "Unknown";
                    Apalabrazos.backend.model.Player player = sessionManager.getPlayerBySessionId(sessionId);
                    if (player != null) {
                        username = player.getName();
                    }

                    // Extract text from payload
                    String text = "";
                    if (node.has("payload")) {
                        com.fasterxml.jackson.databind.JsonNode payload = node.get("payload");
                        if (payload.isObject() && payload.has("text")) {
                            text = payload.get("text").asText();
                        } else if (payload.isTextual()) {
                            text = payload.asText();
                        }
                    }

                    if (!text.isEmpty()) {
                        log.info("[CHAT] Message from '{}' (session {}): {}", username, sessionId, text);
                        // Broadcast to all lobby members
                        LobbyRoom.getInstance().broadcastChat(username, text, sessionManager);
                    } else {
                        log.warn("[CHAT] Empty text received from '{}', ignoring", username);
                    }
                }
            } catch (Exception e) {
                // Not JSON or parse failure – ignore for chat purposes
            }

            super.onClientMessage(sessionId, message);
            log.debug("[MESSAGE] ✓ Mensaje procesado correctamente");
        } catch (Exception e) {
            log.error("[MESSAGE] ❌ Error procesando mensaje: {}", e.getMessage(), e);
        }
    }

    public void onClose(WsCloseContext ctx) {
        try {
            UUID sessionId = ctx.attribute("session-uuid");
            if (sessionId == null) {
                log.warn("[CLOSE] ⚠️ Desconexión: Sesión no encontrada en atributos");
                return;
            }

            log.info("[CLOSE] 🔌 Cierre de conexión para sesión: {}", sessionId);
            log.info("[CLOSE] Código de cierre: {}, Razón: {}", ctx.status(), ctx.reason());

            super.onClientDisconnect(sessionId);
            log.info("[CLOSE] ✓ Desconexión procesada correctamente");
        } catch (Exception e) {
            log.error("[CLOSE] ❌ Error procesando desconexión: {}", e.getMessage(), e);
        }
    }

    public void onError(WsErrorContext ctx) {
        try {
            UUID sessionId = ctx.attribute("session-uuid");
            String sessionInfo = sessionId != null ? sessionId.toString() : "unknown";

            log.error("[ERROR] ❌ Error WebSocket en sesión {}: {}",
                    sessionInfo, ctx.error() != null ? ctx.error().getMessage() : "unknown error",
                    ctx.error());
        } catch (Exception e) {
            log.error("[ERROR] ❌ Error procesando error WebSocket: {}", e.getMessage(), e);
        }
    }

    // Sobrescribimos onClientConnect para asegurar compatibilidad
    @Override
    public void onClientConnect(Object session, String username) {
        super.onClientConnect(session, username);
    }
}
