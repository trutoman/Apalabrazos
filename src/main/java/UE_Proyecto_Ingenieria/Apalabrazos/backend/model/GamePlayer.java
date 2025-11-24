package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

/**
 * Represents the game instance for a single player.
 * Contains the player's timer, question list, and game result.
 */
public class GamePlayer {

    private long timeCounter;  // Tiempo en milisegundos
    private QuestionList questionList;
    private GameRecord gameResult;
    private Player player;

    /**
     * Default constructor
     */
    public GamePlayer() {
        this.timeCounter = 0;
        this.questionList = new QuestionList();
        this.gameResult = new GameRecord();
        this.player = new Player();
    }

    /**
     * Constructor with player name
     * @param playerName The name of the player
     */
    public GamePlayer(Player player) {
        this.timeCounter = 0;
        this.questionList = new QuestionList();
        this.gameResult = new GameRecord();
        this.player = player;
    }

    /**
     * Constructor with player name and question list
     * @param playerName The name of the player
     * @param questionList The question list for this player
     */
    public GamePlayer(Player player, QuestionList questionList) {
        this.timeCounter = 0;
        this.questionList = questionList;
        this.gameResult = new GameRecord();
        this.player = player;
    }


    /**
     * Get the time counter in milliseconds
     * @return The time counter
     */
    public long getTimeCounter() {
        return timeCounter;
    }

    /**
     * Set the time counter
     * @param timeCounter The time counter in milliseconds
     */
    public void setTimeCounter(long timeCounter) {
        this.timeCounter = timeCounter;
    }

    public void incrementTimeCounter(long increment) {
        this.timeCounter += increment;
    }

    /**
     * Reset the time counter to zero
     */
    public void resetTimeCounter() {
        this.timeCounter = 0;
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
     * Get the time counter formatted as mm:ss
     * @return The formatted time string
     */
    public String getFormattedTime() {
        long seconds = timeCounter / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
