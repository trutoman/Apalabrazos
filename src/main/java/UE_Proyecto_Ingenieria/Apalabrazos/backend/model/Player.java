package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.MessageSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Represents a player in the system - the "anchor" of the architecture.
 * This class lives for the entire user session, from connection to disconnection.
 * It bridges the physical world (network) with the logical world (game rules).
 *
 * Combines three facets:
 * - Identity: Who they are (ID, Username, Avatar)
 * - State: Where they are (LOBBY, PLAYING, DISCONNECTED, etc.)
 * - Channel: How we communicate with them (MessageSender abstraction)
 */
public class Player {

    // ===== Identity =====
    private final UUID sessionId;           // Unique session identifier
    private String name;
    private String imageResource;           // Can be a local path or a URL
    private String playerID;                // Human-readable ID: nombre-xxxx

    // ===== State =====
    private PlayerState state;              // Current logical state
    private UUID currentMatchId;            // null if not in a match

    // ===== Channel (Network Abstraction) =====
    private final MessageSender sender;     // Communication channel

    // ===== Game Data =====
    private final List<GameRecord> history = new ArrayList<>();

    /**
     * Default constructor (for backward compatibility)
     * Creates a player without network capabilities (for testing/legacy code)
     */
    public Player() {
        this.sessionId = UUID.randomUUID();
        this.name = "";
        this.imageResource = null;
        this.playerID = generatePlayerID("");
        this.state = PlayerState.LOBBY;
        this.sender = null;
        this.currentMatchId = null;
    }

    /**
     * Constructor with name (for backward compatibility)
     * @param name Player name
     */
    public Player(String name) {
        this.sessionId = UUID.randomUUID();
        this.name = name;
        this.imageResource = "resources/images/default-profile.png";
        this.playerID = generatePlayerID(name);
        this.state = PlayerState.LOBBY;
        this.sender = null;
        this.currentMatchId = null;
    }

    /**
     * Constructor with name and image resource (for backward compatibility)
     * @param name Player name
     * @param imageResource Image path or URL
     */
    public Player(String name, String imageResource) {
        this.sessionId = UUID.randomUUID();
        this.name = name;
        this.imageResource = imageResource;
        this.playerID = generatePlayerID(name);
        this.state = PlayerState.LOBBY;
        this.sender = null;
        this.currentMatchId = null;
    }

    /**
     * Full constructor for connected player (PRIMARY CONSTRUCTOR FOR SERVER)
     * @param sessionId Unique session identifier
     * @param name Player name
     * @param sender Communication channel to the client
     */
    public Player(UUID sessionId, String name, MessageSender sender) {
        this.sessionId = sessionId;
        this.name = name;
        this.sender = sender;
        this.imageResource = "resources/images/default-profile.png";
        this.playerID = generatePlayerID(name);
        this.state = PlayerState.LOBBY;
        this.currentMatchId = null;
    }

    /**
     * Get player name
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Set player name
     * @param name new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get image resource (local path or URL)
     * @return image resource string
     */
    public String getImageResource() {
        return imageResource;
    }

    /**
     * Set image resource
     * @param imageResource path or URL
     */
    public void setImageResource(String imageResource) {
        this.imageResource = imageResource;
    }

    /**
     * Get immutable game history
     * @return list of GameResult
     */
    public List<GameRecord> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /**
     * Add a game result to history
     * @param result game result to add
     */
    public void addGameResult(GameRecord result) {
        if (result != null) {
            history.add(result);
        }
    }

    /**
     * Clear game history
     */
    public void clearHistory() {
        history.clear();
    }

    /**
     * Obtener el ID único del jugador (nombre-xxxx)
     * @return ID único del jugador
     */
    public String getPlayerID() {
        return playerID;
    }

    /**
     * Establecer el ID único del jugador (usado cuando se necesita mantener consistencia)
     * @param playerID ID del jugador
     */

    // ===== Session Management Methods =====

    /**
     * Get the unique session identifier
     * @return Session UUID
     */
    public UUID getSessionId() {
        return sessionId;
    }

    /**
     * Get the current player state
     * @return Current PlayerState
     */
    public PlayerState getState() {
        return state;
    }

    /**
     * Set the player state
     * @param state New state
     */
    public void setState(PlayerState state) {
        this.state = state;
    }

    /**
     * Get the current match ID (if in a match)
     * @return Match UUID or null
     */
    public UUID getCurrentMatchId() {
        return currentMatchId;
    }

    /**
     * Set the current match ID
     * @param currentMatchId Match UUID
     */
    public void setCurrentMatchId(UUID currentMatchId) {
        this.currentMatchId = currentMatchId;
    }

    /**
     * Check if player is currently in a match
     * @return true if in a match
     */
    public boolean isInMatch() {
        return currentMatchId != null && state == PlayerState.PLAYING;
    }

    /**
     * Send a message to the client through the communication channel.
     * This is the key method for the game logic to communicate with the client.
     * Only sends if the player is not disconnected and has a valid sender.
     * @param message The message to send
     */
    public void sendMessage(Object message) {
        if (sender != null && state != PlayerState.DISCONNECTED) {
            sender.send(message);
        }
    }

    /**
     * Check if the player has an active connection
     * @return true if connected
     */
    public boolean isConnected() {
        return sender != null && sender.isConnected() && state != PlayerState.DISCONNECTED;
    }

    /**
     * Mark player as disconnected
     */
    public void disconnect() {
        this.state = PlayerState.DISCONNECTED;
        if (sender != null) {
            sender.close();
        }
    }

    /**
     * Get the message sender (for advanced use cases)
     * @return The MessageSender instance
     */
    public MessageSender getSender() {
        return sender;
    }

    public void setPlayerID(String playerID) {
        this.playerID = playerID;
    }

    /**
     * Generar un ID único para el jugador: nombre-xxxx (4 caracteres alfanuméricos al azar)
     * @param playerName nombre del jugador
     * @return ID único en formato nombre-xxxx
     */
    private String generatePlayerID(String playerName) {
        String safeName = (playerName != null && !playerName.isEmpty()) ? playerName : "Player";
        String randomPart = generateRandomString(4);
        return safeName + "-" + randomPart;
    }

    /**
     * Generar una cadena aleatoria de caracteres alfanuméricos
     * @param length longitud de la cadena
     * @return cadena aleatoria
     */
    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder result = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }
        return result.toString();
    }
}
