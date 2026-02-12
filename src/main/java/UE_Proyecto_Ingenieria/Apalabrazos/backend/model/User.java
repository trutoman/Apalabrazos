package UE_Proyecto_Ingenieria.Apalabrazos.backend.model;

import java.util.UUID;

public class User {
    public String id; // Required by Cosmos DB
    public String username;
    public String email;
    public String password; // TODO: Should be hashed in production

    public User() {
        // Default constructor for serialization
    }

    public User(String username, String email, String password) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.password = password;
    }
}
