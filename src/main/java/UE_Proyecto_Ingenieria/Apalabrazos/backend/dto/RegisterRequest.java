package UE_Proyecto_Ingenieria.Apalabrazos.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class RegisterRequest {
    public String username;
    public String email;
    public String password;

    // Default constructor is needed for Jackson/Javalin serialization
    public RegisterRequest() {
    }

    public RegisterRequest(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        // Validate Username
        if (username == null || username.trim().isEmpty()) {
            errors.add("Username must not be empty");
        } else if (username.length() < 3) {
            errors.add("Username must be at least 3 characters long");
        }

        // Validate Email (Regex)
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        if (email == null || email.trim().isEmpty()) {
            errors.add("Email must not be empty");
        } else if (!email.matches(emailRegex)) {
            errors.add("Email format is invalid");
        }

        // Validate Password
        // Min 8 chars, at least one letter, at least one number
        String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";
        if (password == null || password.trim().isEmpty()) {
            errors.add("Password must not be empty");
        } else if (!password.matches(passwordRegex)) {
            errors.add("Password must be at least 8 characters long and contain at least one letter and one number");
        }

        return errors;
    }
}
