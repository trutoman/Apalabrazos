package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the game instance for a single player.
 * Contains the player's timer, question list, and game result.
 */
public class GameInstance {

    /**
     * Enum representing the game state
     */
    public enum GameState {
        PENDING,
        PLAYING,
        PAUSED,
        FINISHED
    }

    private int timeCounter;  // Tiempo en milisegundos
    private QuestionList questionList;
    private GameRecord gameResult;
    private int timer = 240 /*seconds */;
    private int currentQuestionIndex;  // Índice de la pregunta actual
    private QuestionLevel difficulty;  // Dificultad del juego
    private List<Player> players;  // Lista de jugadores que forman parte de la partida
    private GameState state; // Estado de la partida
    private GameType type;

    /**
     * Default constructor
     */
    public GameInstance() {
        this.timeCounter = this.timer;
        this.questionList = new QuestionList();
        this.gameResult = new GameRecord();
        this.currentQuestionIndex = 0;
        this.difficulty = QuestionLevel.EASY;
        this.players = new ArrayList<>();
        // No añadir jugadores por defecto; se añaden vía PlayerJoinedEvent
        this.state = GameState.PENDING;
        this.type = GameType.HIGHER_POINTS_WINS;
    }

    /**
     * @param timer in seconds
     * @param questionList The list of questions for the game
     * @param gameResult The result of the game
     * @param player The player participating in the game
     * @param difficulty The difficulty level of the game
     */
    public GameInstance(int timer,
                        QuestionList questionList,
                        GameRecord gameResult,
                        Player player,
                        QuestionLevel difficulty) {
        this.timeCounter = timer;
        this.questionList = questionList;
        this.gameResult = gameResult;
        this.currentQuestionIndex = 0;
        this.difficulty = difficulty != null ? difficulty : QuestionLevel.EASY;
        this.players = new ArrayList<>();
        this.players.add(player);
        this.state = GameState.PENDING;
    }

    /**
     * Convenience constructor to initialize a game instance directly from configuration.
     *
     * @param timerSeconds    initial timer in seconds
     * @param player          the player participating in the game
     * @param difficultyLevel difficulty level selected
     * @param questionNumber  number of questions to load (used externally by service)
     * @param gameType        game type/rule set
     */
    public GameInstance(int timerSeconds,
                        Player player,
                        QuestionLevel difficultyLevel,
                        int questionNumber,
                        GameType gameType) {
        this.timeCounter = timerSeconds;
        this.questionList = new QuestionList();
        this.gameResult = new GameRecord();
        this.currentQuestionIndex = 0;
        this.difficulty = difficultyLevel != null ? difficultyLevel : QuestionLevel.EASY;
        this.players = new ArrayList<>();
        this.players.add(player);
        this.state = GameState.PENDING;
        this.type = gameType != null ? gameType : GameType.HIGHER_POINTS_WINS;
        // questionNumber parameter is intentionally not stored; GameService loads questions using it
    }


    /**
     * Get the time counter in milliseconds
     * @return The time counter
     */
    public int getTimeCounter() {
        return timeCounter;
    }

    /**
     * Set the time counter
     * @param timeCounter The time counter in milliseconds
     */
    public void setTimeCounter(int timeCounter) {
        this.timeCounter = timeCounter;
    }

    public void incrementTimeCounter(int increment) {
        this.timeCounter += increment;
    }

    public void decrementTimeCounter(int decrement) {
        this.timeCounter -= decrement;
    }

    /**
     * Get the question list
     * @return The QuestionList instance
     */
    public QuestionList getQuestionList() {
        return questionList;
    }

    /**
     * Set the question list
     * @param questionList The new QuestionList
     */
    public void setQuestionList(QuestionList questionList) {
        this.questionList = questionList;
    }

    /**
     * Get the game result
     * @return The GameResult instance
     */
    public GameRecord getGameResult() {
        return gameResult;
    }

    /**
     * Set the game result
     * @param gameResult The new GameResult
     */
    public void setGameResult(GameRecord gameResult) {
        this.gameResult = gameResult;
    }

    /**
     * Get the current question index
     * @return The index of the current question
     */
    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    /**
     * Set the current question index
     * @param currentQuestionIndex The index of the question to set as current
     */
    public void setCurrentQuestionIndex(int currentQuestionIndex) {
        this.currentQuestionIndex = currentQuestionIndex;
    }

    /**
     * Get the difficulty level
     * @return The difficulty level
     */
    public QuestionLevel getDifficulty() {
        return difficulty;
    }

    /**
     * Set the difficulty level
     * @param difficulty The difficulty level
     */
    public void setDifficulty(QuestionLevel difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * Set the game type
     * @param gameType The difficulty level
     */
    public void setGameType(GameType gameType) {
        this.type = gameType;
    }

    /**
     * Obtener la lista de jugadores que forman parte de la partida
     * @return Lista inmutable de jugadores
     */
    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    /**
     * Agregar un jugador a la partida
     * @param player El jugador a agregar
     */
    public void addPlayer(Player player) {
        if (player != null && !players.contains(player)) {
            players.add(player);
        }
    }

    /**
     * Remover un jugador de la partida
     * @param player El jugador a remover
     */
    public void removePlayer(Player player) {
        players.remove(player);
    }

    /**
     * Obtener el número de jugadores en la partida
     * @return Cantidad de jugadores
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Limpiar la lista de jugadores
     */
    public void clearPlayers() {
        players.clear();
    }

    /**
     * Obtener el estado de la partida
     * @return estado actual
     */
    public GameState getState() {
        return state;
    }

    /**
     * Establecer el estado de la partida
     * @param state nuevo estado
     */
    public void setState(GameState state) {
        this.state = state;
    }

    /**
     * Start the game
     */
    public void start() {
        this.state = GameState.PLAYING;
    }

    /**
     * Pause the game
     */
    public void pause() {
        if (this.state == GameState.PLAYING) {
            this.state = GameState.PAUSED;
        }
    }

    /**
     * Resume the game
     */
    public void resume() {
        if (this.state == GameState.PAUSED) {
            this.state = GameState.PLAYING;
        }
    }

    /**
     * Reset the game to ready state
     */
    public void reset() {
        this.state = GameState.PENDING;
    }

}
