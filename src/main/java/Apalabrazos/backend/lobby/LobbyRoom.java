package Apalabrazos.backend.lobby;

import Apalabrazos.backend.model.Player;
import Apalabrazos.backend.service.MatchesManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton representing the global Lobby Room.
 * All connected players are automatically part of the lobby.
 * Has a fixed ID: "lobby-main".
 */
public class LobbyRoom {

    public static final String LOBBY_ROOM_ID = "lobby-main";

    private static final Logger log = LoggerFactory.getLogger(LobbyRoom.class);
    private static volatile LobbyRoom instance;

    /** Session IDs currently in this lobby */
    private final Set<UUID> sessions = ConcurrentHashMap.newKeySet();

    private final ObjectMapper mapper = new ObjectMapper();

    private LobbyRoom() {
    }

    public static LobbyRoom getInstance() {
        if (instance == null) {
            synchronized (LobbyRoom.class) {
                if (instance == null) {
                    instance = new LobbyRoom();
                    log.info("[LOBBY] LobbyRoom singleton created (id={})", LOBBY_ROOM_ID);
                }
            }
        }
        return instance;
    }

    /** Register a player session in the lobby. */
    public void join(UUID sessionId) {
        sessions.add(sessionId);
        log.info("[LOBBY] Player {} joined lobby '{}'. Total in lobby: {}", sessionId, LOBBY_ROOM_ID, sessions.size());
    }

    /** Remove a player session from the lobby. */
    public void leave(UUID sessionId) {
        sessions.remove(sessionId);
        log.info("[LOBBY] Player {} left lobby '{}'. Total in lobby: {}", sessionId, LOBBY_ROOM_ID, sessions.size());
    }

    /**
     * Broadcast a chat message to every player currently in the lobby.
     *
     * @param usernameOriginator The username of the player who sent the message.
     * @param text               The message text.
     * @param sessionManager     The GameSessionManager used to resolve sessions →
     *                           Players.
     */
    public void broadcastChat(String usernameOriginator, String text, MatchesManager sessionManager) {
        try {
            // Build: { "type": "chat_message", "payload": { "text": "...",
            // "username_originator": "..." } }
            ObjectNode payload = mapper.createObjectNode();
            payload.put("text", text);
            payload.put("username_originator", usernameOriginator);

            ObjectNode message = mapper.createObjectNode();
            message.put("type", "chat_message");
            message.set("payload", payload);

            String json = mapper.writeValueAsString(message);
            log.info("[LOBBY-CHAT] Broadcasting from '{}': {} → {} recipients", usernameOriginator, text,
                    sessions.size());

            for (UUID sessionId : sessions) {
                Player player = sessionManager.getPlayerBySessionId(sessionId);
                if (player != null && player.isConnected()) {
                    player.sendMessage(json);
                } else {
                    log.warn("[LOBBY-CHAT] Session {} not found or disconnected, skipping", sessionId);
                }
            }
        } catch (Exception e) {
            log.error("[LOBBY-CHAT] Error broadcasting chat message: {}", e.getMessage(), e);
        }
    }

    public String getId() {
        return LOBBY_ROOM_ID;
    }

    public int getPlayerCount() {
        return sessions.size();
    }
}
