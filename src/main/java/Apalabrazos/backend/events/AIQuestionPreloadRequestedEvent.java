package Apalabrazos.backend.events;

/**
 * Global bus command to request asynchronous question preload for a match.
 */
public class AIQuestionPreloadRequestedEvent extends GameEvent {
    private final String matchId;
    private final int numberOfQuestions;
    private final boolean allowFallback;

    public AIQuestionPreloadRequestedEvent(String matchId, int numberOfQuestions, boolean allowFallback) {
        super();
        this.matchId = matchId;
        this.numberOfQuestions = numberOfQuestions;
        this.allowFallback = allowFallback;
    }

    public String getMatchId() {
        return matchId;
    }

    public int getNumberOfQuestions() {
        return numberOfQuestions;
    }

    public boolean isAllowFallback() {
        return allowFallback;
    }
}
