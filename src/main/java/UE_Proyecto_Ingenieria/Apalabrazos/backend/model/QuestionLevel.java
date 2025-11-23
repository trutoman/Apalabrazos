package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Nivel de dificultad de la pregunta.
 * Valores: TRIVIAL, EASY, DIFFICULT
 */
public enum QuestionLevel {
    TRIVIAL("trivial"),
    EASY("easy"),
    DIFFICULT("difficult");

    private final String value;

    QuestionLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static QuestionLevel fromValue(String value) {
        if (value == null)
            throw new IllegalArgumentException("QuestionLevel cannot be null");
        for (QuestionLevel l : values()) {
            if (l.value.equalsIgnoreCase(value) || l.name().equalsIgnoreCase(value))
                return l;
        }
        throw new IllegalArgumentException("Unknown QuestionLevel: " + value);
    }
}
