package UE_Proyecto_Ingenieria.Apalabrazos.backend.dto;

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
}
