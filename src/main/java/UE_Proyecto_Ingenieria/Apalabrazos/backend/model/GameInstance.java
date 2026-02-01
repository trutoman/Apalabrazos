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

    private QuestionList questionList;
    private GameRecord gameResult;
    private int currentQuestionIndex;  // Índice de la pregunta actual
    private GameState gameInstanceState;

    /**
     * Default constructor
     */
    public GameInstance() {
        this.questionList = new QuestionList();
        this.gameResult = new GameRecord();
        this.currentQuestionIndex = 0;
        // No añadir jugadores por defecto; se añaden vía PlayerJoinedEvent
        this.gameInstanceState = GameState.PENDING;
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
        this.questionList = new QuestionList();
        this.gameResult = new GameRecord();
        this.currentQuestionIndex = 0;
        this.gameInstanceState = GameState.PENDING;
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
    public void setNextCurrentQuestionIndex(int currentQuestionIndex) {
        this.currentQuestionIndex = currentQuestionIndex + 1;
    }

    /**
     * Obtener el estado de la partida
     * @return estado actual
     */
    public GameState getGameInstanceState() {
        return gameInstanceState;
    }

    /**
     * Establecer el estado de la partida
     * @param state nuevo estado
     */
    public void setGameInstanceState(GameState state) {
        this.gameInstanceState = state;
    }

    /**
     * Start the game
     */
    public void start() {
        this.gameInstanceState = GameState.PLAYING;
    }

    /**
     * Pause the game
     */
    public void pause() {
        if (this.gameInstanceState == GameState.PLAYING) {
            this.gameInstanceState = GameState.PAUSED;
        }
    }

    /**
     * Resume the game
     */
    public void resume() {
        if (this.gameInstanceState == GameState.PAUSED) {
            this.gameInstanceState = GameState.PLAYING;
        }
    }

    /**
     * Reset the game to ready state
     */
    public void reset() {
        this.gameInstanceState = GameState.PENDING;
    }

    /**
     * Calcular totales de respuestas correctas e incorrectas
     * @return Array de 2 elementos: [totalCorrect, totalIncorrect]
     */
    public int[] getCorrectIncorrectTotals() {
        int totalCorrect = 0;
        int totalIncorrect = 0;

        if (questionList != null) {
            for (int i = 0; i < questionList.getCurrentLength(); i++) {
                Question q = questionList.getQuestionAt(i);
                String userResponse = q.getUserResponseRecorded();
                if ("responsed_ok".equals(userResponse)) {
                    totalCorrect++;
                } else if ("responsed_fail".equals(userResponse)) {
                    totalIncorrect++;
                }
            }
        }

        return new int[]{totalCorrect, totalIncorrect};
    }

}
