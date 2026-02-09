package UE_Proyecto_Ingenieria.Apalabrazos.backend.network.impl;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Implementación de ConnectionHandler para Spring WebSocket
 *
 * Para usarla, necesitarías añadir Spring WebSocket al pom.xml:
 *
 * {@code
 * <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-websocket</artifactId>
 *     <version>3.0.0</version>
 * </dependency>
 * }
 *
 * Ejemplo de uso:
 * {@code
 * @Component
 * public class GameWebSocketHandler extends SpringWebSocketHandler {
 *
 *     @Override
 *     public void afterConnectionEstablished(WebSocketSession session)
 *             throws Exception {
 *         String username = extractUsername(session);
 *         onOpen(session, username);
 *     }
 * }
 * }
 */
public class SpringWebSocketHandler extends ConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(SpringWebSocketHandler.class);

    /**
     * Invocado cuando se establece la conexión WebSocket
     *
     * @param session La sesión org.springframework.web.socket.WebSocketSession
     * @param username El nombre de usuario
     */
    public void afterConnectionEstablished(Object session, String username) {
        log.info("→ Spring WebSocket Connected: {}", username);
        onClientConnect(session, username);
    }

    /**
     * Invocado cuando se recibe un mensaje
     *
     * @param sessionId El ID de sesión
     * @param messageContent El contenido del mensaje
     */
    public void handleMessage(UUID sessionId, String messageContent) {
        log.debug("→ Spring WebSocket Message: {}", messageContent);
        onClientMessage(sessionId, messageContent);
    }

    /**
     * Invocado cuando se cierra la conexión
     *
     * @param sessionId El ID de sesión
     */
    public void afterConnectionClosed(UUID sessionId) {
        log.info("→ Spring WebSocket Closed: {}", sessionId);
        onClientDisconnect(sessionId);
    }

    /**
     * Invocado cuando ocurre un error
     *
     * @param sessionId El ID de sesión
     * @param exception La excepción ocurrida
     */
    public void handleTransportError(UUID sessionId, Exception exception) {
        log.error("✗ Spring WebSocket Error en sesión {}: {}",
                 sessionId, exception.getMessage());
    }
}
