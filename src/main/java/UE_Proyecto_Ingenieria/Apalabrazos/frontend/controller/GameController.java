package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionStatus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.AlphabetMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller responsible for the gameplay view.
 * Listens to game events and updates the UI accordingly.
 */
public class GameController implements EventListener {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    @FXML
    private Label questionLabel;

    @FXML
    private Label timerLabel;

    @FXML
    private Label playerNameLabel;

    @FXML
    private Canvas playerOneCanvas;

    @FXML
    private Canvas rivalCanvas;

    @FXML
    private Button startButton;

    @FXML
    private VBox questionArea;

    @FXML
    private Pane roscoPane;

    @FXML
    private VBox leftButtonsArea;

    @FXML
    private VBox rightButtonsArea;

    @FXML
    private Button option1Button;

    @FXML
    private Button option2Button;

    @FXML
    private Button option3Button;

    @FXML
    private Button option4Button;

    @FXML
    private Button skipButton;

    private GamePlayerConfig playerConfig;
    private String localPlayerId;
    private String roomId;

    //
    private EventBus externalBus;
    private boolean listenerRegistered = false;
    private ViewNavigator navigator;
    // Mapa de botones por letra del rosco
    private Map<String, Button> letterButtons = new HashMap<>();

    public void setNavigator(ViewNavigator navigator) {
        this.navigator = navigator;
    }

    public void setPlayerConfig(GamePlayerConfig playerConfig) {
        this.playerConfig = playerConfig;
        if (playerConfig != null) {
            if (playerConfig.getPlayer() != null) {
                this.localPlayerId = playerConfig.getPlayer().getPlayerID();
            }
            this.roomId = playerConfig.getRoomId();
        }
    }

    public void setExternalBus(EventBus externalBus) {
        this.externalBus = externalBus;
        if (this.externalBus != null && !listenerRegistered) {
            this.externalBus.addListener(this);
            listenerRegistered = true;
        }
    }

    @FXML
    public void initialize() {
        // Configurar el botón de inicio
        if (startButton != null) {
            // Mientras se valida el inicio desde el lobby, mostrar "Esperando..."
            startButton.setText("Esperando...");
            startButton.setDisable(true);
            startButton.setOnAction(event -> handleStartGame());
        }
    }

    /**
     * Llamar a este método después de configurar todas las dependencias
     * (setNavigator, setPlayerConfig, setExternalBus)
     */
    public void postInitialize() {
        if (!listenerRegistered && externalBus != null) {
            externalBus.addListener(this);
            listenerRegistered = true;
        }

        // Notificar al GameService que el controller está listo (máquina de estados)
        if (externalBus != null && localPlayerId != null && roomId != null) {
            externalBus.publish(new GameControllerReady(localPlayerId, roomId));
            log.info("postInitialize() completed - publishing GameControllerReady (playerId={}, roomId={})", localPlayerId, roomId);
        }
    }

    /**
     * Crear el rosco de letras en forma circular
    */
    private void createRosco() {
        int numLetters = this.playerConfig.getQuestionNumber();
        double buttonSize = 42; // Tamaño de cada botón

        for (int i = 0; i < numLetters; i++) {
            String letter = AlphabetMap.getLetter(i);

            // Crear botón circular
            Button btn = new Button(letter);
            btn.setPrefSize(buttonSize, buttonSize);
            btn.setMinSize(buttonSize, buttonSize);
            btn.setMaxSize(buttonSize, buttonSize);

            // Aplicar clase CSS base del rosco
            btn.getStyleClass().addAll("rosco-letter", "rosco-letter-pending");
            // Guardar referencia al botón
            letterButtons.put(letter, btn);
            // Agregar al panel
            roscoPane.getChildren().add(btn);
        }

        // Calcular posiciones iniciales
        updateRoscoLayout();

        // Escuchar cambios de tamaño del roscoPane para recalcular posiciones
        roscoPane.widthProperty().addListener((obs, oldVal, newVal) -> updateRoscoLayout());
        roscoPane.heightProperty().addListener((obs, oldVal, newVal) -> updateRoscoLayout());
    }

    /**
     * Actualizar la posición de los botones del rosco según el tamaño del contenedor
     */
    private void updateRoscoLayout() {
        if (letterButtons.isEmpty() || roscoPane.getWidth() <= 0 || roscoPane.getHeight() <= 0) {
            return;
        }

        int numLetters = letterButtons.size();
        double centerX = roscoPane.getWidth() / 2;
        double centerY = roscoPane.getHeight() / 2;
        // Radio proporcional al tamaño más pequeño del contenedor
        double radius = Math.min(centerX, centerY) * 0.8;
        double buttonSize = 42;

        int index = 0;
        for (Button btn : letterButtons.values()) {
            // Calcular posición en círculo (empezar desde arriba, -90 grados)
            double angle = Math.toRadians((360.0 / numLetters) * index - 90);
            double x = centerX + radius * Math.cos(angle) - buttonSize / 2;
            double y = centerY + radius * Math.sin(angle) - buttonSize / 2;

            btn.setLayoutX(x);
            btn.setLayoutY(y);
            index++;
        }
    }

