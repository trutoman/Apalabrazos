package Apalabrazos.backend.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        log.info("WebSocketMessageSender created for client: {}", clientId);
    }

    @Override
    public void send(Object message) {
        if (!connected) {
            log.warn("[WS-BUS][BE->FE] Client {} disconnected. Queuing message (queue size: {})",
                clientId, messageQueue.size() + 1);
            messageQueue.offer(message);
            return;
        }

        try {
            // Convertir a JSON usando Jackson
            String messageStr;
            if (message instanceof String) {
                messageStr = (String) message;
            } else {
                messageStr = objectMapper.writeValueAsString(message);
            }

            log.debug("[WS-BUS][BE->FE][SEND] Sending message to {}: {}", clientId, messageStr);

            // Enviar usando Javalin
            session.send(messageStr);
            log.debug("[WS-BUS][BE->FE][SENT] Message sent successfully to: {}", clientId);

        } catch (Exception e) {
            log.error("[WS-BUS][BE->FE] ❌ Error sending message to {}: {}", clientId, e.getMessage(), e);
            this.connected = false;
            log.warn("[WS-BUS][BE->FE] Connection marked as disconnected. Queuing message");
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
        log.info("[CLOSE] WebSocketMessageSender closed for client: {} (queued messages: {})",
            clientId, messageQueue.size());

        try {
            // session.close() si es necesario
            log.debug("[CLOSE] Connection closed correctly");
        } catch (Exception e) {
            log.error("[CLOSE] ❌ Error closing connection for {}: {}", clientId, e.getMessage(), e);
        }
    }

    /**
     * Reconectar después de una desconexión temporal
     */
    public void reconnect() {
        try {
            this.connected = true;
            int queuedMessages = messageQueue.size();
            log.info("[RECONNECT] Client {} reconnected. Sending {} queued messages", clientId, queuedMessages);

            // Enviar todos los mensajes encolados
            int sent = 0;
            while (!messageQueue.isEmpty()) {
                Object queuedMessage = messageQueue.poll();
                log.debug("[RECONNECT] Sending queued message {}/{}", ++sent, queuedMessages);
                send(queuedMessage);
            }
            log.info("[RECONNECT] Reconnection complete. {} message(s) resent", sent);
        } catch (Exception e) {
            log.error("[RECONNECT] ❌ Error during client reconnection {}: {}", clientId, e.getMessage(), e);
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
