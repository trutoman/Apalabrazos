package Apalabrazos.backend.events;

/**
 * Global bus notification emitted when asynchronous question preload fails.
 */
public class AIQuestionPreloadFailedEvent extends GameEvent {
    private final String matchId;
    private final String errorMessage;
    private final String errorReason;

    public AIQuestionPreloadFailedEvent(String matchId, String errorMessage, String errorReason) {
        super();
        this.matchId = matchId;
        this.errorMessage = errorMessage;
        this.errorReason = errorReason;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorReason() {
        return errorReason;
    }
}
