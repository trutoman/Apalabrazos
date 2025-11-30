package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;

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
    private TextField playerNameInput;

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
    }

    private void handleSinglePlayer() {
        if (!playerNameInput.isVisible()) {
            // Animar el botón hacia arriba
            TranslateTransition transition = new TranslateTransition(Duration.millis(300), singlePlayerButton);
            transition.setByY(-10); // Mover 10px hacia arriba
            transition.play();

            // Mostrar el campo de texto
            playerNameInput.setVisible(true);
            playerNameInput.setManaged(true);
            playerNameInput.requestFocus();
        } else {
            String name = playerNameInput.getText().trim();
            if (name.isEmpty()) {
                // No permitir avanzar si el nombre está vacío
                playerNameInput.getStyleClass().add("player-name-input-error");
                return;
            }
            // Remover clase de error si había
            playerNameInput.getStyleClass().remove("player-name-input-error");
            
            System.out.println("Iniciando modo Un Jugador con nombre: " + name);
            // Navegar al juego en modo un jugador
            if (navigator != null) {
                GamePlayerConfig playerOneConfig = new GamePlayerConfig(name, "images/default-profile.png", 180);
                navigator.startGame(playerOneConfig);
            }
        }
    }

    private void handleMultiplayer() {
        System.out.println("Iniciando modo Multijugador...");
        // // Navegar al juego en modo multijugador
        // if (navigator != null) {
        // navigator.startGame("Jugador 1", "Jugador 2");
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