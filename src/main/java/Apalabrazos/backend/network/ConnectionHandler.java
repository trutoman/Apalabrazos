package Apalabrazos.backend.network;

import Apalabrazos.backend.lobby.LobbyRoom;
import Apalabrazos.backend.model.Player;
import Apalabrazos.backend.network.WsMessageType;
import Apalabrazos.backend.service.MatchManager;
import Apalabrazos.backend.service.ConnectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * ConnectionHandler maneja las conexiones WebSocket entrantes.
 *
 * Es la puerta de entrada del Nivel 1 (Red) al Nivel 2 (Sesión).
 * Sus responsabilidades:
 * 1. Aceptar nuevas conexiones WebSocket
 * 2. Crear objetos Player como "anclas" de sesión
 * 3. Vincular la conexión física con la lógica de juego
 * 4. Manejar desconexiones y reconexiones
 *
 * NOTA: Esta es una clase base/interfaz. La implementación real dependería del
 * framework
 * (Spring WebSocket, java.websocket.WebSocketEndpoint, Netty, etc.)
 */
public abstract class ConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);

    protected final MatchManager matchManager = MatchManager.getInstance();
    protected final ConnectionRegistry connectionRegistry = ConnectionRegistry.getInstance();

    /**
     * Se invoca cuando un cliente se conecta.
     * Esta es la única entrada a new Player()
     *
     * @param session      La sesión WebSocket (tipo depende del framework)
     * @param username     El nombre de usuario (viene del cliente)
     * @param cosmosUserId El ID del usuario en Cosmos DB (viene del JWT)
     */
    public void onClientConnect(Object session, String username, String cosmosUserId) {
        try {
            log.info("[CLIENT-CONNECT] Starting connection process for: {} (CosmosUserId: {})", username,
                    cosmosUserId);

            // 1. Nivel 1: Crear abstracción de la conexión física
            UUID sessionId = UUID.randomUUID();
            log.debug("[CLIENT-CONNECT] Generated SessionID: {}", sessionId);

            // Intento de guardar el ID en la propia sesión si es soportado (caso Javalin)
            if (session instanceof io.javalin.websocket.WsContext) {
                ((io.javalin.websocket.WsContext) session).attribute("session-uuid", sessionId);
                log.debug("[CLIENT-CONNECT] SessionID stored in WsContext");
            } else {
                log.warn("[CLIENT-CONNECT] Session is not WsContext, type: {}", session.getClass().getName());
            }

            log.debug("[CLIENT-CONNECT] Creating WebSocketMessageSender for client: {}", sessionId);
            WebSocketMessageSender messageSender = new WebSocketMessageSender(session, sessionId.toString());

            // 2. Nivel 2: Crear el Player (el ancla) — linked to Cosmos DB user
            log.debug("[CLIENT-CONNECT] Creating Player for user: {} (CosmosUserId: {})", username, cosmosUserId);
            Player player = new Player(sessionId, username, cosmosUserId, messageSender);

            // 3. Registrar en ConnectionRegistry
            log.debug("[CLIENT-CONNECT] Registering connection in ConnectionRegistry");
            boolean registered = connectionRegistry.registerConnection(player);

            if (registered) {
                log.info("[CLIENT-CONNECT] Client connected successfully: {} (SessionID: {})", username, sessionId);
                // Auto-join the global lobby room
                LobbyRoom.getInstance().join(sessionId);
                log.debug("[CLIENT-CONNECT] Sending welcome message");
                String welcomeMessage = "{\"type\":\"system\",\"message\":\"¡Bienvenido "
                        + username
                        + "! Connection established.\"}";
                player.sendMessage(welcomeMessage);
                sendLobbyMatchesSnapshot(player);
                log.debug("[CLIENT-CONNECT] Welcome message and lobby snapshot sent");
            } else {
                log.error("[CLIENT-CONNECT] Failed to register player: {} in GameSessionManager", username);
                messageSender.close();
                log.info("[CLIENT-CONNECT] MessageSender closed");
            }

        } catch (Exception e) {
            log.error("[CLIENT-CONNECT] Error processing new connection for {}: {}", username, e.getMessage(), e);
        }
    }

    /**
     * Se invoca cuando un cliente envía un mensaje.
     *
     * @param sessionId      El ID de sesión del cliente
     * @param messageContent El contenido del mensaje (JSON o texto)
     */
    public void onClientMessage(UUID sessionId, String messageContent) {
        try {
            log.debug("[CLIENT-MESSAGE] Looking up player for session: {}", sessionId);
            Player player = connectionRegistry.getPlayerBySessionId(sessionId);

            if (player == null) {
                log.error("[CLIENT-MESSAGE] Session not found: {}", sessionId);
                return;
            }

            log.debug("[WS-INBOUND] Message received from {}: {}", player.getName(), messageContent);

            // Aquí iría la lógica de procesar el mensaje
            // Por ahora solo lo registramos
            log.debug("[CLIENT-MESSAGE] Message processed");

        } catch (Exception e) {
            log.error("[CLIENT-MESSAGE] Error processing message for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Se invoca cuando un cliente se desconecta.
     *
     * @param sessionId El ID de sesión del cliente
     */
    public void onClientDisconnect(UUID sessionId) {
        try {
            log.info("[CLIENT-DISCONNECT] Processing disconnect for session: {}", sessionId);
            Player player = connectionRegistry.unregisterConnection(sessionId);

            // Always leave the lobby, regardless of whether the player was found
            LobbyRoom.getInstance().leave(sessionId);

            // Remove player from any active match
            if (player != null) {
                String leftMatchId = matchManager.leavePlayerFromCurrentMatch(player);
                if (leftMatchId != null) {
                    log.info("[CLIENT-DISCONNECT] Player {} removed from match {} during disconnect",
                            player.getPlayerID(), leftMatchId);
                }

                log.info("[CLIENT-DISCONNECT] Client disconnected successfully: {} (SessionID: {})",
                        player.getName(), sessionId);
                log.debug("[CLIENT-DISCONNECT] Final player state: {}", player.getState());
            } else {
                log.warn("[CLIENT-DISCONNECT] Attempt to disconnect unregistered session: {}", sessionId);
            }

        } catch (Exception e) {
            log.error("[CLIENT-DISCONNECT] Error processing disconnect for session {}: {}", sessionId,
                    e.getMessage(), e);
        }
    }

    /**
     * Se invoca cuando un cliente se reconecta después de una desconexión temporal.
     *
     * @param sessionId  El ID original de sesión
     * @param newSession La nueva sesión WebSocket
     */
    protected void onClientReconnect(UUID sessionId, Object newSession) {
        try {
            Player player = connectionRegistry.getPlayerBySessionId(sessionId);

            if (player == null) {
                log.warn("Session to reconnect not found: {}", sessionId);
                return;
            }

            ((WebSocketMessageSender) player.getSender()).reconnect();

            log.info("Client reconnected: {} (SessionID: {})",
                    player.getName(), sessionId);
            player.sendMessage("Reconnection established");

        } catch (Exception e) {
            log.error("Error processing reconnection: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía al cliente recién autenticado el snapshot actual de partidas activas del lobby.
     */
    private void sendLobbyMatchesSnapshot(Player player) {
        if (player == null) {
            return;
        }

        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("matches", matchManager.getActiveMatchesSummary());

            java.util.Map<String, Object> message = new java.util.LinkedHashMap<>();
            message.put("type", WsMessageType.LOBBY_MATCHES_SNAPSHOT);
            message.put("payload", payload);

            player.sendMessage(message);
            log.info("[CLIENT-CONNECT] Lobby snapshot sent to {} with {} active matches",
                    player.getName(), matchManager.getActiveMatchCount());
        } catch (Exception e) {
            log.error("[CLIENT-CONNECT] Error sending lobby snapshot to {}: {}",
                    player.getName(), e.getMessage(), e);
        }
    }

    /**
     * Broadcast a todos los clientes conectados.
     *
     * @param message El mensaje a enviar
     */
    public void broadcastToAll(Object message) {
        connectionRegistry.broadcastToAll(message);
    }

    /**
     * Enviar mensaje a un cliente específico.
     *
     * @param sessionId El ID de sesión
     * @param message   El mensaje a enviar
     */
    public boolean sendToClient(UUID sessionId, Object message) {
        return connectionRegistry.sendToPlayer(sessionId, message);
    }

    /**
     * Obtener el número de clientes conectados.
     */
    public int getConnectedClientsCount() {
        return connectionRegistry.getActiveConnectionCount();
    }
}
