package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the highest level instance of a game session.
 * Contains a map of player instances (playerID -> GameInstance) and manages the game state.
 */
public class GameGlobal {

    private static final Logger log = LoggerFactory.getLogger(GameGlobal.class);

    /**
     * Enum representing the game state
     */
    public enum GameGlobalState {
        IDLE,
        CONTROLLER_READY,
        START_VALIDATED,
        INITIALIZED,           // Ambas condiciones (Controller Ready + Start Validated) se cumplieron
        PLAYING,
        PAUSED,
        POST
    }

    private Map<String, GameInstance> playerInstances;  // playerID -> GameInstance
    private GameGlobalState state;
    private GameType gameType; // Tipo de juego (del modelo)
    private QuestionLevel difficulty;
    private int maxPlayers;
    private int numberOfQuestions;
    private int gameDuration; // Duration in seconds

    /**
     * Default constructor
     */
    public GameGlobal() {
        this.playerInstances = new HashMap<>();
        this.state = GameGlobalState.IDLE;
        this.gameType = GameType.HIGHER_POINTS_WINS;
        this.difficulty = QuestionLevel.EASY;
        this.maxPlayers = 1;
        this.numberOfQuestions = 10;
        this.gameDuration = 300; // 5 minutes default
    }

    /**
     * Constructor from GamePlayerConfig
     * @param config The player configuration containing game settings
     */
    public GameGlobal(GamePlayerConfig config) {
        this.playerInstances = new HashMap<>();
        this.state = GameGlobalState.IDLE;
        this.gameType = config.getGameType() != null ? config.getGameType() : GameType.HIGHER_POINTS_WINS;
        this.difficulty = config.getDifficultyLevel() != null ? config.getDifficultyLevel() : QuestionLevel.EASY;
        this.maxPlayers = config.getMaxPlayers() > 0 ? config.getMaxPlayers() : 1;
        this.numberOfQuestions = config.getQuestionNumber() > 0 ? config.getQuestionNumber() : 10;
        this.gameDuration = config.getTimerSeconds() > 0 ? config.getTimerSeconds() : 300; // 5 minutes default
    }

    /**
     * Add a player instance to the game
     * @param playerId The unique player ID
     * @param instance The GameInstance for this player
     */
    public void addPlayerInstance(String playerId, GameInstance instance) {
        if (playerId != null && instance != null) {
            this.playerInstances.put(playerId, instance);
        }
    }

    /**
     * Remove a player from the game
     * @param playerId The unique player ID to remove
     */
    public void removePlayer(String playerId) {
        this.playerInstances.remove(playerId);
    }

    /**
     * Get a specific player's game instance
     * @param playerId The player ID
     * @return The GameInstance for this player, or null if not found
     */
    public GameInstance getPlayerInstance(String playerId) {
        return this.playerInstances.get(playerId);
    }

    /**
     * Get all player instances
     * @return Collection of all GameInstance objects
     */
    public Collection<GameInstance> getAllPlayerInstances() {
        return playerInstances.values();
    }

    /**
     * Get the map of player instances
     * @return Map of playerID -> GameInstance
     */
    public Map<String, GameInstance> getPlayerInstancesMap() {
        return playerInstances;
    }

    /**
     * Set the player instances map
     * @param playerInstances Map of playerID -> GameInstance
     */
    public void setPlayerInstances(Map<String, GameInstance> playerInstances) {
        this.playerInstances = playerInstances != null ? playerInstances : new HashMap<>();
    }

    /**
     * Get the current game state
     * @return The current GameState
     */
    public GameGlobalState getState() {
        return state;
    }

    /**
     * Set the game state
     * @param state The new GameState
     */
    public void setState(GameGlobalState state) {
        this.state = state;
    }

    /**
     * Get the game type
     * @return The GameType
     */
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Set the game type
     * @param gameType The new GameType
     */
    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    /**
     * Get the difficulty level
     * @return The current QuestionLevel
     */
    public QuestionLevel getDifficulty() {
        return difficulty;
    }

    /**
     * Set the difficulty level
     * @param difficulty The new QuestionLevel
     */
    public void setDifficulty(QuestionLevel difficulty) {
        this.difficulty = difficulty != null ? difficulty : QuestionLevel.EASY;
    }

    /**
     * Start the game
     */
    public void start() {
        this.state = GameGlobalState.PLAYING;
    }

    /**
     * Pause the game
     */
    public void pause() {
        if (this.state == GameGlobalState.PLAYING) {
            this.state = GameGlobalState.PAUSED;
        }
    }

    /**
     * Resume the game
     */
    public void resume() {
        if (this.state == GameGlobalState.PAUSED) {
            this.state = GameGlobalState.PLAYING;
        }
    }

    /**
     * Reset the game to ready state
     */
    public void reset() {
        this.state = GameGlobalState.IDLE;
    }

    /**
     * Get the number of players
     * @return The number of players in the game
     */
    public int getPlayerCount() {
        return playerInstances.size();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    /**
     * Get the number of questions
     * @return The number of questions in the game
     */
    public int getNumberOfQuestions() {
        return this.numberOfQuestions;
    }

    /**
     * Set the number of questions
     * @param numberOfQuestions The number of questions
     */
    public void setNumberOfQuestions(int numberOfQuestions) {
        this.numberOfQuestions = numberOfQuestions;
    }

    /**
     * Get the game duration
     * @return The game duration in seconds
     */
    public int getGameDuration() {
        return this.gameDuration;
    }

    /**
     * Set the game duration
     * @param gameDuration The game duration in seconds
     */
    public void setGameDuration(int gameDuration) {
        this.gameDuration = gameDuration;
    }

    /**
     * Check if a player is in the game
     * @param playerId The player ID to check
     * @return true if the player is in the game
     */
    public boolean hasPlayer(String playerId) {
        return playerInstances.containsKey(playerId);
    }

    /**
     * Transiciona a CONTROLLER_READY si es posible
     * Retorna true si transición exitosa y se alcanzó INITIALIZED
     * @return true si se alcanzó INITIALIZED, false en otro caso
     */
    public synchronized boolean transitionControllerReady() {
        if (this.state == GameGlobalState.IDLE) {
            this.state = GameGlobalState.CONTROLLER_READY;
            log.info("State transitioned: IDLE -> CONTROLLER_READY");
            return false;
        } else if (this.state == GameGlobalState.START_VALIDATED) {
            this.state = GameGlobalState.INITIALIZED;
            log.info("State transitioned: START_VALIDATED -> INITIALIZED (ok)");
            return true;
        }
        return false;
    }

    /**
     * Transiciona a START_VALIDATED si es posible
     * Retorna true si transición exitosa y se alcanzó INITIALIZED
     * @return true si se alcanzó INITIALIZED, false en otro caso
     */
    public synchronized boolean transitionStartValidated() {
        if (this.state == GameGlobalState.IDLE) {
            this.state = GameGlobalState.START_VALIDATED;
            log.info("State transitioned: IDLE -> START_VALIDATED");
            return false;
        } else if (this.state == GameGlobalState.CONTROLLER_READY) {
            this.state = GameGlobalState.INITIALIZED;
            log.info("State transitioned: CONTROLLER_READY -> INITIALIZED (ok)");
            return true;
        }
        return false;
    }

    /**
     * Verifica si el juego ha sido inicializado (ambas condiciones se cumplen)
     * @return true si el estado es INITIALIZED
     */
    public boolean isGameInitialized() {
        return this.state == GameGlobalState.INITIALIZED;
    }
}
