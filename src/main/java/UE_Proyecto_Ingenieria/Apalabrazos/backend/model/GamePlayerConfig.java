package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

public class GamePlayerConfig {

    private Player player;
    private int timerSeconds;
    private QuestionLevel difficultyLevel;
    private int questionNumber;

    public GamePlayerConfig() {
        this.player = new Player("Jugador1", "default_avatar.png");
        this.timerSeconds = 240;
        this.difficultyLevel = QuestionLevel.EASY;
        this.questionNumber = 27;
    }

    public GamePlayerConfig(Player player, int timerSeconds, QuestionLevel difficultyLevel, int questionNumber) {
        this.player = player;
        this.timerSeconds = timerSeconds;
        this.difficultyLevel = difficultyLevel;
        this.questionNumber = questionNumber;
    }
    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public void setTimerSeconds(int timerSeconds) {
        this.timerSeconds = timerSeconds;
    }

    public QuestionLevel getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(QuestionLevel difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(int questionNumber) {
        this.questionNumber = questionNumber;
    }

    public int getTimerSeconds() {
        return timerSeconds;
    }
}