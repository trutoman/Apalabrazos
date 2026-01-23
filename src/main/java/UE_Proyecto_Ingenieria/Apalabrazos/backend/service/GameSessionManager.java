package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GameGlobal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for managing multiplayer game sessions.
 * Handles creation, deletion, and listing of active game sessions.
 */
public class GameSessionManager implements EventListener {

    private static final Logger log = LoggerFactory.getLogger(GameSessionManager.class);

    private final EventBus eventBus;
    private final Map<String, GameService> activeSessions;
    private final Map<String, String> sessionCreators; // roomId -> creatorUsername

    public GameSessionManager() {
        this.eventBus = GlobalEventBus.getInstance();
        this.activeSessions = new ConcurrentHashMap<>();
        this.sessionCreators = new ConcurrentHashMap<>();
        // Registrarse como listener de eventos
        eventBus.addListener(this);
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
     * Process game creation request from lobby
     */
    private void handleGameCreationRequested(GameCreationRequestedEvent event) {
        log.info("Game creation requested by {}", event.getConfig().getPlayer().getName());
        GameService gameService = new GameService(event.getConfig());
        String sessionId = addSession(gameService);
        String tempRoomCode = event.getTempRoomCode();

        // Guardar quién es el creador de esta sesión
        if (sessionId != null && event.getConfig().getPlayer() != null) {
            sessionCreators.put(sessionId, event.getConfig().getPlayer().getName());
        }

        // Publish event to notify lobby that session was created
        if (sessionId != null) {
            GameSessionCreatedEvent sessionCreatedEvent = new GameSessionCreatedEvent(tempRoomCode, sessionId, gameService);
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
        String creator = sessionCreators.get(roomId);
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
            // service.onEvent(event); // reenviar al GameService si debe manejar la creación de la instancia
        } else {
            log.error("Room with ID {} not found", roomId);
        }
    }

    /**
     * Add a new active game session to the registry
     * @param gameService The GameService instance to add
     * @return The session ID of the added service
     */
    public String addSession(GameService gameService) {
        if (gameService != null) {
            String sessionId = gameService.getGameSessionId();
            activeSessions.put(sessionId, gameService);
            log.info("Session added with ID: {}. Active sessions: {}", sessionId, activeSessions.size());
            return sessionId;
        }
        return null;
    }

    /**
     * Remove a game session from the active registry by GameService instance
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
     * @param sessionId The unique session ID
     */
    public void removeSessionById(String sessionId) {
        if (sessionId != null && activeSessions.remove(sessionId) != null) {
            log.info("Session removed with ID: {}. Active sessions: {}", sessionId, activeSessions.size());
        }
    }

    /**
     * Get all active game sessions
     * @return List of active GameService instances
     */
    public List<GameService> getActiveSessions() {
        return new ArrayList<>(activeSessions.values());
    }

    /**
     * Get the number of active sessions
     * @return Number of active game sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Get a specific session by its ID
     * @param sessionId The unique session ID
     * @return The GameService for this session, or null if not found
     */
    public GameService getSessionById(String sessionId) {
        return sessionId != null ? activeSessions.get(sessionId) : null;
    }

    /**
     * Check if a session exists
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
}
