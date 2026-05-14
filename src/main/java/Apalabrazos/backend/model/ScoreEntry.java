package Apalabrazos.backend.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Documento de puntuacion persistente para Cosmos DB.
 *
 * Notas de diseno:
 * - Cada puntuacion tiene su propio id.
 * - scoreType representa la agrupacion TYPE-difficulty-time-questionNumber.
 * - Se recomienda usar /scoreType como partition key en el contenedor Scores.
 */
public class ScoreEntry {

    public String id;
    public String scoreType;
    public String userId;
    public String matchId;
    public String playerId;
    public int scoreValue;
    public String gameType;
    public String difficulty;
    public int timerSeconds;
    public int questionNumber;
    public String createdAt;

    // Metricas opcionales de resultado final por jugador
    public Integer correctAnswers;
    public Integer incorrectAnswers;
    public Integer passedQuestions;
    public Integer totalTime;

    public ScoreEntry() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public ScoreEntry(String userId,
                      String matchId,
                      String playerId,
                      int scoreValue,
                      String gameType,
                      String difficulty,
                      int timerSeconds,
                      int questionNumber) {
        this();
        this.userId = userId;
        this.matchId = matchId;
        this.playerId = playerId;
        this.scoreValue = Math.max(0, scoreValue);
        this.gameType = gameType;
        this.difficulty = difficulty;
        this.timerSeconds = Math.max(0, timerSeconds);
        this.questionNumber = Math.max(0, questionNumber);
        this.scoreType = buildScoreType(gameType, difficulty, this.timerSeconds, this.questionNumber);
    }

    public static String buildScoreType(String gameType,
                                        String difficulty,
                                        int timerSeconds,
                                        int questionNumber) {
        String safeType = sanitizePart(gameType, "UNKNOWN_TYPE");
        String safeDifficulty = sanitizePart(difficulty, "UNKNOWN_DIFFICULTY");
        return safeType + "-" + safeDifficulty + "-" + Math.max(0, timerSeconds) + "-" + Math.max(0, questionNumber);
    }

    private static String sanitizePart(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase();
    }
}