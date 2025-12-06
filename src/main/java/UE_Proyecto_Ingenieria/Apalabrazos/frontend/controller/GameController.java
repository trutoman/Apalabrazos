package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventListener;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GameEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GameStartedEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.QuestionChangedEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.TimerTickEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

public class GameController implements EventListener {

    // Navegador (MainApp); no lo usamos ahora pero lo necesita showGame(...)
    private Object navigator;

    // Config de la partida, viene desde showGame(...)
    private GamePlayerConfig playerConfig;

    // Servicio de juego
    private final GameService gameService = new GameService();

    // --- FXML ---

    @FXML
    private VBox questionArea;

    @FXML
    private Label questionPromptLabel;

    @FXML
    private Label feedbackLabel;

    @FXML
    private Button optionButtonOne;

    @FXML
    private Button optionButtonTwo;

    @FXML
    private Button optionButtonThree;

    @FXML
    private Button optionButtonFour;

    @FXML
    private Button startButton;   // Botón verde "Empezar"

    @FXML
    private Label timerLabel;     // Label del contador (fx:id="timerLabel" en game.fxml)

    @FXML
    public void initialize() {
        // Registrar este controlador como listener del GameService
        gameService.addListener(this);

        // Ocultar el área de preguntas al principio
        if (questionArea != null) {
            questionArea.setVisible(false);
            questionArea.setManaged(false);
        }

        // Conectar el botón "Empezar" a nuestro manejador
        if (startButton != null) {
            startButton.setOnAction(e -> handleStartButton());
        }
    }

    // Lo llama MainApp en showGame(...)
    public void setNavigator(Object navigator) {
        this.navigator = navigator;
    }

    // Lo llama MainApp en showGame(...)
    public void setPlayerConfig(GamePlayerConfig playerConfig) {
        // Solo guardamos la config; NO empezamos la partida aún
        this.playerConfig = playerConfig;
    }

    // Pulsar el botón "Empezar" -> arranca la partida
    private void handleStartButton() {
        if (playerConfig == null) {
            System.out.println("[GameController] playerConfig es null, no se puede empezar.");
            return;
        }

        // Lanzamos el evento de inicio hacia el GameService
        GameStartedEvent startEvent = new GameStartedEvent(playerConfig);
        gameService.onEvent(startEvent);

        // Ocultamos/desactivamos el botón de empezar
        startButton.setDisable(true);
        startButton.setVisible(false);
    }

    @Override
    public void onEvent(GameEvent event) {

        if (event instanceof QuestionChangedEvent) {
            QuestionChangedEvent qEvent = (QuestionChangedEvent) event;
            Question question = qEvent.getQuestion();
            Platform.runLater(() -> updateQuestion(question));
        } else if (event instanceof TimerTickEvent) {
            TimerTickEvent tickEvent = (TimerTickEvent) event;
            // Este TimerTickEvent ya viene del GameService con el tiempo restante
            int seconds = tickEvent.getElapsedSeconds();
            Platform.runLater(() -> updateTimer(seconds));
        }
    }

    // Pinta el enunciado y las cuatro opciones
    private void updateQuestion(Question question) {
        if (question == null) {
            return;
        }

        // Mostrar área de preguntas
        if (questionArea != null) {
            questionArea.setVisible(true);
            questionArea.setManaged(true);
        }

        // Enunciado
        if (questionPromptLabel != null) {
            questionPromptLabel.setText(question.getQuestionText());
        }

        // Opciones
        List<String> responses = question.getQuestionResponsesList();
        if (responses != null && responses.size() >= 4) {
            optionButtonOne.setText(responses.get(0));
            optionButtonTwo.setText(responses.get(1));
            optionButtonThree.setText(responses.get(2));
            optionButtonFour.setText(responses.get(3));
        }

        if (feedbackLabel != null) {
            feedbackLabel.setText("");
        }
    }

    // Actualiza el contador de tiempo
    private void updateTimer(int seconds) {
        if (timerLabel != null) {
            timerLabel.setText(String.valueOf(seconds));
        }
    }
}
