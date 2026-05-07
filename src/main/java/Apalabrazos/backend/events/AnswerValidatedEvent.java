package Apalabrazos.backend.events;

import Apalabrazos.backend.model.QuestionStatus;

/**
 * Event fired when an answer is validated.
 */
public class AnswerValidatedEvent extends GameEvent {
    private final String playerId;
    private final int questionIndex;
    private final String questionLetter;
    private final String selectedAnswer;
    private final QuestionStatus status;
    private final String correctAnswer;
    private final int totalCorrect;
    private final int totalIncorrect;

    public AnswerValidatedEvent(String playerId, int questionIndex, String questionLetter,
                                String selectedAnswer, QuestionStatus status,
                                String correctAnswer, int totalCorrect, int totalIncorrect) {
        super();
        this.playerId = playerId;
        this.questionIndex = questionIndex;
        this.questionLetter = questionLetter;
        this.selectedAnswer = selectedAnswer;
        this.status = status;
        this.correctAnswer = correctAnswer;
        this.totalCorrect = totalCorrect;
        this.totalIncorrect = totalIncorrect;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public String getQuestionLetter() {
        return questionLetter;
    }

    public String getSelectedAnswer() {
        return selectedAnswer;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public int getTotalCorrect() {
        return totalCorrect;
    }

    public int getTotalIncorrect() {
        return totalIncorrect;
    }

    public boolean isCorrect() {
        return status == QuestionStatus.RESPONDED_OK;
    }
}
