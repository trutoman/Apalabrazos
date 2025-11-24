package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

/**
 * Represents the record of a game for a player.
 * This class contains game statistics and final results.
 */
public class GameRecord {

    private int correctAnswers;
    private int incorrectAnswers;
    private int passedQuestions;
    private int totalTime; // in seconds

    /**
     * Default constructor
     */
    public GameRecord() {
        this.correctAnswers = 0;
        this.incorrectAnswers = 0;
        this.passedQuestions = 0;
        this.totalTime = 0;
    }

    /**
     * Constructor with all parameters
     */
    public GameRecord(int correctAnswers, int incorrectAnswers, int passedQuestions, int totalTime) {
        this.correctAnswers = correctAnswers;
        this.incorrectAnswers = incorrectAnswers;
        this.passedQuestions = passedQuestions;
        this.totalTime = totalTime;
    }

    /**
     * Get the number of correct answers
     * @return The number of correct answers
     */
    public int getCorrectAnswers() {
        return correctAnswers;
    }

    /**
     * Set the number of correct answers
     * @param correctAnswers The number of correct answers
     */
    public void setCorrectAnswers(int correctAnswers) {
        this.correctAnswers = correctAnswers;
    }

    /**
     * Get the number of incorrect answers
     * @return The number of incorrect answers
     */
    public int getIncorrectAnswers() {
        return incorrectAnswers;
    }

    /**
     * Set the number of incorrect answers
     * @param incorrectAnswers The number of incorrect answers
     */
    public void setIncorrectAnswers(int incorrectAnswers) {
        this.incorrectAnswers = incorrectAnswers;
    }

    /**
     * Get the number of passed questions
     * @return The number of passed questions
     */
    public int getPassedQuestions() {
        return passedQuestions;
    }

    /**
     * Set the number of passed questions
     * @param passedQuestions The number of passed questions
     */
    public void setPassedQuestions(int passedQuestions) {
        this.passedQuestions = passedQuestions;
    }

    /**
     * Get the total time taken
     * @return The total time in seconds
     */
    public int getTotalTime() {
        return totalTime;
    }

    /**
     * Set the total time taken
     * @param totalTime The total time in seconds
     */
    public void setTotalTime(int totalTime) {
        this.totalTime = totalTime;
    }

    /**
     * Get the total number of answered questions
     * @return The sum of correct and incorrect answers
     */
    public int getTotalAnswered() {
        return correctAnswers + incorrectAnswers;
    }

    /**
     * Calculate the score percentage
     * @return The percentage of correct answers (0-100)
     */
    public double getScorePercentage() {
        int total = getTotalAnswered();
        if (total == 0) {
            return 0.0;
        }
        return (correctAnswers * 100.0) / total;
    }
}
