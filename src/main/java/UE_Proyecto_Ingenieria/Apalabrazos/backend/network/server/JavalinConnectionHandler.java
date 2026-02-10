package UE_Proyecto_Ingenieria.Apalabrazos.backend.network.server;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.ConnectionHandler;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsMessageContext;

import java.util.UUID;

/**
 * Adaptador de ConnectionHandler para Javalin.
 */
public class JavalinConnectionHandler extends ConnectionHandler {

    public void onConnect(WsConnectContext ctx) {
        String username = ctx.pathParam("username");
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
