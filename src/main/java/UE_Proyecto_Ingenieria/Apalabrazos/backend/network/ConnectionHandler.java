package UE_Proyecto_Ingenieria.Apalabrazos.backend.network;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameSessionManager;
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
 * NOTA: Esta es una clase base/interfaz. La implementación real dependería del framework
 * (Spring WebSocket, java.websocket.WebSocketEndpoint, Netty, etc.)
 */
public abstract class ConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);

    protected final GameSessionManager sessionManager = GameSessionManager.getInstance();

    /**
     * Se invoca cuando un cliente se conecta.
     * Esta es la única entrada a new Player()
     *
     * @param session La sesión WebSocket (tipo depende del framework)
     * @param username El nombre de usuario (viene del cliente)
     */
    public void onClientConnect(Object session, String username) {
        try {
            // 1. Nivel 1: Crear abstracción de la conexión física
            UUID sessionId = UUID.randomUUID();

            // Intento de guardar el ID en la propia sesión si es soportado (caso Javalin)
            if (session instanceof io.javalin.websocket.WsContext) {
                 ((io.javalin.websocket.WsContext) session).attribute("session-uuid", sessionId);
            }

            WebSocketMessageSender messageSender = new WebSocketMessageSender(session, sessionId.toString());

            // 2. Nivel 2: Crear el Player (el ancla)
            Player player = new Player(sessionId, username, messageSender);

            // 3. Registrar en GameSessionManager
            boolean registered = sessionManager.registerConnection(player);

            if (registered) {
                log.info("✓ Cliente conectado: {} (SessionID: {})", username, sessionId);
                player.sendMessage("¡Bienvenido " + username + "! Conexión establecida.");
            } else {
                log.error("✗ No se pudo registrar el jugador: {}", username);
                messageSender.close();
            }

        } catch (Exception e) {
            log.error("Error procesando nueva conexión: {}", e.getMessage(), e);
        }
    }

    /**
     * Se invoca cuando un cliente envía un mensaje.
     *
     * @param sessionId El ID de sesión del cliente
     * @param messageContent El contenido del mensaje (JSON o texto)
     */
    public void onClientMessage(UUID sessionId, String messageContent) {
        try {
            Player player = sessionManager.getPlayerBySessionId(sessionId);

            if (player == null) {
                log.error("Sesión no encontrada: {}", sessionId);
                return;
            }

            log.debug("Mensaje de {}: {}", player.getName(), messageContent);

            // Aquí iría la lógica de procesar el mensaje
            // Por ahora solo lo registramos

        } catch (Exception e) {
            log.error("Error procesando mensaje: {}", e.getMessage(), e);
        }
    }

    /**
     * Se invoca cuando un cliente se desconecta.
     *
     * @param sessionId El ID de sesión del cliente
     */
    public void onClientDisconnect(UUID sessionId) {
        try {
            Player player = sessionManager.unregisterConnection(sessionId);

            if (player != null) {
                log.info("✓ Cliente desconectado: {} (SessionID: {})",
                        player.getName(), sessionId);
            } else {
                log.warn("Intento de desconectar sesión no registrada: {}", sessionId);
            }

        } catch (Exception e) {
            log.error("Error procesando desconexión: {}", e.getMessage(), e);
        }
    }

    /**
     * Se invoca cuando un cliente se reconecta después de una desconexión temporal.
     *
     * @param sessionId El ID original de sesión
     * @param newSession La nueva sesión WebSocket
     */
    protected void onClientReconnect(UUID sessionId, Object newSession) {
        try {
            Player player = sessionManager.getPlayerBySessionId(sessionId);

            if (player == null) {
                log.warn("Sesión a reconectar no encontrada: {}", sessionId);
                return;
            }

            // Reemplazar el MessageSender con la nueva conexión
            WebSocketMessageSender newSender =
                new WebSocketMessageSender(newSession, sessionId.toString());

            // Actualizar el jugador (esto requeriría un setter en Player)
            // player.setMessageSender(newSender);

            ((WebSocketMessageSender) player.getSender()).reconnect();

            log.info("✓ Cliente reconectado: {} (SessionID: {})",
                    player.getName(), sessionId);
            player.sendMessage("Reconexión establecida");

        } catch (Exception e) {
            log.error("Error procesando reconexión: {}", e.getMessage(), e);
        }
    }

    /**
     * Broadcast a todos los clientes conectados.
     * @param message El mensaje a enviar
     */
    public void broadcastToAll(Object message) {
        sessionManager.broadcastToAll(message);
    }

    /**
     * Enviar mensaje a un cliente específico.
     * @param sessionId El ID de sesión
     * @param message El mensaje a enviar
     */
    public boolean sendToClient(UUID sessionId, Object message) {
        return sessionManager.sendToPlayer(sessionId, message);
    }

    /**
     * Obtener el número de clientes conectados.
     */
    public int getConnectedClientsCount() {
        return sessionManager.getActiveConnectionCount();
    }
}
