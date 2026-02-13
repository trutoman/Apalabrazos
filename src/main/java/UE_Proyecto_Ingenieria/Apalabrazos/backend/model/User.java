package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class User {
    public String id; // Required by Cosmos DB
    public String userId; // Partition key in Cosmos DB (/userId)
    public String username;
    public String email;
    public String password; // Hashed password
    public String salt; // Salt used for hashing
    public boolean isOnline;
    public String lastSeen;
    public String connectionId;
    public Preferences preferences;
    public Scores scores;

    public static class Preferences {
        public String theme;
        public boolean notifications;

        public Preferences() {
            this.theme = "dark";
            this.notifications = true;
        }
    }

    public static class Scores {
        public String max_score;

        public Scores() {
            this.max_score = "";
        }
    }

    public User() {
        // Default constructor for serialization
        applyDefaultState();
    }

    public User(String username, String email, String password) {
        applyDefaultState();
        assignNewUserIds();
        this.username = normalizeUsername(username);
        this.email = normalizeEmail(email);
        this.password = password;
    }

    public User(String username, String email, String hashedPassword, String salt) {
        applyDefaultState();
        assignNewUserIds();
        this.username = normalizeUsername(username);
        this.email = normalizeEmail(email);
        this.password = hashedPassword;
        this.salt = salt;
    }

    private void assignNewUserIds() {
        String generatedId = UUID.randomUUID().toString();
        this.id = generatedId;
        this.userId = generatedId;
    }

    private static String normalizeUsername(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private static String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private void applyDefaultState() {
        this.isOnline = false;
        this.lastSeen = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        this.connectionId = "";
        this.preferences = new Preferences();
        this.scores = new Scores();
    }
}
