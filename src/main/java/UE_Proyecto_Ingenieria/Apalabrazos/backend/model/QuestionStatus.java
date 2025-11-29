package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Estado de una pregunta dentro del juego.
 * Valores  posibles:
 *   - responsed_ok
 *   - responsed_fail
 *   - init
 *   - passed
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

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static QuestionStatus fromValue(String v) {
        if (v == null)
            throw new IllegalArgumentException("QuestionStatus cannot be null");
        for (QuestionStatus s : values()) {
            if (s.value.equalsIgnoreCase(v) || s.name().equalsIgnoreCase(v))
                return s;
        }
        throw new IllegalArgumentException("Unknown QuestionStatus: " + v);
    }
}
