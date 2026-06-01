package Apalabrazos.backend.config;

/**
 * Centralized score rules for game progression.
 */
public final class ScoresConfig {

    private ScoresConfig() {
        // Utility class.
    }

    /**
     * Points awarded for a correct answer when the question was never passed.
     */
    public static final int CORRECT_ANSWER_BASE_POINTS = 100;

    /**
     * Penalty applied for each previous pass of the same question.
     * This affects second and subsequent rounds after pressing "pasar".
     */
    public static final int PASS_PENALTY_PER_ROUND = 10;

    /**
     * Bonus points awarded per remaining second when a player finishes the rosco.
     */
    public static final int EXTRA_TIME_POINTS_PER_SECOND = 5;
}
