package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

/**
 * Represents the game instance for a single player.
 * Contains the player's timer, question list, and game result.
 */
public class GameSingleInstance {

    private int timeCounter;  // Tiempo en milisegundos
    private QuestionList questionList;
    private GameRecord gameResult;
    private Player player;
    private int timer = 240 /*seconds */;
    private int currentQuestionIndex;  // Índice de la pregunta actual
    private QuestionLevel difficulty;  // Dificultad del juego

    /**
     * Default constructor
     */
    public GameSingleInstance() {
        this.timeCounter = this.timer;
        this.questionList = new QuestionList();
        this.gameResult = new GameRecord();
        this.player = new Player();
        this.currentQuestionIndex = 0;
        this.difficulty = QuestionLevel.EASY;
    }

    /**
     *
     * @param timer in seconds
     * @param questionList The list of questions for the game
     * @param gameResult The result of the game
     * @param player The player participating in the game
     * @param difficulty The difficulty level of the game
     */
    public GameSingleInstance(int timer,
            QuestionList questionList,
            GameRecord gameResult,
            Player player,
            QuestionLevel difficulty) {
        this.timeCounter = timer;
        this.questionList = questionList;
        this.gameResult = gameResult;
        this.player = player;
        this.currentQuestionIndex = 0;
        this.difficulty = difficulty != null ? difficulty : QuestionLevel.EASY;
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
     * Get the player name
     * @return The player name
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Set the player name
     * @param playerName The new player name
     */
    public void setPlayer(Player player) {
        this.player = player;
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
}
