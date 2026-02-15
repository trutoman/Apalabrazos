package UE_Proyecto_Ingenieria.Apalabrazos.backend.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Implementación de MessageSender para WebSocket.
 * Abstrae el envío de mensajes a través de una conexión WebSocket.
 *
 * Esta clase actúa como puente entre la lógica de juego (GameService, GameSessionManager)
 * y la capa de red (WebSocket).
 */
public class WebSocketMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(WebSocketMessageSender.class);

    // La sesión WebSocket de Javalin
    private final io.javalin.websocket.WsContext session;
    private boolean connected = true;
    private final String clientId;

    // Cola de mensajes para casos de desconexión temporal
    private final Queue<Object> messageQueue = new ConcurrentLinkedQueue<>();

    /**
     * Constructor para WebSocket Javalin
     * @param session La sesión WebSocket (WsContext)
     * @param clientId Identificador del cliente (IP, sessionId, etc)
     */
    public WebSocketMessageSender(Object session, String clientId) {
        if (!(session instanceof io.javalin.websocket.WsContext)) {
             throw new IllegalArgumentException("Se esperaba una sesión de tipo WsContext");
        }
        this.session = (io.javalin.websocket.WsContext) session;
        this.clientId = clientId;
        log.info("WebSocketMessageSender creado para cliente: {}", clientId);
    }

    @Override
    public void send(Object message) {
        if (!connected) {
            log.warn("[SEND] ⚠️ Cliente {} desconectado. Encolando mensaje (queue size: {})",
                clientId, messageQueue.size() + 1);
            messageQueue.offer(message);
            return;
        }

        try {
            // Convertir a String (JSON) - Por ahora toString() para probar
            String messageStr = message instanceof String ? (String) message : message.toString();

            log.debug("[SEND] 📤 Enviando mensaje a {}: {}", clientId, messageStr);

            // Enviar usando Javalin
            session.send(messageStr);
            log.debug("[SEND] ✓ Mensaje enviado exitosamente a: {}", clientId);

        } catch (Exception e) {
            log.error("[SEND] ❌ Error enviando mensaje a {}: {}", clientId, e.getMessage(), e);
            this.connected = false;
            log.warn("[SEND] Conexión marcada como desconectada. Encolando mensaje");
            messageQueue.offer(message); // Encolar para luego
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        this.connected = false;
        log.info("[CLOSE] 🔌 WebSocketMessageSender cerrado para cliente: {} (mensajes en cola: {})",
            clientId, messageQueue.size());

        try {
            // session.close() si es necesario
            log.debug("[CLOSE] ✓ Conexión cerrada correctamente");
        } catch (Exception e) {
            log.error("[CLOSE] ❌ Error cerrando conexión para {}: {}", clientId, e.getMessage(), e);
        }
    }

    /**
     * Reconectar después de una desconexión temporal
     */
    public void reconnect() {
        try {
            this.connected = true;
            int queuedMessages = messageQueue.size();
            log.info("[RECONNECT] 🔄 Cliente {} reconectado. Enviando {} mensajes en cola", clientId, queuedMessages);

            // Enviar todos los mensajes encolados
            int sent = 0;
            while (!messageQueue.isEmpty()) {
                Object queuedMessage = messageQueue.poll();
                log.debug("[RECONNECT] 📤 Enviando mensaje encolado {}/{}", ++sent, queuedMessages);
                send(queuedMessage);
            }
            log.info("[RECONNECT] ✓ Reconexión completada. {} mensajes reenviados", sent);
        } catch (Exception e) {
            log.error("[RECONNECT] ❌ Error durante reconexión del cliente {}: {}", clientId, e.getMessage(), e);
        }
    }

    /**
     * Obtener el ID del cliente
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Obtener la sesión WebSocket bruta (si es necesaria)
     */
    public Object getSession() {
        return session;
    }
}
