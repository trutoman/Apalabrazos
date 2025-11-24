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
        FIRST_WINS,  // El primer jugador que termina gana
        NUMBER_WINS  // Gana quien tenga más aciertos
    }

    private List<GamePlayer> players;
    private GameState state;
    private GameType gameType;

    /**
     * Default constructor
     */
    public GameGlobal() {
        this.players = new ArrayList<>();
        this.state = GameState.READY;
        this.gameType = GameType.FIRST_WINS;
    }

    /**
     * Constructor with game type
     * @param gameType The type of game
     */
    public GameGlobal(GameType gameType) {
        this.players = new ArrayList<>();
        this.state = GameState.READY;
        this.gameType = gameType;
    }

    /**
     * Add a player to the game
     * @param player The GamePlayer to add
     */
    public void addPlayer(GamePlayer player) {
        this.players.add(player);
    }

    /**
     * Remove a player from the game
     * @param player The GamePlayer to remove
     */
    public void removePlayer(GamePlayer player) {
        this.players.remove(player);
    }

    /**
     * Get all players
     * @return List of GamePlayer instances
     */
    public List<GamePlayer> getPlayers() {
        return players;
    }

    /**
     * Set the list of players
     * @param players List of GamePlayer instances
     */
    public void setPlayers(List<GamePlayer> players) {
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
}
