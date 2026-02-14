package UE_Proyecto_Ingenieria.Apalabrazos.backend.network.server;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.ConnectionHandler;
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

    public void onConnect(WsConnectContext ctx) {
        // Extract token from query parameter
        String token = ctx.queryParam("token");

        // Validate token
        if (token == null || token.trim().isEmpty()) {
            log.warn("WebSocket connection rejected: No token provided");
            ctx.closeSession(4001, "Authentication required");
            return;
        }

        // TODO: In production, validate token properly (JWT, session, etc.)
        // For now, accept any non-empty token
        if (!isValidToken(token)) {
            log.warn("WebSocket connection rejected: Invalid token");
            ctx.closeSession(4002, "Invalid authentication token");
            return;
        }

        log.info("WebSocket connection authenticated with token");

        String username = ctx.pathParam("username");
        onClientConnect(ctx, username);
    }

    /**
     * Validates the authentication token.
     * TODO: Implement proper token validation (JWT, database session, etc.)
     */
    private boolean isValidToken(String token) {
        // For now, accept "dummy-token" or any non-empty token
        // In production, validate against JWT or session store
        return token != null && !token.trim().isEmpty();
    }

    public void onMessage(WsMessageContext ctx) {
        UUID sessionId = ctx.attribute("session-uuid");
        if (sessionId != null) {
            super.onClientMessage(sessionId, ctx.message());
        }
    }

    public void onClose(WsCloseContext ctx) {
        UUID sessionId = ctx.attribute("session-uuid");
        if (sessionId != null) {
            super.onClientDisconnect(sessionId);
        }
    }

    public void onError(WsErrorContext ctx) {
        // Loguear error
    }

    // Sobrescribimos onClientConnect para asegurar compatibilidad
    @Override
    public void onClientConnect(Object session, String username) {
        super.onClientConnect(session, username);
    }
}
