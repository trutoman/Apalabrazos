package Apalabrazos.backend.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evento periódico con el ranking parcial de la partida.
 * Contiene los 3 jugadores con mayor puntuación (o menos si hay menos jugadores).
 */
public class StandingsEvent extends GameEvent {

    public static class StandingEntry {
        private final String playerId;
        private final int score;

        public StandingEntry(String playerId, int score) {
            this.playerId = playerId;
            this.score = score;
        }

        public String getPlayerId() {
            return playerId;
        }

        public int getScore() {
            return score;
        }
    }

    private final String matchId;
    private final List<StandingEntry> topEntries;

    public StandingsEvent(String matchId, List<StandingEntry> topEntries) {
        super();
        this.matchId = matchId;
        this.topEntries = topEntries == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(topEntries));
    }

    public String getMatchId() {
        return matchId;
    }

    public List<StandingEntry> getTopEntries() {
        return topEntries;
    }
}
