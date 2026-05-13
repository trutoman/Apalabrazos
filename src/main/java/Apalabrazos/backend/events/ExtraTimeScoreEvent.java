package Apalabrazos.backend.events;

/**
 * Evento enviado cuando un jugador termina su rosco y recibe bonus por tiempo restante.
 */
public class ExtraTimeScoreEvent extends GameEvent {
    private final String matchId;
    private final String playerId;
    private final int remainingSeconds;
    private final int extraTimeScore;
    private final int totalScore;

    public ExtraTimeScoreEvent(String matchId, String playerId, int remainingSeconds, int extraTimeScore, int totalScore) {
        super();
        this.matchId = matchId;
        this.playerId = playerId;
        this.remainingSeconds = remainingSeconds;
        this.extraTimeScore = extraTimeScore;
        this.totalScore = totalScore;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public int getExtraTimeScore() {
        return extraTimeScore;
    }

    public int getTotalScore() {
        return totalScore;
    }
}
