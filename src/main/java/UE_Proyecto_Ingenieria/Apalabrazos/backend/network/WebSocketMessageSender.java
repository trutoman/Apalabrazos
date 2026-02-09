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

    // La sesión WebSocket (podría ser javax.websocket.Session o Spring WebSocket)
    // Para mantener independencia, usamos Object y delegamos en la interfaz
    private final Object session;
    private boolean connected = true;
    private final String clientId;

    // Cola de mensajes para casos de desconexión temporal
    private final Queue<Object> messageQueue = new ConcurrentLinkedQueue<>();

    /**
     * Constructor para WebSocket genérico
     * @param session La sesión WebSocket
     * @param clientId Identificador del cliente (IP, sessionId, etc)
     */
    public WebSocketMessageSender(Object session, String clientId) {
        this.session = session;
        this.clientId = clientId;
        log.info("WebSocketMessageSender creado para cliente: {}", clientId);
    }

    @Override
    public void send(Object message) {
        if (!connected) {
            log.warn("Cliente {} desconectado. Encolando mensaje: {}", clientId, message);
            messageQueue.offer(message);
            return;
        }

        try {
            // Aquí se enviaría a través del WebSocket
            // String json = serializeToJson(message);
            // session.getBasicRemote().sendText(json);

            // Por ahora solo registramos (será implementado cuando se añada el servidor WebSocket)
            log.debug("Enviando mensaje a {}: {}", clientId, message);

        } catch (Exception e) {
            log.error("Error enviando mensaje a {}: {}", clientId, e.getMessage());
            this.connected = false;
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
        log.info("WebSocketMessageSender cerrado para cliente: {}", clientId);

        try {
            // session.close() si es necesario
        } catch (Exception e) {
            log.error("Error cerrando conexión para {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Reconectar después de una desconexión temporal
     */
    public void reconnect() {
        this.connected = true;
        log.info("Cliente {} reconectado. Enviando {} mensajes en cola", clientId, messageQueue.size());

        // Enviar todos los mensajes encolados
        while (!messageQueue.isEmpty()) {
            Object queuedMessage = messageQueue.poll();
            send(queuedMessage);
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
