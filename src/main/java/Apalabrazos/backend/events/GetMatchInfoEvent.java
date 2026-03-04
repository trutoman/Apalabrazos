package Apalabrazos.backend.events;

/**
 * Event published when information about a game session is requested.
 * Typically triggered when a user selects a game in the lobby.
 */
public class GetMatchInfoEvent extends GameEvent {

    private final String matchId;

    public GetMatchInfoEvent(String matchId) {
        this.matchId = matchId;
    }

    public String getMatchId() {
        return matchId;
    }
}

