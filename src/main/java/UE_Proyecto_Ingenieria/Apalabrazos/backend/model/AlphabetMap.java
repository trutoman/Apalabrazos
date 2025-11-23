package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import java.util.Map;

/**
 * Mapa inmutable que asocia índices (0..26) a las letras del abecedario
 * en español sin las letras "ch" ni "ll". Incluye la "ñ"
 *
 * Esta clase está pensada solo para consulta (static helpers).
 */
public final class AlphabetMap {
    private AlphabetMap() { }

    public static final Map<Integer, String> MAP = Map.ofEntries(
            Map.entry(0, "a"),
            Map.entry(1, "b"),
            Map.entry(2, "c"),
            Map.entry(3, "d"),
            Map.entry(4, "e"),
            Map.entry(5, "f"),
            Map.entry(6, "g"),
            Map.entry(7, "h"),
            Map.entry(8, "i"),
            Map.entry(9, "j"),
            Map.entry(10, "k"),
            Map.entry(11, "l"),
            Map.entry(12, "m"),
            Map.entry(13, "n"),
            Map.entry(14, "ñ"),
            Map.entry(15, "o"),
            Map.entry(16, "p"),
            Map.entry(17, "q"),
            Map.entry(18, "r"),
            Map.entry(19, "s"),
            Map.entry(20, "t"),
            Map.entry(21, "u"),
            Map.entry(22, "v"),
            Map.entry(23, "w"),
            Map.entry(24, "x"),
            Map.entry(25, "y"),
            Map.entry(26, "z")
    );

    /**
     * Devuelve el mapa inmutable (0..26 -> letra).
     */
    public static Map<Integer, String> getMap() {
        return MAP;
    }

    /**
     * Devuelve la letra asociada al índice (0..26).
     * Lanza IndexOutOfBoundsException si el índice está fuera de rango.
     */
    public static String getLetter(int index) {
        String s = MAP.get(index);
        if (s == null)
            throw new IndexOutOfBoundsException("Index must be between 0 and 26: " + index);
        return s;
    }
}
