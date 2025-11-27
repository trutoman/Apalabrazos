package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the highest level instance of a game session.
 * Contains a list of GamePlayer instances (one per player) and manages the game state.
 */
public class GameGlobal {

    /**
     * Enum representing the game state
     */
    public enum GameState {
        READY,
        RUN,
        PAUSED
    }

    /**
     * Enum representing the type of game
     */
    public enum GameType {
        HIGHER_POINTS_WINS,  // El primer jugador que termina gana
        NUMBER_WINS  // Gana quien tenga más aciertos
    }

    private List<GameSingleInstance> players;
    private GameState state;
    private GameType gameType;
    private int currentPlayerIndex;  // Índice del jugador actual (0 o 1)

    /**
     * Default constructor
     */
    public GameGlobal() {
        this.players = new ArrayList<>();
        this.state = GameState.READY;
        this.gameType = GameType.HIGHER_POINTS_WINS;
        this.currentPlayerIndex = 0;
    }

    /**
     * Constructor with all parameters
     * @param players List of GameSingleInstance instances
     * @param state The initial game state
     * @param gameType The game type
     */
    public GameGlobal(List<GameSingleInstance> players, GameState state, GameType gameType) {
        this.players = players != null ? players : new ArrayList<>();
        this.state = state != null ? state : GameState.READY;
        this.gameType = gameType != null ? gameType : GameType.HIGHER_POINTS_WINS;
    }

    /**
     * Add a player to the game
     * @param player The GamePlayer to add
     */
    public void addPlayer(GameSingleInstance player) {
        this.players.add(player);
    }

    /**
     * Remove a player from the game
     * @param player The GameSingleInstance to remove
     */
    public void removePlayer(GameSingleInstance player) {
        this.players.remove(player);
    }

    /**
     * Get all players
     * @return List of GameSingleInstance instances
     */
    public List<GameSingleInstance> getPlayers() {
        return players;
    }

    /**
     * Set the list of players
     * @param players List of GameSingleInstance instances
     */
    public void setPlayers(List<GameSingleInstance> players) {
        this.players = players;
    }

    /**
     * Get the current game state
     * @return The current GameState
     */
    public GameState getState() {
        return state;
    }

    /**
     * Set the game state
     * @param state The new GameState
     */
    public void setState(GameState state) {
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
     * Start the game
     */
    public void start() {
        this.state = GameState.RUN;
    }

    /**
     * Pause the game
     */
    public void pause() {
        if (this.state == GameState.RUN) {
            this.state = GameState.PAUSED;
        }
    }

    /**
     * Resume the game
     */
    public void resume() {
        if (this.state == GameState.PAUSED) {
            this.state = GameState.RUN;
        }
    }

    /**
     * Reset the game to ready state
     */
    public void reset() {
        this.state = GameState.READY;
    }

    /**
     * Get the number of players
     * @return The number of players in the game
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Get the current player index
     * @return The index of the current player
     */
    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    /**
     * Set the current player index
     * @param currentPlayerIndex The index of the current player
     */
    public void setCurrentPlayerIndex(int currentPlayerIndex) {
        this.currentPlayerIndex = currentPlayerIndex;
    }

    /**
     * Get player 1 (convenience method)
     * @return The first player or null if not available
     */
    public GameSingleInstance getGamePlayer1() {
        return players.size() > 0 ? players.get(0) : null;
    }

    /**
     * Set player 1 (convenience method)
     * @param player The player to set as player 1
     */
    public void setGamePlayer1(GameSingleInstance player) {
        if (players.isEmpty()) {
            players.add(player);
        } else {
            players.set(0, player);
        }
    }
    /**
     * Get the current player
     * @return The current GameSingleInstance
     */
    public GameSingleInstance getCurrentPlayer() {
        if (currentPlayerIndex >= 0 && currentPlayerIndex < players.size()) {
            return players.get(currentPlayerIndex);
        }
        return null;
    }
}
