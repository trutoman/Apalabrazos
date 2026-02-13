package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import java.util.UUID;

public class User {
    public String id; // Required by Cosmos DB
    public String username;
    public String email;
    public String password; // Hashed password
    public String salt; // Salt used for hashing

    public User() {
        // Default constructor for serialization
    }

    public User(String username, String email, String password) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public User(String username, String email, String hashedPassword, String salt) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.password = hashedPassword;
        this.salt = salt;
    }
}
