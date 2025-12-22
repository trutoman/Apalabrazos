package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Represents a player of the game. Holds identity, image resource and game history.
 */
public class Player {

    private String name;
    private String imageResource; // Can be a local path or a URL
    private String playerID; // ID único: nombre-xxxx
    private final List<GameRecord> history = new ArrayList<>();

    /**
     * Default constructor
     */
    public Player() {
        this.name = "";
        this.imageResource = null;
        this.playerID = generatePlayerID("");
    }

    /**
     * Constructor with name
     * @param name Player name
     */
    public Player(String name) {
        this.name = name;
        this.imageResource = "resources/images/default-profile.png";
        this.playerID = generatePlayerID(name);
    }

    /**
     * Constructor with name and image resource
     * @param name Player name
     * @param imageResource Image path or URL
     */
    public Player(String name, String imageResource) {
        this.name = name;
        this.imageResource = imageResource;
        this.playerID = generatePlayerID(name);
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
