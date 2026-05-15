package Apalabrazos.backend.service;

import Apalabrazos.backend.model.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry responsible for managing active player connections.
 * This is the Level 2A - Connection Management:
 * - Maintains active connections (Map<SessionID, Player>)
 * - Handles player connection lifecycle (connect, disconnect)
 * - Provides connection lookup and broadcast utilities
 *
 * Singleton pattern to ensure only one instance manages all connections.
 */
public class ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);

    // Singleton instance
    private static volatile ConnectionRegistry instance;

    // ===== Connection Registry =====
    // Maps physical connections (sessionId) to Player objects
    private final Map<UUID, Player> activeConnections;

    /**
     * Private constructor to prevent direct instantiation
     */
    private ConnectionRegistry() {
        this.activeConnections = new ConcurrentHashMap<>();
        log.info("ConnectionRegistry singleton initialized");
    }

    /**
     * Get the singleton instance of ConnectionRegistry
     *
     * @return The singleton instance
     */
    public static ConnectionRegistry getInstance() {
        if (instance == null) {
            synchronized (ConnectionRegistry.class) {
                if (instance == null) {
                    instance = new ConnectionRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * Register a new player connection.
     * This is called when a physical connection (WebSocket) is established.
     *
     * @param player The Player object representing the connected user
     * @return true if registered successfully
     */
    public boolean registerConnection(Player player) {
        try {
            if (player == null || player.getSessionId() == null) {
                log.error("[REGISTER] ❌ Cannot register: Player is null or has no sessionId");
                return false;
            }

            log.info("[REGISTER] Registering player: {} with SessionID: {}",
                    player.getName(), player.getSessionId());

            activeConnections.put(player.getSessionId(), player);

            log.info("[REGISTER] Player registered successfully: {} (SessionID: {}). Active connections: {}",
                    player.getName(), player.getSessionId(), activeConnections.size());
            log.debug("[REGISTER] Player state: {}", player.getState());

            return true;
        } catch (Exception e) {
            log.error("[REGISTER] ❌ Error registering player: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Unregister a player connection.
     * Called when a connection is closed or times out.
     *
     * @param sessionId The session identifier
     * @return The removed Player, or null if not found
     */
    public Player unregisterConnection(UUID sessionId) {
        try {
            log.debug("[UNREGISTER] Looking up player with SessionID: {}", sessionId);

            Player player = activeConnections.remove(sessionId);

            if (player != null) {
                log.debug("[UNREGISTER] Disconnecting player: {}", player.getName());
                player.disconnect();
                log.info(
                        "[UNREGISTER] Player unregistered successfully: {} (SessionID: {}). Remaining connections: {}",
                        player.getName(), sessionId, activeConnections.size());
                log.debug("[UNREGISTER] Final player state: {}", player.getState());
            } else {
                log.warn("[UNREGISTER] Attempt to unregister SessionID not found: {}", sessionId);
            }

            return player;
        } catch (Exception e) {
            log.error("[UNREGISTER] ❌ Error unregistering SessionID {}: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get a player by their session ID
     *
     * @param sessionId The session identifier
     * @return The Player object, or null if not found
     */
    public Player getPlayerBySessionId(UUID sessionId) {
        try {
            Player player = activeConnections.get(sessionId);
            if (player == null) {
                log.warn("[GET-PLAYER] Player not found for SessionID: {}", sessionId);
            } else {
                log.debug("[GET-PLAYER] Player found: {} (SessionID: {})", player.getName(), sessionId);
            }
            return player;
        } catch (Exception e) {
            log.error("[GET-PLAYER] ❌ Error getting player for SessionID {}: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Find a connected player by their logical player ID.
     *
     * @param playerId The logical player ID (e.g. nombre-xxxx)
     * @return The Player object if found, otherwise null
     */
    public Player findConnectedPlayerByPlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return null;
        }

        for (Player connectedPlayer : activeConnections.values()) {
            if (connectedPlayer == null || connectedPlayer.getPlayerID() == null) {
                continue;
            }
            if (playerId.equals(connectedPlayer.getPlayerID())) {
                return connectedPlayer;
            }
        }
        return null;
    }

    /**
     * Resolve a player's display name from their logical player ID.
     *
     * @param playerId The logical player ID (e.g. nombre-xxxx)
     * @return The player's name if found in active connections, otherwise null
     */
    public String getPlayerNameByPlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return null;
        }

        for (Player player : activeConnections.values()) {
            if (player != null && playerId.equals(player.getPlayerID())) {
                return player.getName();
            }
        }

        log.debug("[GET-PLAYER-NAME] No active player found for playerId: {}", playerId);
        return null;
    }

    /**
     * Get all connected players
     *
     * @return List of all active players
     */
    public List<Player> getAllConnectedPlayers() {
        try {
            List<Player> players = new ArrayList<>(activeConnections.values());
            log.debug("[GET-ALL-PLAYERS] Retrieving list of {} connected players", players.size());
            return players;
        } catch (Exception e) {
            log.error("[GET-ALL-PLAYERS] ❌ Error retrieving player list: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Get the count of active connections
     *
     * @return Number of connected players
     */
    public int getActiveConnectionCount() {
        int count = activeConnections.size();
        log.debug("[CONNECTION-COUNT] Total active connections: {}", count);
        return count;
    }

    /**
     * Check if a session is active
     *
     * @param sessionId The session identifier
     * @return true if the session exists
     */
    public boolean isSessionActive(UUID sessionId) {
        return activeConnections.containsKey(sessionId);
    }

    /**
     * Broadcast a message to all connected players
     *
     * @param message The message to broadcast
     */
    public void broadcastToAll(Object message) {
        activeConnections.values().forEach(player -> player.sendMessage(message));
    }

    /**
     * Send a message to a specific player
     *
     * @param sessionId The session identifier
     * @param message   The message to send
     * @return true if message was sent
     */
    public boolean sendToPlayer(UUID sessionId, Object message) {
        Player player = activeConnections.get(sessionId);
        if (player != null && player.isConnected()) {
            player.sendMessage(message);
            return true;
        }
        return false;
    }

    /**
     * Clear all active connections (useful for testing or reset)
     */
    public void clearAllConnections() {
        activeConnections.clear();
        log.info("All connections cleared");
    }
}
