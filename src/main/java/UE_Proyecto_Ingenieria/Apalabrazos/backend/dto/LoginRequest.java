package UE_Proyecto_Ingenieria.Apalabrazos.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for login requests.
 */
public class LoginRequest {
    public String email;
    public String pass;

    public LoginRequest() {
    }

    public LoginRequest(String email, String pass) {
        this.email = email;
        this.pass = pass;
    }

    /**
     * Validates the login request.
     *
     * @return List of validation errors (empty if valid)
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (email == null || email.isBlank()) {
            errors.add("Email is required");
        } else if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            errors.add("Invalid email format");
        }

        if (pass == null || pass.isBlank()) {
            errors.add("Password is required");
        }

        return errors;
    }
}
