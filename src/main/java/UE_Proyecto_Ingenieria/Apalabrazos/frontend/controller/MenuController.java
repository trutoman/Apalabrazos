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
    private Button singlePlayerButton;

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
    private VBox singlePlayerInputs;

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
        if (!singlePlayerInputs.isVisible()) {
            // Animación ligera del botón
            TranslateTransition transition = new TranslateTransition(Duration.millis(250), singlePlayerButton);
            transition.setByY(-8);
            transition.play();

            // Mostrar todos los inputs
            singlePlayerInputs.setVisible(true);
            singlePlayerInputs.setManaged(true);
            playerNameInput.requestFocus();
            return;
        }

        // Validar campos
        String name = playerNameInput.getText().trim();
        String questionsStr = questionCountInput.getText().trim();
        String durationStr = durationSecondsInput.getText().trim();
        String difficultyStr = difficultyInput.getValue() == null ? "" : difficultyInput.getValue().trim().toUpperCase();
        String gameTypeStr = gameTypeInput.getValue() == null ? "" : gameTypeInput.getValue().trim().toUpperCase();

        boolean error = false;
        if (name.isEmpty()) {
            markError(playerNameInput);
            error = true;
        }
        int questionCount = parsePositiveInt(questionsStr, questionCountInput);
        if (questionCount == -1)
            error = true;
        int durationSeconds = parsePositiveInt(durationStr, durationSecondsInput);
        if (durationSeconds == -1)
            error = true;
        if (!(difficultyStr.equals("EASY") || difficultyStr.equals("MEDIUM") || difficultyStr.equals("HARD"))) {
            markErrorCombo(difficultyInput);
            error = true;
        }
        if (!(gameTypeStr.equals("HIGHER_POINTS_WINS") || gameTypeStr.equals("NUMBER_WINS"))) {
            markErrorCombo(gameTypeInput);
            error = true;
        }
        if (error) {
            return; // Hay errores, no continuar
        }

        System.out.println("Single player -> name=" + name + ", questions=" + questionCount + ", duration=" + durationSeconds + ", difficulty=" + difficultyStr + ", gameType=" + gameTypeStr);

        try {
            // Nowadays here we create the unique player ID structure
            Player player = new Player(name);
            System.out.println("Player created: " + player.getName() + " with ID: " + player.getPlayerID());
            
            GamePlayerConfig playerOneConfig = new GamePlayerConfig();
            playerOneConfig.setPlayer(player);
            playerOneConfig.setQuestionNumber(questionCount);
            playerOneConfig.setTimerSeconds(durationSeconds);
            playerOneConfig.setGameType(GameType.valueOf(gameTypeStr));
            playerOneConfig.setDifficultyLevel(QuestionLevel.valueOf(difficultyStr));
            System.out.println("GamePlayerConfig created successfully");
            
            if (navigator != null) {
                System.out.println("Navigator is not null, starting game...");
                navigator.startGame(playerOneConfig);
            } else {
                System.err.println("ERROR: Navigator is null!");
            }
        } catch (Exception e) {
            System.err.println("ERROR in handleSinglePlayer: " + e.getMessage());
            e.printStackTrace();
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