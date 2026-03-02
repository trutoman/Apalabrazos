package Apalabrazos.backend.service;

import Apalabrazos.backend.events.*;
import Apalabrazos.backend.model.GameGlobal;
import Apalabrazos.backend.model.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for managing multiplayer game sessions.
 * Handles creation, deletion, and listing of active game sessions.
 * Singleton pattern to ensure only one instance manages all sessions.
 *
 * This is the Level 2 - Session Manager:
 * - Maintains active connections (Map<SessionID, Player>)
 * - Routes events to appropriate game sessions
 * - Handles player lifecycle (connect, disconnect, reconnect)
 */
public class GameSessionManager implements EventListener {

    private static final Logger log = LoggerFactory.getLogger(GameSessionManager.class);

    // Singleton instance
    private static volatile GameSessionManager instance;

    private final AsyncEventBus eventBus;

    // ===== Connection Registry (Level 1 → Level 2 Bridge) =====
    // Maps physical connections (sessionId) to Player objects
    private final Map<UUID, Player> activeConnections;

    // ===== Game Session Registry =====
    /**
     * Registry de sesiones de juego activas.
     * Mapea sessionId (UUID único de la partida) a su correspondiente GameService.
     * Permite acceso rápido a la lógica de juego de cualquier sesión activa.
     * El creador y nombre de cada partida se obtienen directamente desde GameService.
     * Key: sessionId (identificador único de la sesión)
     * Value: GameService (instancia de la lógica de juego)
     */
    private final Map<String, GameService> activeSessions;

    /**
     * Private constructor to prevent direct instantiation
     */
    private GameSessionManager() {
        this.eventBus = GlobalAsyncEventBus.getInstance();
        this.activeConnections = new ConcurrentHashMap<>();
        this.activeSessions = new ConcurrentHashMap<>();
        // Registrarse como listener de eventos
        eventBus.addListener(this);
        log.info("GameSessionManager singleton initialized");
    }

