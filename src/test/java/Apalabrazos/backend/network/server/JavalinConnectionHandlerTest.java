package Apalabrazos.backend.network.server;

import Apalabrazos.backend.model.Player;
import Apalabrazos.backend.network.MockMessageSender;
import Apalabrazos.backend.service.ConnectionRegistry;
import Apalabrazos.backend.service.MatchManager;
import io.javalin.websocket.WsMessageContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JavalinConnectionHandlerTest {

    private final JavalinConnectionHandler handler = new JavalinConnectionHandler();
    private final MatchManager matchManager = MatchManager.getInstance();
    private final ConnectionRegistry connectionRegistry = ConnectionRegistry.getInstance();

    @BeforeEach
    void setUp() {
        matchManager.clearAllMatches();
        connectionRegistry.clearAllConnections();
    }

    @Test
    void malformedJsonDoesNotThrowAndDoesNotCreateMatches() {
        UUID sessionId = UUID.randomUUID();
        registerPlayer(sessionId, "creator", "creator-id");
        WsMessageContext ctx = messageContext(sessionId, "{not-json");

        assertDoesNotThrow(() -> handler.onMessage(ctx));
        assertEquals(0, matchManager.getActiveMatchCount());
    }

    @Test
    void gameCreationRequestMissingDataDoesNotThrowAndDoesNotCreateMatches() {
        UUID sessionId = UUID.randomUUID();
        registerPlayer(sessionId, "creator", "creator-id");
        WsMessageContext ctx = messageContext(sessionId, "{\"type\":\"GameCreationRequest\"}");

        assertDoesNotThrow(() -> handler.onMessage(ctx));
        assertEquals(0, matchManager.getActiveMatchCount());
    }

    @Test
    void gameCreationRequestWithIncompleteDataDoesNotThrowAndDoesNotCreateMatches() {
        UUID sessionId = UUID.randomUUID();
        registerPlayer(sessionId, "creator", "creator-id");
        WsMessageContext ctx = messageContext(sessionId,
                "{\"type\":\"GameCreationRequest\",\"data\":{\"name\":\"Sala\"}}");

        assertDoesNotThrow(() -> handler.onMessage(ctx));
        assertEquals(0, matchManager.getActiveMatchCount());
    }

    @Test
    void joinMatchRequestMissingRoomIdReturnsInvalidResponse() {
        UUID sessionId = UUID.randomUUID();
        Player player = registerPlayer(sessionId, "joiner", "joiner-id");
        WsMessageContext ctx = messageContext(sessionId, "{\"type\":\"JoinMatchRequest\",\"data\":{}}");

        assertDoesNotThrow(() -> handler.onMessage(ctx));
        Map<String, Object> message = lastMessage(player);
        assertNotNull(message);
        assertEquals("JoinMatchRequestInvalid", message.get("type"));
        assertEquals("No se ha indicado una sala válida.", payloadValue(message, "cause"));
        assertEquals("", payloadValue(message, "roomId"));
    }

    @Test
    void startMatchRequestMissingRoomIdReturnsInvalidResponse() {
        UUID sessionId = UUID.randomUUID();
        Player player = registerPlayer(sessionId, "creator", "creator-id");
        WsMessageContext ctx = messageContext(sessionId, "{\"type\":\"StartMatchRequest\",\"data\":{}}");

        assertDoesNotThrow(() -> handler.onMessage(ctx));
        Map<String, Object> message = lastMessage(player);
        assertNotNull(message);
        assertEquals("StartMatchRequestInvalid", message.get("type"));
        assertEquals("No se ha indicado una sala válida para iniciar la partida.", payloadValue(message, "cause"));
        assertEquals("", payloadValue(message, "roomId"));
    }

    @Test
    void gameControllerReadyMissingRoomIdDoesNotThrowOrSendResponse() {
        UUID sessionId = UUID.randomUUID();
        Player player = registerPlayer(sessionId, "creator", "creator-id");
        WsMessageContext ctx = messageContext(sessionId, "{\"type\":\"GameControllerReady\",\"data\":{}}");

        assertDoesNotThrow(() -> handler.onMessage(ctx));
        assertFalse(senderMessageCount(player) > 0);
        assertEquals(0, senderMessageCount(player));
    }

    @Test
    void answerSubmittedWithInvalidQuestionIndexDoesNotThrowOrSendResponse() {
        UUID sessionId = UUID.randomUUID();
        Player player = registerPlayer(sessionId, "player", "player-id");
        WsMessageContext ctx = messageContext(sessionId,
                "{\"type\":\"AnswerSubmitted\",\"data\":{\"selectedOption\":0}}"
        );

        assertDoesNotThrow(() -> handler.onMessage(ctx));
        assertEquals(0, senderMessageCount(player));
    }

    @Test
    void answerSubmittedWithOutOfRangeOptionDoesNotThrowOrSendResponse() {
        UUID sessionId = UUID.randomUUID();
        Player player = registerPlayer(sessionId, "player", "player-id");
        WsMessageContext ctx = messageContext(sessionId,
                "{\"type\":\"AnswerSubmitted\",\"data\":{\"questionIndex\":0,\"selectedOption\":99}}"
        );

        assertDoesNotThrow(() -> handler.onMessage(ctx));
        assertEquals(0, senderMessageCount(player));
    }

    @Test
    void chatMessageWithTextPayloadDoesNotThrow() {
        UUID sessionId = UUID.randomUUID();
        registerPlayer(sessionId, "speaker", "speaker-id");
        WsMessageContext ctx = messageContext(sessionId,
                "{\"type\":\"chat\",\"payload\":{\"text\":\"hola\"}}"
        );

        assertDoesNotThrow(() -> handler.onMessage(ctx));
        assertEquals(0, matchManager.getActiveMatchCount());
    }

    private Player registerPlayer(UUID sessionId, String name, String playerId) {
        MockMessageSender sender = new MockMessageSender();
        Player player = new Player(sessionId, name, "cosmos-" + playerId, sender);
        player.setPlayerID(playerId);
        connectionRegistry.registerConnection(player);
        return player;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastMessage(Player player) {
        Object sender = player.getSender();
        if (sender instanceof MockMessageSender mockSender) {
            Object message = mockSender.getLastMessage();
            if (message instanceof Map<?, ?> raw) {
                return (Map<String, Object>) raw;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object payloadValue(Map<String, Object> message, String key) {
        Object payload = message.get("payload");
        if (payload instanceof Map<?, ?> raw) {
            return ((Map<String, Object>) raw).get(key);
        }
        return null;
    }

    private int senderMessageCount(Player player) {
        Object sender = player.getSender();
        if (sender instanceof MockMessageSender mockSender) {
            return mockSender.getMessageCount();
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private WsMessageContext messageContext(UUID sessionId, String message) {
        WsMessageContext ctx = mock(WsMessageContext.class);
        when(ctx.attribute("session-uuid")).thenReturn(sessionId);
        when(ctx.message()).thenReturn(message);
        return ctx;
    }
}
