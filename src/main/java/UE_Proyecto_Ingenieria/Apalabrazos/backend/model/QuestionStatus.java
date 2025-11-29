package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the status of a question within the game.
 */
public enum QuestionStatus {
    RESPONDED_OK("responsed_ok"),
    RESPONDED_FAIL("responsed_fail"),
    INIT("init"),
    PASSED("passed");

    private final String value;

    QuestionStatus(String value) {
        this.value = value;
    }

    /**
     * Get the string value representation
     * @return The status value
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Create a QuestionStatus from a string value
     * @param v The string value to parse
     * @return The corresponding QuestionStatus
     */
    @JsonCreator
    public static QuestionStatus fromValue(String v) {
        if (v == null) {
            throw new IllegalArgumentException("QuestionStatus cannot be null");
        }
        for (QuestionStatus s : values()) {
            if (s.value.equalsIgnoreCase(v) || s.name().equalsIgnoreCase(v)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown QuestionStatus: " + v);
    }
}
