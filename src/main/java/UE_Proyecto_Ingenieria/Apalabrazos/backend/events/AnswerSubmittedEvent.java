package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Event fired when a player submits an answer.
 */
public class AnswerSubmittedEvent extends GameEvent {
    private final String playerId;
    private final int questionIndex;
    private final int selectedOption;

    public AnswerSubmittedEvent(String playerId, int questionIndex, int selectedOption) {
        super();
        this.playerId = playerId;
        this.questionIndex = questionIndex;
        this.selectedOption = selectedOption;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public int getSelectedOption() {
        return selectedOption;
    }

}
