package Apalabrazos.backend.events;

import Apalabrazos.backend.model.QuestionList;

/**
 * Global bus notification emitted when asynchronous question preload succeeds.
 */
public class AIQuestionPreloadCompletedEvent extends GameEvent {
    private final String matchId;
    private final QuestionList questions;
    private final String source;

    public AIQuestionPreloadCompletedEvent(String matchId, QuestionList questions, String source) {
        super();
        this.matchId = matchId;
        this.questions = questions;
        this.source = source;
    }

    public String getMatchId() {
        return matchId;
    }

    public QuestionList getQuestions() {
        return questions;
    }

    public String getSource() {
        return source;
    }
}
