package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GameType;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionLevel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
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
    private Button jugarButton;

    @FXML
    private TextField playerNameInput;

    @FXML
    private TextField questionCountInput;

    @FXML
    private TextField durationSecondsInput;

    @FXML
    private ComboBox<String> difficultyInput;

    @FXML
    private ComboBox<String> gameTypeInput;

    @FXML
    private VBox jugarInputs;

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

        // Poblar dificultad
        if (difficultyInput != null) {
            difficultyInput.getItems().clear();
            difficultyInput.getItems().addAll("EASY", "MEDIUM", "HARD");
            difficultyInput.setValue("EASY"); // Valor por defecto
        }

        // Poblar tipo de partida
        if (gameTypeInput != null) {
            gameTypeInput.getItems().clear();
            gameTypeInput.getItems().addAll("HIGHER_POINTS_WINS", "NUMBER_WINS");
            gameTypeInput.setValue("HIGHER_POINTS_WINS"); // Valor por defecto
        }

        // Configurar los eventos de los botones
        jugarButton.setOnAction(event -> handleMultiplayer());
        scoresButton.setOnAction(event -> handleViewScores());
        exitButton.setOnAction(event -> handleExit());

        // Efectos hover para los botones
        setupButtonHoverEffects(jugarButton, "#2980b9");
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

    private void handleMultiplayer() {
        // Por ahora, el lobby se abre desde "Un jugador" (temporal)
        if (navigator != null) {
            navigator.showLobby();
        }
    }

    private void markError(TextField field) {
        field.setStyle(field.getStyle() + "; -fx-border-color: red; -fx-border-width: 2px;");
    }

    private void markErrorCombo(ComboBox<?> combo) {
        combo.setStyle(combo.getStyle() + "; -fx-border-color: red; -fx-border-width: 2px;");
    }

    private int parsePositiveInt(String value, TextField field) {
        try {
            int n = Integer.parseInt(value);
            if (n <= 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException ex) {
            markError(field);
            return -1;
        }
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