    /**
     * Handle start game button click
    */
    private void handleStartGame() {
        // Ocultar el botón de inicio
        if (startButton != null) {
            startButton.setVisible(false);
            startButton.setManaged(false);
        }
        // Mostrar el rosco
        if (roscoPane != null) {
            roscoPane.setVisible(true);
            roscoPane.setManaged(true);
        }
        // Mostrar el canvas del juego
        if (playerOneCanvas != null) {
            playerOneCanvas.setVisible(true);
            playerOneCanvas.setManaged(true);
        }
        // Mostrar la zona de preguntas
        if (questionArea != null) {
            questionArea.setVisible(true);
            questionArea.setManaged(true);
        }
        // Mostrar área de botones de opciones
        if (leftButtonsArea != null) {
            leftButtonsArea.setVisible(true);
            leftButtonsArea.setManaged(true);
        }
        if (rightButtonsArea != null) {
            rightButtonsArea.setVisible(true);
            rightButtonsArea.setManaged(true);
        }
        // Mostrar botón pasapalabra
        if (skipButton != null) {
            skipButton.setVisible(true);
            skipButton.setManaged(true);
        }

        // Mostrar el nombre del jugador configurado
        if (playerNameLabel != null && playerConfig != null && playerConfig.getPlayer() != null) {
            playerNameLabel.setVisible(true);
            playerNameLabel.setManaged(true);
            playerNameLabel.setText(playerConfig.getPlayer().getName());
        }

        // Crear el rosco con botones circulares, lo pongo aqui porque me aseguro
        // que ya existe la config. Necesito la config para pintar  el rosco
        createRosco();
    }

    /**
     * Recibir y procesar eventos del juego
     */
    @Override
    public void onEvent(GameEvent event) {
        // Verificar el tipo de evento y llamar al método apropiado
        if (event instanceof TimerTickEvent) {
            int remaining = ((TimerTickEvent) event).getElapsedSeconds();
            Platform.runLater(() -> timerLabel.setText(String.valueOf(remaining)));
        } else if (event instanceof CreatorInitGameEvent) {
            // Validación correcta del inicio del juego por el creador
            Platform.runLater(() -> {
                if (startButton != null) {
                    startButton.setText("Empezar");
                    startButton.setDisable(false);
                }
                // Iniciar como si se hubiera pulsado el botón
                handleStartGame();
            });
        } else if (event instanceof QuestionChangedEvent) {
            QuestionChangedEvent questionEvent = (QuestionChangedEvent) event;
            // Filtrar por destinatario si el evento viene dirigido a un jugador concreto
            if (questionEvent.getPlayerId() != null && localPlayerId != null &&
                !questionEvent.getPlayerId().equals(localPlayerId)) {
                log.debug("Evento ignorado: destinado a {} pero este controlador es de {}",
                         questionEvent.getPlayerId(), localPlayerId);
                return; // Evento para otro jugador; ignorar
            }
            log.debug("Procesando QuestionChangedEvent para jugador: {} (índice: {})",
                     localPlayerId, questionEvent.getQuestionIndex());
            String letter = AlphabetMap.getLetter(questionEvent.getQuestionIndex());
            QuestionStatus status = questionEvent.getStatus();

            // Actualizar el botón del rosco según el estado de la pregunta
            Button letterButton = letterButtons.get(letter);
            if (letterButton != null) {
                Platform.runLater(() -> {
                    letterButton.getStyleClass().removeAll("rosco-letter-pending", "rosco-letter-correct", "rosco-letter-wrong", "rosco-letter-current");
                    switch (status) {
                        case INIT:
                            letterButton.getStyleClass().add("rosco-letter-current");
                            break;
                        case PASSED:
                            letterButton.getStyleClass().add("rosco-letter-pending");
                            break;
                        case RESPONDED_OK:
                            letterButton.getStyleClass().add("rosco-letter-correct");
                            break;
                        case RESPONDED_FAIL:
                            letterButton.getStyleClass().add("rosco-letter-wrong");
                            break;
                        default:
                            letterButton.getStyleClass().add("rosco-letter-pending");
                    }
                });
            }

            // Obtener texto de la pregunta y respuestas
            String questionText = questionEvent.getQuestion().getQuestionText();
            List<String> responses = questionEvent.getQuestion().getQuestionResponsesList();

            // Solo actualizar el contenido de texto
            Platform.runLater(() -> {
                questionLabel.setText(questionText);
                option1Button.setText(responses.get(0));
                option2Button.setText(responses.get(1));
                option3Button.setText(responses.get(2));
                option4Button.setText(responses.get(3));
            });
        }
    }
}