    /**
     * Get the singleton instance of GameSessionManager
     *
     * @return The singleton instance
     */
    public static GameSessionManager getInstance() {
        if (instance == null) {
            synchronized (GameSessionManager.class) {
                if (instance == null) {
                    instance = new GameSessionManager();
                }
            }
        }
        return instance;
    }

    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof GameCreationRequestedEvent) {
            handleGameCreationRequested((GameCreationRequestedEvent) event);
        } else if (event instanceof GetGameSessionInfoEvent) {
            handleGetGameSessionInfo((GetGameSessionInfoEvent) event);
        } else if (event instanceof PlayerJoinedEvent) {
            handlePlayerJoined((PlayerJoinedEvent) event);
        } else if (event instanceof GameStartedRequestEvent) {
            handleGameStartedRequest((GameStartedRequestEvent) event);
        }
    }

    /**
     * Valida el nombre de la partida (igual que frontend).
     */
    private boolean isValidGameName(String name) {
        if (name == null)
            return false;
        String trimmed = name.trim();
        if (trimmed.length() < 3 || trimmed.length() > 20)
            return false;
        if (!trimmed.matches("^[a-zA-Z0-9 _-]+$"))
            return false;
        if (trimmed.contains("  "))
            return false;
        return true;
    }

    /**
     * Comprueba si ya existe una partida con este nombre.
     * Recorre todas las sesiones activas y busca un nombre coincidente (case-insensitive).
     *
     * @param gameName El nombre de la partida a validar
     * @return true si el nombre ya está en uso, false en caso contrario
     */
    private boolean isGameNameTaken(String gameName) {
        if (gameName == null)
            return false;

        String normalizedName = gameName.trim().toLowerCase();
        return activeSessions.values().stream()
                .anyMatch(service -> {
                    String serviceName = service.getGameName();
                    return serviceName != null && serviceName.toLowerCase().equals(normalizedName);
                });
    }

    /**
     * Obtiene el ID del jugador creador de una sesión de juego.
     * Recorre todas las sesiones activas para encontrar el creador.
     *
     * @param sessionId El ID de la sesión de juego
     * @return El ID del creador, o null si la sesión no existe o no tiene creador asignado
     */
    private String getSessionCreatorId(String sessionId) {
        if (sessionId == null)
            return null;

        GameService service = activeSessions.get(sessionId);
        if (service != null) {
            return service.getCreatorPlayerId();
        }
        return null;
    }

    /**
     * Valida la configuración de la partida y retorna el error (null si es válido).
     */
    private String validateGameConfig(Apalabrazos.backend.model.GamePlayerConfig config, String roomName) {
        if (config == null)
            return "Config is null";

        // 1. Validar nombre de la partida (que llega como tempRoomCode normalmente)
        if (!isValidGameName(roomName)) {
            log.warn("Invalid game name: {}", roomName);
            return "El nombre debe tener de 3 a 20 caracteres alfanuméricos sin espacios dobles.";
        }

        // 1.5. Validar que no existe ya una partida con este nombre
        if (isGameNameTaken(roomName)) {
            log.warn("Game name already exists: {}", roomName);
            return "El nombre de la partida ya está en uso. Por favor, elige otro nombre.";
        }

        // 2. Validar número de jugadores (2 a 8)
        if (config.getMaxPlayers() < 2 || config.getMaxPlayers() > 8) {
            log.warn("Invalid max players: {}", config.getMaxPlayers());
            return "El número de jugadores debe estar entre 2 y 8.";
        }

        // 3. Validar tiempo (30, 60, 120, 180, 300, 420, 600 segundos)
        int[] validTimes = { 30, 60, 120, 180, 300, 420, 600 };
        boolean validTime = false;
        for (int t : validTimes) {
            if (config.getTimerSeconds() == t) {
                validTime = true;
                break;
            }
        }
        if (!validTime) {
            log.warn("Invalid timer seconds: {}", config.getTimerSeconds());
            return "Tiempo no válido.";
        }

        // 4. Validar dificultad
        if (config.getDifficultyLevel() == null) {
            log.warn("Difficulty level is null");
            return "Dificultad no válida.";
        }

        // 5. Validar tipo de juego
        if (config.getGameType() == null) {
            log.warn("Game type is null");
            return "Tipo de juego no válido.";
        }

        return null; // OK
    }

    /**
     * Process game creation request from lobby
     */
    private void handleGameCreationRequested(GameCreationRequestedEvent event) {
        String tempRoomCode = event.getTempRoomCode();
        Player player = event.getConfig() != null ? event.getConfig().getPlayer() : null;

        // Validar primero antes de instanciar y asignar recursos
        String validationError = validateGameConfig(event.getConfig(), tempRoomCode);
        if (validationError != null) {
            String requester = (player != null) ? player.getName() : "unknown";
            log.warn("Game creation rejected due to invalid config from {}: {}", requester, validationError);

            // En caso de error se enviara el mensaje de type GameCreationRequestInvalid con
            // la causa
            if (player != null) {
                player.sendMessage(java.util.Map.of(
                        "type", "GameCreationRequestInvalid",
                        "payload", java.util.Map.of("cause", validationError)));
            }
            return;
        }

        log.info("Game creation requested by {}", player.getName());
        GameService gameService = new GameService(event.getConfig());
        // Asignar creador y nombre de partida antes de agregar a registro
        gameService.setCreatorPlayerId(player.getPlayerID());
        gameService.setGameName(tempRoomCode);
        String sessionId = addSession(gameService);

        // Guardar información de la sesión
        if (sessionId != null) {

            // En caso de partida creada ok se enviara un mensaje de vuelta type
            // GameCreationRequestValid
            player.sendMessage(java.util.Map.of(
                    "type", "GameCreationRequestValid",
                    "payload", java.util.Map.of("roomId", sessionId)));
        }

        // Publish event to notify lobby that session was created
        if (sessionId != null) {
            GameSessionCreatedEvent sessionCreatedEvent = new GameSessionCreatedEvent(tempRoomCode, sessionId,
                    gameService);
            eventBus.publish(sessionCreatedEvent);
        }
    }

    /**
     * Send session information back to listeners when requested from the lobby
     */
    private void handleGetGameSessionInfo(GetGameSessionInfoEvent event) {
        String roomCode = event.getRoomCode();
        GameService service = getSessionById(roomCode);
        if (service != null) {
            // Reuse GameSessionCreatedEvent to deliver the GameService reference
            eventBus.publish(new GameSessionCreatedEvent(roomCode, roomCode, service));
        }
    }

    /**
     * Handle game start request - validates that the requester is the creator
     */
    private void handleGameStartedRequest(GameStartedRequestEvent event) {
        String roomId = event.getRoomId();
        String username = event.getUsername();

        log.info("Game start requested by {} for room {}", username, roomId);

        // Validar que el usuario sea el creador de la partida
        String creator = getSessionCreatorId(roomId);
        if (creator == null) {
            log.error("No se encontró el creador para la sala {}", roomId);
            return;
        }

        if (!creator.equals(username)) {
            log.error("Solo el creador puede iniciar la partida. Creador: {}, Usuario: {}", creator, username);
            return;
        }

        // Obtener el GameService y validar inicio
        GameService service = getSessionById(roomId);
        if (service != null) {
            service.GameStartedValid();
            log.info("Validación exitosa. Juego iniciado por {} en sala {}", username, roomId);
        } else {
            log.error("Room with ID {} not found", roomId);
        }
    }

    /**
     * Forward player join requests to the correct game session
     */
    private void handlePlayerJoined(PlayerJoinedEvent event) {
        String playerId = event.getPlayerID();
        String roomId = event.getRoomCode();

        if (playerId == null || roomId == null) {
            log.error("playerId o roomId es null (playerId={}, roomId={})", playerId, roomId);
            return;
        }
        // En multijugador, cada GameInstance manejará sus propios jugadores
        GameService service = getSessionById(roomId);
        if (service != null) {
            GameGlobal gameInstance = service.getGameInstance();
            boolean alreadyInRoom = gameInstance != null && gameInstance.hasPlayer(playerId);

            if (alreadyInRoom) {
                log.info("Player {} already in room {}", playerId, roomId);
                return;
            }

            log.info("Player {} joined room {}", playerId, roomId);
            // Agregar jugador a la partida
            boolean added = service.addPlayerToGame(playerId);
            if (!added) {
                log.error("No se pudo agregar el jugador {} a la sala {}", playerId, roomId);
            }
            // Aquí podríamos añadir la instancia del jugador si existe lógica para ello
            // service.onEvent(event); // reenviar al GameService si debe manejar la
            // creación de la instancia
        } else {
            log.error("Room with ID {} not found", roomId);
        }
    }

    /**
     * Add a new active game session to the registry
     *
     * @param gameService The GameService instance to add
     * @return The session ID of the added service
     */
    public String addSession(GameService gameService) {
        if (gameService != null) {
            String sessionId = gameService.getGameSessionId();
            activeSessions.put(sessionId, gameService);
            log.info("Session added with ID: {} (name: {}). Active sessions: {}", sessionId, gameService.getGameName(), activeSessions.size());
            return sessionId;
        }
        return null;
    }

    /**
     * Remove a game session from the active registry by GameService instance
     *
     * @param gameService The GameService instance to remove
     */
    public void removeSession(GameService gameService) {
        if (gameService != null) {
            String sessionId = gameService.getGameSessionId();
            if (activeSessions.remove(sessionId) != null) {
                log.info("Session removed with ID: {}. Active sessions: {}", sessionId, activeSessions.size());
            }
        }
    }

    /**
     * Remove a game session by its session ID
     *
     * @param sessionId The unique session ID
     */
    public void removeSessionById(String sessionId) {
        if (sessionId != null && activeSessions.remove(sessionId) != null) {
            log.info("Session removed with ID: {}. Active sessions: {}", sessionId, activeSessions.size());
        }
    }

    /**
     * Get all active game sessions
     *
     * @return List of active GameService instances
     */
    public List<GameService> getActiveSessions() {
        return new ArrayList<>(activeSessions.values());
    }

    /**
     * Get the number of active sessions
     *
     * @return Number of active game sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Get a specific session by its ID
     *
     * @param sessionId The unique session ID
     * @return The GameService for this session, or null if not found
     */
    public GameService getSessionById(String sessionId) {
        return sessionId != null ? activeSessions.get(sessionId) : null;
    }

    /**
     * Check if a session exists
     *
     * @param gameService The GameService to check
     * @return true if the session is active
     */
    public boolean isSessionActive(GameService gameService) {
        return gameService != null && activeSessions.containsKey(gameService.getGameSessionId());
    }

    /**
     * Clear all active sessions
     */
    public void clearAllSessions() {
        activeSessions.clear();
        log.info("All sessions cleared");
    }

    // ===== Connection Management (Level 1 Bridge) =====

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
                log.error("[REGISTER] ❌ No se puede registrar: Player null o sin sessionId");
                return false;
            }

            log.info("[REGISTER] 🔐 Registrando jugador: {} con SessionID: {}",
                    player.getName(), player.getSessionId());

            activeConnections.put(player.getSessionId(), player);

            log.info("[REGISTER] ✅ Jugador registrado exitosamente: {} (SessionID: {}). Conexiones activas: {}",
                    player.getName(), player.getSessionId(), activeConnections.size());
            log.debug("[REGISTER] Estado del jugador: {}", player.getState());

            return true;
        } catch (Exception e) {
            log.error("[REGISTER] ❌ Error registrando jugador: {}", e.getMessage(), e);
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
            log.debug("[UNREGISTER] 🔍 Buscando jugador con SessionID: {}", sessionId);

            Player player = activeConnections.remove(sessionId);

            if (player != null) {
                log.debug("[UNREGISTER] 📤 Desconectando jugador: {}", player.getName());
                player.disconnect();
                log.info(
                        "[UNREGISTER] ✅ Jugador desregistrado exitosamente: {} (SessionID: {}). Conexiones restantes: {}",
                        player.getName(), sessionId, activeConnections.size());
                log.debug("[UNREGISTER] Estado final del jugador: {}", player.getState());
            } else {
                log.warn("[UNREGISTER] ⚠️ Intento de desregistrar SessionID no encontrada: {}", sessionId);
            }

            return player;
        } catch (Exception e) {
            log.error("[UNREGISTER] ❌ Error desregistrando SessionID {}: {}", sessionId, e.getMessage(), e);
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
                log.warn("[GET-PLAYER] ⚠️ Jugador no encontrado para SessionID: {}", sessionId);
            } else {
                log.debug("[GET-PLAYER] ✓ Jugador encontrado: {} (SessionID: {})", player.getName(), sessionId);
            }
            return player;
        } catch (Exception e) {
            log.error("[GET-PLAYER] ❌ Error obteniendo jugador para SessionID {}: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get all connected players
     *
     * @return List of all active players
     */
    public List<Player> getAllConnectedPlayers() {
        try {
            List<Player> players = new ArrayList<>(activeConnections.values());
            log.debug("[GET-ALL-PLAYERS] 📊 Obteniendo lista de {} jugadores conectados", players.size());
            return players;
        } catch (Exception e) {
            log.error("[GET-ALL-PLAYERS] ❌ Error obteniendo lista de jugadores: {}", e.getMessage(), e);
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
        log.debug("[CONNECTION-COUNT] 📊 Total de conexiones activas: {}", count);
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
}
