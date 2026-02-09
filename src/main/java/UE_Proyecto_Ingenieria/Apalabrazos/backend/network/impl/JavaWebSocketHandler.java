package UE_Proyecto_Ingenieria.Apalabrazos.backend.network.impl;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Implementación de ConnectionHandler para Java WebSocket API (@javax.websocket)
 *
 * Esta sería la implementación real para uso con Java EE/Jakarta EE.
 * Para usarla, necesitarías añadir javax.websocket al pom.xml:
 *
 * {@code
 * <dependency>
 *     <groupId>javax.websocket</groupId>
 *     <artifactId>javax.websocket-api</artifactId>
 *     <version>1.1</version>
 * </dependency>
 * }
 *
 * Ejemplo de uso en un endpoint:
 * {@code
 * @ServerEndpoint("/ws/game/{username}")
 * public class GameWebSocketEndpoint extends JavaWebSocketHandler {
 *     // Automáticamente usa los métodos de ConnectionHandler
 * }
 * }
 */
public class JavaWebSocketHandler extends ConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(JavaWebSocketHandler.class);

    /**
     * Este método se invocaría con la anotación @OnOpen
     *
     * @param session La sesión javax.websocket.Session
     * @param username Parámetro del path {username}
     */
    public void onOpen(Object session, String username) {
        log.info("→ WebSocket OnOpen: {}", username);
        onClientConnect(session, username);
    }

    /**
     * Este método se invocaría con la anotación @OnMessage
     *
     * @param messageContent El contenido del mensaje
     * @param sessionId El ID de sesión del cliente
     */
    public void onMessage(String messageContent, UUID sessionId) {
        log.debug("→ WebSocket OnMessage: {}", messageContent);
        onClientMessage(sessionId, messageContent);
    }

    /**
     * Este método se invocaría con la anotación @OnClose
     *
     * @param sessionId El ID de sesión del cliente
     */
    public void onClose(UUID sessionId) {
        log.info("→ WebSocket OnClose: {}", sessionId);
        onClientDisconnect(sessionId);
    }

    /**
     * Este método se invocaría con la anotación @OnError
     *
     * @param sessionId El ID de sesión del cliente
     * @param throwable El error ocurrido
     */
    public void onError(UUID sessionId, Throwable throwable) {
        log.error("✗ WebSocket Error en sesión {}: {}", sessionId, throwable.getMessage());
        // Posible reconexión automática
    }
}
