package UE_Proyecto_Ingenieria.Apalabrazos.backend.network.server;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.ConnectionHandler;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.tools.JwtService;
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
        // Extract token from query parameter
        String token = ctx.queryParam("token");

        // Validate token
        if (token == null || token.trim().isEmpty()) {
            log.warn("WebSocket connection rejected: No token provided");
            ctx.closeSession(4001, "Authentication required");
            return;
        }

        DecodedJWT jwt = jwtService.verifyToken(token);
        if (jwt == null) {
            log.warn("WebSocket connection rejected: Invalid token");
            ctx.closeSession(4002, "Invalid authentication token");
            return;
        }

        String username = ctx.pathParam("username");
        String tokenUsername = jwtService.extractUsername(jwt);
        if (tokenUsername == null || !tokenUsername.equalsIgnoreCase(username)) {
            log.warn("WebSocket connection rejected: Username does not match token");
            ctx.closeSession(4003, "User mismatch");
            return;
        }

        log.info("WebSocket connection authenticated for user {}", username);
        onClientConnect(ctx, username);
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
