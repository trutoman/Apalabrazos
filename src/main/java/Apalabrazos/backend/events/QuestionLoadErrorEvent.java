package Apalabrazos.backend.events;

/**
 * Event fired when question loading fails or times out during game initialization.
 */
public class QuestionLoadErrorEvent extends GameEvent {
    private final String matchId;
    private final String errorMessage;
    private final String errorReason; // "TIMEOUT", "LOAD_FAILED", etc.

    public QuestionLoadErrorEvent(String matchId, String errorMessage, String errorReason) {
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
