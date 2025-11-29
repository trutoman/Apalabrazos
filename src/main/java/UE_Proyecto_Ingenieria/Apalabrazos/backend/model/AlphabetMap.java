package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import java.util.Map;

/**
 * Immutable map that associates indices (0..26) with letters of the Spanish alphabet.
 * Excludes "ch" and "ll", includes "ñ".
 * This class is designed for static lookup only.
 */
public final class AlphabetMap {

    private AlphabetMap() {
    }

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
     * Get the immutable map of indices to letters
     * @return The map (0..26 -> letter)
     */
    public static Map<Integer, String> getMap() {
        return MAP;
    }

    /**
     * Get the letter associated with the given index
     * @param index The index (0..26)
     * @return The corresponding letter
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public static String getLetter(int index) {
        String s = MAP.get(index);
        if (s == null) {
            throw new IndexOutOfBoundsException("Index must be between 0 and 26: " + index);
        }
        return s;
    }
}
