package Apalabrazos.backend.network.server;

import Apalabrazos.backend.lobby.LobbyRoom;
import Apalabrazos.backend.network.ConnectionHandler;
import Apalabrazos.backend.tools.JwtService;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
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

            String userId = ctx.pathParam("userId"); // Changed from username to userId
            String tokenUserId = jwtService.extractUserId(jwt); // Extract userId from token
            String tokenUsername = jwtService.extractUsername(jwt); // Still need username for logging/onClientConnect
            log.debug("[CONNECT] UserId en URL: {}, UserId en token: {}", userId, tokenUserId);

            if (tokenUserId == null || !tokenUserId.equalsIgnoreCase(userId)) {
                log.warn("[CONNECT] ❌ Conexión rechazada: UserId mismatch (URL: {}, Token: {})",
                        userId, tokenUserId);
                ctx.closeSession(4003, "User mismatch");
                return;
            }

            log.info("[CONNECT] ✅ Conexión autenticada para usuario: {} (CosmosUserId: {})", tokenUsername,
                    tokenUserId);
            onClientConnect(ctx, tokenUsername, tokenUserId); // Pass username and userId
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

            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(message);
                String type = node.has("type") ? node.get("type").asText() : "";

                // ── CHAT ────────────────────────────────────────────────────
                if ("chat".equalsIgnoreCase(type)) {
                    String username = "Unknown";
                    Apalabrazos.backend.model.Player player = connectionRegistry.getPlayerBySessionId(sessionId);
                    if (player != null)
                        username = player.getName();

                    // Frontend sends { type, data: payload } via SocketClient.send()
                    // but chat uses { type, payload } – accept both keys
                    com.fasterxml.jackson.databind.JsonNode dataNode = node.has("data") ? node.get("data")
                            : node.get("payload");

                    String text = "";
                    if (dataNode != null) {
                        if (dataNode.isObject() && dataNode.has("text")) {
                            text = dataNode.get("text").asText();
                        } else if (dataNode.isTextual()) {
                            text = dataNode.asText();
                        }
                    }

                    if (!text.isEmpty()) {
                        log.info("[CHAT] Message from '{}' (session {}): {}", username, sessionId, text);
                        LobbyRoom.getInstance().broadcastChat(username, text, matchManager);
                    } else {
                        log.warn("[CHAT] Empty text received from '{}', ignoring", username);
                    }

                    // ── GAME CREATION REQUEST ────────────────────────────────────
                } else if ("GameCreationRequest".equalsIgnoreCase(type)) {
                    Apalabrazos.backend.model.Player player = connectionRegistry.getPlayerBySessionId(sessionId);
                    String username = player != null ? player.getName() : "Unknown";

                    com.fasterxml.jackson.databind.JsonNode data = node.get("data");
                    if (data == null) {
                        log.warn("[GAME-CREATE] ⚠️ GameCreationRequest sin campo 'data' de '{}'", username);
                    } else {
                        String gameName = data.path("name").asText("?");
                        int players = data.path("players").asInt(0);
                        String gameType = data.path("gameType").asText("?");
                        double time = data.path("time").asDouble(0);
                        String difficulty = data.path("difficulty").asText("?");
                        String createdByUserId = player != null ? player.getCosmosUserId() : "Unknown";
                        long requestedAt = data.path("requestedAt").asLong(0);

                        log.info("[GAME-CREATE] 🎮 Solicitud de creación de partida recibida de '{}' (sesión {})",
                                username, sessionId);
                        log.info("[GAME-CREATE]   name={}, players={}, type={}, time={}min, difficulty={}",
                                gameName, players, gameType, time, difficulty);
                        log.info("[GAME-CREATE]   creatorName={}, creatorId={}, requestedAt={}",
                                username, createdByUserId, requestedAt);

                        // Convert frontend data to backend model
                        int timerSeconds = (int) (time * 60);
                        Apalabrazos.backend.model.QuestionLevel qLevel;
                        try {
                            qLevel = Apalabrazos.backend.model.QuestionLevel.fromValue(difficulty);
                        } catch (Exception e) {
                            qLevel = Apalabrazos.backend.model.QuestionLevel.MEDIUM; // Fallback
                        }

                        // Default to 27 questions for now, as frontend doesn't send it yet.
                        Apalabrazos.backend.model.GamePlayerConfig config = new Apalabrazos.backend.model.GamePlayerConfig(
                                player, timerSeconds, qLevel, players, 27);

                        // Default GameType to HIGHER_POINTS_WINS
                        config.setGameType(Apalabrazos.backend.model.GameType.HIGHER_POINTS_WINS);

                        // Publicar el evento de creación de partida
                        Apalabrazos.backend.events.GameCreationRequestedEvent creationEvent = new Apalabrazos.backend.events.GameCreationRequestedEvent(
                                config, gameName);

                        Apalabrazos.backend.events.GlobalAsyncEventBus.getInstance().publish(creationEvent);
                    }

                    // ── JOIN MATCH REQUEST ───────────────────────────────────────
                } else if ("JoinMatchRequest".equalsIgnoreCase(type)) {
                    Apalabrazos.backend.model.Player player = connectionRegistry.getPlayerBySessionId(sessionId);
                    String username = player != null ? player.getName() : "Unknown";

                    com.fasterxml.jackson.databind.JsonNode data = node.get("data");
                    String roomId = data != null ? data.path("roomId").asText("").trim() : "";

                    if (player == null) {
                        log.warn("[GAME-JOIN] ⚠️ JoinMatchRequest received but player was not found for session {}", sessionId);
                    } else if (roomId.isEmpty()) {
                        log.warn("[GAME-JOIN] ⚠️ JoinMatchRequest sin roomId de '{}'", username);
                        player.sendMessage(java.util.Map.of(
                                "type", "JoinMatchRequestInvalid",
                                "payload", java.util.Map.of(
                                        "roomId", roomId,
                                        "cause", "No se ha indicado una sala válida.")));
                    } else {
                        log.info("[GAME-JOIN] 🚪 Solicitud de unión recibida de '{}' para la sala {}", username, roomId);
                        boolean joined = matchManager.joinPlayerToMatch(player, roomId);
                        if (joined) {
                            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>(matchManager.getMatchSummary(roomId));
                            payload.put("roomId", roomId);
                            payload.put("joined", true);
                            player.sendMessage(java.util.Map.of(
                                    "type", "JoinMatchRequestValid",
                                    "payload", payload));
                        } else {
                            player.sendMessage(java.util.Map.of(
                                    "type", "JoinMatchRequestInvalid",
                                    "payload", java.util.Map.of(
                                            "roomId", roomId,
                                            "cause", "No se ha podido unir a la partida. Puede estar llena o no existir.")));
                        }
                    }

                    // ── LEAVE MATCH REQUEST ──────────────────────────────────────
                } else if ("LeaveMatchRequest".equalsIgnoreCase(type)) {
                    Apalabrazos.backend.model.Player player = connectionRegistry.getPlayerBySessionId(sessionId);
                    String username = player != null ? player.getName() : "Unknown";

                    if (player == null) {
                        log.warn("[GAME-LEAVE] ⚠️ LeaveMatchRequest received but player was not found for session {}", sessionId);
                        return;
                    }

                    log.info("[GAME-LEAVE] 🚪 Solicitud de salida recibida de '{}'", username);
                    String leftRoomId = matchManager.leavePlayerFromCurrentMatch(player);

                    if (leftRoomId != null && !leftRoomId.isBlank()) {
                        player.sendMessage(java.util.Map.of(
                                "type", "LeaveMatchRequestValid",
                                "payload", java.util.Map.of(
                                        "roomId", leftRoomId,
                                        "left", true)));
                    } else {
                        player.sendMessage(java.util.Map.of(
                                "type", "LeaveMatchRequestInvalid",
                                "payload", java.util.Map.of(
                                        "cause", "No estás unido a ninguna partida.")));
                    }

                    // ── START MATCH REQUEST ─────────────────────────────────────
                } else if ("StartMatchRequest".equalsIgnoreCase(type)) {
                    Apalabrazos.backend.model.Player player = connectionRegistry.getPlayerBySessionId(sessionId);

                    if (player == null) {
                        log.warn("[GAME-START] ⚠️ StartMatchRequest received but player was not found for session {}", sessionId);
                        return;
                    }

                    com.fasterxml.jackson.databind.JsonNode data = node.get("data");
                    String roomId = data != null ? data.path("roomId").asText("").trim() : "";

                    if (roomId.isEmpty()) {
                        player.sendMessage(java.util.Map.of(
                                "type", "StartMatchRequestInvalid",
                                "payload", java.util.Map.of(
                                        "cause", "No se ha indicado una sala válida para iniciar la partida.")));
                        return;
                    }

                    log.info("[GAME-START] ▶️ Solicitud de inicio recibida de '{}' para la sala {}",
                            player.getName(), roomId);

                    Apalabrazos.backend.events.GlobalAsyncEventBus.getInstance().publish(
                            new Apalabrazos.backend.events.GameStartedRequestEvent(roomId, player.getPlayerID()));

                    // ── GAME CONTROLLER READY ───────────────────────────────────
                } else if ("GameControllerReady".equalsIgnoreCase(type)) {
                    Apalabrazos.backend.model.Player player = connectionRegistry.getPlayerBySessionId(sessionId);

                    if (player == null) {
                        log.warn("[GAME-READY] ⚠️ GameControllerReady received but player was not found for session {}", sessionId);
                        return;
                    }

                    com.fasterxml.jackson.databind.JsonNode data = node.get("data");
                    String roomId = data != null ? data.path("roomId").asText("").trim() : "";

                    if (roomId.isEmpty()) {
                        log.warn("[GAME-READY] ⚠️ GameControllerReady sin roomId de '{}'", player.getName());
                        return;
                    }

                    boolean readyAccepted = matchManager.markMatchControllerReady(roomId, player.getPlayerID());
                    log.info("[GAME-READY] 🎛️ Controller ready de '{}' para sala {} => {}",
                            player.getName(), roomId, readyAccepted ? "accepted" : "ignored");

                    // ── UNKNOWN ──────────────────────────────────────────────────
                } else if (!type.isEmpty() && !"PING".equalsIgnoreCase(type)) {
                    log.warn("[MESSAGE] ⚠️ Tipo de mensaje desconocido: '{}' de sesión {}", type, sessionId);
                }

            } catch (Exception e) {
                log.warn("[MESSAGE] ⚠️ No se pudo parsear el mensaje como JSON: {}", e.getMessage());
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
}
