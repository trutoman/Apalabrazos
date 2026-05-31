package Apalabrazos.backend.service;

import Apalabrazos.backend.events.GameCreationRequestedEvent;
import Apalabrazos.backend.events.GameStartedRequestEvent;
import Apalabrazos.backend.events.PlayerJoinedEvent;
import Apalabrazos.backend.model.GamePlayerConfig;
import Apalabrazos.backend.model.GameType;
import Apalabrazos.backend.model.Player;
import Apalabrazos.backend.model.QuestionLevel;
import Apalabrazos.backend.network.MessageSender;
import Apalabrazos.backend.network.WsMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchManagerTest {

    private MatchManager manager;
    private ConnectionRegistry connectionRegistry;

    @BeforeEach
    void setUp() {
        manager = MatchManager.getInstance();
        manager.clearAllMatches();

        connectionRegistry = ConnectionRegistry.getInstance();
        connectionRegistry.clearAllConnections();
    }

    @Test
    void gameCreationRequestWithInvalidNameIsRejectedAndNoMatchIsCreated() {
        TestMessageSender sender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", sender);

        GamePlayerConfig config = validConfig(creator, 2, 60);
        GameCreationRequestedEvent event = new GameCreationRequestedEvent(config, "ab");

        manager.onEvent(event);

        assertEquals(0, manager.getActiveMatchCount());
        Map<String, Object> msg = sender.firstMessageOfType(WsMessageType.GAME_CREATION_REQUEST_INVALID);
        assertNotNull(msg);
        assertTrue(String.valueOf(payloadValue(msg, "cause")).contains("nombre"));
    }

    @Test
    void gameCreationRequestWithValidConfigCreatesMatchAndJoinsCreator() {
        TestMessageSender sender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", sender);

        GamePlayerConfig config = validConfig(creator, 2, 60);
        GameCreationRequestedEvent event = new GameCreationRequestedEvent(config, "Sala_Prueba_01");

        manager.onEvent(event);

        assertEquals(1, manager.getActiveMatchCount());
        GameService created = manager.getActiveMatches().get(0);
        assertEquals("creator-id", created.getCreatorPlayerId());
        assertTrue(created.getGameInstance().hasPlayer("creator-id"));

        Map<String, Object> msg = sender.firstMessageOfType(WsMessageType.GAME_CREATION_REQUEST_VALID);
        assertNotNull(msg);
        assertEquals(created.getMatchId(), payloadValue(msg, "roomId"));
    }

    @Test
    void gameCreationRequestIsRejectedWhenCreatorIsAlreadyInAnotherMatch() {
        TestMessageSender sender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", sender);

        GameService existing = createMatchWithCreator("owner", "owner-id", "ExistingRoom");
        manager.addMatch(existing);
        assertTrue(manager.joinPlayerToMatch(creator, existing.getMatchId()));

        GameCreationRequestedEvent event = new GameCreationRequestedEvent(validConfig(creator, 2, 60), "AnotherRoom");

        manager.onEvent(event);

        assertEquals(1, manager.getActiveMatchCount());
        Map<String, Object> msg = sender.firstMessageOfType(WsMessageType.GAME_CREATION_REQUEST_INVALID);
        assertNotNull(msg);
        assertTrue(String.valueOf(payloadValue(msg, "cause")).contains("salir"));
    }

    @Test
    void gameCreationRequestIsRejectedWhenMaxPlayersIsOutOfRange() {
        TestMessageSender sender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", sender);

        GamePlayerConfig config = validConfig(creator, 1, 60);
        manager.onEvent(new GameCreationRequestedEvent(config, "RoomTooSmall"));

        assertEquals(0, manager.getActiveMatchCount());
        Map<String, Object> msg = sender.firstMessageOfType(WsMessageType.GAME_CREATION_REQUEST_INVALID);
        assertNotNull(msg);
        assertTrue(String.valueOf(payloadValue(msg, "cause")).contains("jugadores"));
    }

    @Test
    void gameCreationRequestIsRejectedWhenTimerIsInvalid() {
        TestMessageSender sender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", sender);

        GamePlayerConfig config = validConfig(creator, 2, 45);
        manager.onEvent(new GameCreationRequestedEvent(config, "RoomTimer"));

        assertEquals(0, manager.getActiveMatchCount());
        Map<String, Object> msg = sender.firstMessageOfType(WsMessageType.GAME_CREATION_REQUEST_INVALID);
        assertNotNull(msg);
        assertTrue(String.valueOf(payloadValue(msg, "cause")).contains("Tiempo"));
    }

    @Test
    void gameCreationRequestIsRejectedWhenDifficultyIsNull() {
        TestMessageSender sender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", sender);

        GamePlayerConfig config = validConfig(creator, 2, 60);
        config.setDifficultyLevel(null);

        manager.onEvent(new GameCreationRequestedEvent(config, "RoomDifficulty"));

        assertEquals(0, manager.getActiveMatchCount());
        Map<String, Object> msg = sender.firstMessageOfType(WsMessageType.GAME_CREATION_REQUEST_INVALID);
        assertNotNull(msg);
        assertTrue(String.valueOf(payloadValue(msg, "cause")).contains("Dificultad"));
    }

    @Test
    void gameCreationRequestIsRejectedWhenGameTypeIsNull() {
        TestMessageSender sender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", sender);

        GamePlayerConfig config = validConfig(creator, 2, 60);
        config.setGameType(null);

        manager.onEvent(new GameCreationRequestedEvent(config, "RoomGameType"));

        assertEquals(0, manager.getActiveMatchCount());
        Map<String, Object> msg = sender.firstMessageOfType(WsMessageType.GAME_CREATION_REQUEST_INVALID);
        assertNotNull(msg);
        assertTrue(String.valueOf(payloadValue(msg, "cause")).contains("Tipo de juego"));
    }

    @Test
    void joinPlayerToMatchSucceedsWhenMatchExistsAndPlayerIsFree() {
        GameService service = createMatchWithCreator("owner", "owner-id", "RoomOne");
        String matchId = manager.addMatch(service);

        Player joiner = connectedPlayer("joiner", "joiner-id", new TestMessageSender());

        boolean joined = manager.joinPlayerToMatch(joiner, matchId);

        assertTrue(joined);
        assertTrue(service.getGameInstance().hasPlayer("joiner-id"));
    }

    @Test
    void joinPlayerToMatchReturnsTrueWhenPlayerAlreadyJoinedSameMatch() {
        GameService service = createMatchWithCreator("owner", "owner-id", "RoomOne");
        String matchId = manager.addMatch(service);

        Player joiner = connectedPlayer("joiner", "joiner-id", new TestMessageSender());

        assertTrue(manager.joinPlayerToMatch(joiner, matchId));
        assertTrue(manager.joinPlayerToMatch(joiner, matchId));
        assertTrue(service.getGameInstance().hasPlayer("joiner-id"));
    }

    @Test
    void joinPlayerToMatchFailsWhenPlayerAlreadyJoinedInAnotherMatch() {
        GameService first = createMatchWithCreator("owner1", "owner1-id", "RoomFirst");
        GameService second = createMatchWithCreator("owner2", "owner2-id", "RoomSecond");
        String firstId = manager.addMatch(first);
        String secondId = manager.addMatch(second);

        Player joiner = connectedPlayer("joiner", "joiner-id", new TestMessageSender());

        assertTrue(manager.joinPlayerToMatch(joiner, firstId));
        assertFalse(manager.joinPlayerToMatch(joiner, secondId));
        assertTrue(first.getGameInstance().hasPlayer("joiner-id"));
        assertFalse(second.getGameInstance().hasPlayer("joiner-id"));
    }

    @Test
    void startMatchRequestFailsWhenRequesterIsNotCreator() {
        TestMessageSender creatorSender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", creatorSender);

        TestMessageSender nonCreatorSender = new TestMessageSender();
        Player nonCreator = connectedPlayer("guest", "guest-id", nonCreatorSender);

        GameService service = new GameService(validConfig(creator, 2, 60));
        service.setCreatorPlayerId("creator-id");
        service.setGameName("RoomPerms");
        service.addPlayerToGame("creator-id", "creator");
        manager.addMatch(service);

        manager.onEvent(new GameStartedRequestEvent(service.getMatchId(), nonCreator.getPlayerID()));

        Map<String, Object> msg = nonCreatorSender.firstMessageOfType(WsMessageType.START_MATCH_REQUEST_INVALID);
        assertNotNull(msg);
        assertTrue(String.valueOf(payloadValue(msg, "cause")).contains("creador"));
    }

    @Test
    void startMatchRequestFailsWhenRoomDoesNotExist() {
        TestMessageSender sender = new TestMessageSender();
        Player requester = connectedPlayer("req", "req-id", sender);

        manager.onEvent(new GameStartedRequestEvent("missing-room", requester.getPlayerID()));

        Map<String, Object> msg = sender.firstMessageOfType(WsMessageType.START_MATCH_REQUEST_INVALID);
        assertNotNull(msg);
        assertTrue(String.valueOf(payloadValue(msg, "cause")).contains("creador"));
    }

    @Test
    void startMatchRequestByCreatorBroadcastsMatchStartedToPlayersInMatch() {
        TestMessageSender creatorSender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", creatorSender);

        GameService service = new GameService(validConfig(creator, 2, 60));
        service.setCreatorPlayerId("creator-id");
        service.setGameName("RoomStart");
        service.addPlayerToGame("creator-id", "creator");
        manager.addMatch(service);

        manager.onEvent(new GameStartedRequestEvent(service.getMatchId(), creator.getPlayerID()));

        Map<String, Object> started = creatorSender.firstMessageOfType(WsMessageType.MATCH_STARTED);
        assertNotNull(started);
        assertEquals(service.getMatchId(), payloadValue(started, "roomId"));

        Map<String, Object> invalid = creatorSender.firstMessageOfType(WsMessageType.START_MATCH_REQUEST_INVALID);
        assertNull(invalid);
    }

    @Test
    void creatorLeavingRemovesMatchAndNotifiesOtherPlayers() {
        TestMessageSender creatorSender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", creatorSender);

        TestMessageSender joinerSender = new TestMessageSender();
        Player joiner = connectedPlayer("joiner", "joiner-id", joinerSender);

        GameService service = new GameService(validConfig(creator, 3, 60));
        service.setCreatorPlayerId("creator-id");
        service.setGameName("RoomLeaveCreator");
        service.addPlayerToGame("creator-id", "creator");
        service.addPlayerToGame("joiner-id", "joiner");
        String matchId = manager.addMatch(service);

        String leftMatch = manager.leavePlayerFromCurrentMatch(creator);

        assertEquals(matchId, leftMatch);
        assertEquals(0, manager.getActiveMatchCount());
        Map<String, Object> closed = joinerSender.firstMessageOfType(WsMessageType.MATCH_CLOSED_BY_CREATOR);
        assertNotNull(closed);
        assertEquals(matchId, payloadValue(closed, "roomId"));
    }

    @Test
    void normalPlayerLeavingKeepsMatchAliveAndUpdatesSummary() {
        TestMessageSender creatorSender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", creatorSender);

        TestMessageSender joinerSender = new TestMessageSender();
        Player joiner = connectedPlayer("joiner", "joiner-id", joinerSender);

        GameService service = new GameService(validConfig(creator, 3, 60));
        service.setCreatorPlayerId("creator-id");
        service.setGameName("RoomLeavePlayer");
        service.addPlayerToGame("creator-id", "creator");
        service.addPlayerToGame("joiner-id", "joiner");
        String matchId = manager.addMatch(service);

        String leftMatch = manager.leavePlayerFromCurrentMatch(joiner);

        assertEquals(matchId, leftMatch);
        assertEquals(1, manager.getActiveMatchCount());
        assertTrue(service.getGameInstance().hasPlayer("creator-id"));
        assertFalse(service.getGameInstance().hasPlayer("joiner-id"));
        assertTrue(manager.getMatchPlayerNames(matchId).contains("creator"));
        assertFalse(manager.getMatchPlayerNames(matchId).contains("joiner"));
    }

    @Test
    void finishedMatchCleanupRemovesMatchAndClearsRegistry() throws Exception {
        TestMessageSender creatorSender = new TestMessageSender();
        Player creator = connectedPlayer("creator", "creator-id", creatorSender);

        GameService service = new GameService(validConfig(creator, 3, 60));
        service.setCreatorPlayerId("creator-id");
        service.setGameName("RoomCleanup");
        service.addPlayerToGame("creator-id", "creator");
        String matchId = manager.addMatch(service);

        invokeCleanupFinishedMatch(manager, matchId, service);

        assertEquals(0, manager.getActiveMatchCount());
        assertNull(manager.getMatchById(matchId));
        assertTrue(manager.getMatchPlayerNames(matchId).isEmpty());
    }

    private Player connectedPlayer(String name, String playerId, TestMessageSender sender) {
        Player player = new Player(UUID.randomUUID(), name, "cosmos-" + playerId, sender);
        player.setPlayerID(playerId);
        connectionRegistry.registerConnection(player);
        return player;
    }

    private GameService createMatchWithCreator(String name, String creatorId, String roomName) {
        Player creator = connectedPlayer(name, creatorId, new TestMessageSender());
        GameService service = new GameService(validConfig(creator, 3, 60));
        service.setCreatorPlayerId(creatorId);
        service.setGameName(roomName);
        service.addPlayerToGame(creatorId, name);
        return service;
    }

    private GamePlayerConfig validConfig(Player player, int maxPlayers, int timerSeconds) {
        GamePlayerConfig config = new GamePlayerConfig(player, timerSeconds, QuestionLevel.MEDIUM, maxPlayers, 27);
        config.setGameType(GameType.HIGHER_POINTS_WINS);
        return config;
    }

    private Object payloadValue(Map<String, Object> msg, String key) {
        Object payloadObj = msg.get("payload");
        if (!(payloadObj instanceof Map<?, ?> payload)) {
            return null;
        }
        return payload.get(key);
    }

    private static void invokeCleanupFinishedMatch(MatchManager manager, String matchId, GameService service)
            throws Exception {
        Method method = MatchManager.class.getDeclaredMethod("cleanupFinishedMatch", String.class, GameService.class);
        method.setAccessible(true);
        method.invoke(manager, matchId, service);
    }

    private static final class TestMessageSender implements MessageSender {
        private final List<Object> messages = new ArrayList<>();
        private boolean connected = true;

        @Override
        public void send(Object message) {
            messages.add(message);
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void close() {
            connected = false;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> firstMessageOfType(String type) {
            for (Object message : messages) {
                if (message instanceof Map<?, ?> raw) {
                    Object msgType = raw.get("type");
                    if (type.equals(msgType)) {
                        return (Map<String, Object>) raw;
                    }
                }
            }
            return null;
        }
    }
}
