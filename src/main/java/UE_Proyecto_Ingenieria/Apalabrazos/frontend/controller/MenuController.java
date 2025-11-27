package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

/**
 * Controller for the main menu with game mode selection.
 */
public class MenuController {

    @FXML
    private ImageView profileImage;

    @FXML
    private Label usernameLabel;

    @FXML
    private Button singlePlayerButton;

    @FXML
    private Button multiplayerButton;

    @FXML
    private Button scoresButton;

    @FXML
    private Button exitButton;

    private ViewNavigator navigator;
    private String username = "Jugador1"; // Por defecto

    public void setNavigator(ViewNavigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        // Configurar el nombre de usuario por defecto
        usernameLabel.setText(username);

        // Configurar los eventos de los botones
        singlePlayerButton.setOnAction(event -> handleSinglePlayer());
        multiplayerButton.setOnAction(event -> handleMultiplayer());
        scoresButton.setOnAction(event -> handleViewScores());
        exitButton.setOnAction(event -> handleExit());

        // Efectos hover para los botones
        setupButtonHoverEffects(singlePlayerButton, "#2980b9");
        setupButtonHoverEffects(multiplayerButton, "#27ae60");
        setupButtonHoverEffects(scoresButton, "#e67e22");
        setupButtonHoverEffects(exitButton, "#c0392b");
    }

    private void setupButtonHoverEffects(Button button, String hoverColor) {
        String originalStyle = button.getStyle();
        button.setOnMouseEntered(e -> {
            button.setStyle(originalStyle + "; -fx-background-color: " + hoverColor + ";");
        });
        button.setOnMouseExited(e -> {
            button.setStyle(originalStyle);
        });
    }

    private void handleSinglePlayer() {
        System.out.println("Iniciando modo Un Jugador...");
        // Navegar al juego en modo un jugador
        if (navigator != null) {
            GamePlayerConfig playerOneConfig = new GamePlayerConfig("Jugador 1", "images/default-profile.png", 180);
            navigator.startGame(playerOneConfig);
        }
    }

    private void handleMultiplayer() {
        System.out.println("Iniciando modo Multijugador...");
        // // Navegar al juego en modo multijugador
        // if (navigator != null) {
        //     navigator.startGame("Jugador 1", "Jugador 2");
        // }
    }

    private void handleViewScores() {
        System.out.println("Mostrando puntuaciones...");
        // Navegar a la vista de puntuaciones
        if (navigator != null) {
            navigator.showResults();
        }
    }

    private void handleExit() {
        System.out.println("Saliendo de la aplicación...");
        Platform.exit();
        System.exit(0);
    }
}