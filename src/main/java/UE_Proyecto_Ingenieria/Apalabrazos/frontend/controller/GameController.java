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
        
        // Configurar botones de opciones
        if (option1Button != null) {
            option1Button.setOnAction(event -> handleOptionSelected(0));
        }
        if (option2Button != null) {
            option2Button.setOnAction(event -> handleOptionSelected(1));
        }
        if (option3Button != null) {
            option3Button.setOnAction(event -> handleOptionSelected(2));
        }
        if (option4Button != null) {
            option4Button.setOnAction(event -> handleOptionSelected(3));
        }
        
        // Configurar botón de skip (pasapalabra)
        if (skipButton != null) {
            skipButton.setOnAction(event -> handleSkip());
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
        double buttonSize = 38;;

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
        } else if (event instanceof AnswerValidatedEvent) {
            handleAnswerValidated((AnswerValidatedEvent) event);
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
                return; // Evento para otro jugador; ignorar
            }
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

    /**
     * Manejar cuando se recibe la validación de una respuesta
     * Actualiza el color del botón del rosco según si fue correcta o incorrecta
     */
    private void handleAnswerValidated(AnswerValidatedEvent event) {
        char letter = event.getLetter();
        QuestionStatus status = event.getStatus();
        boolean isCorrect = event.isCorrect();

        log.info("Respuesta validada para letra {}: {} (status={})", letter, isCorrect ? "CORRECTA" : "INCORRECTA", status);

        // Obtener el botón correspondiente a la letra
        String letterStr = String.valueOf(letter).toUpperCase();
        Button letterButton = letterButtons.get(letterStr);

        if (letterButton != null) {
            Platform.runLater(() -> {
                // Remover todas las clases de estado anteriores
                letterButton.getStyleClass().removeAll("rosco-letter-pending", "rosco-letter-correct", "rosco-letter-wrong", "rosco-letter-current");

                // Aplicar el estilo según el status
                if (status == QuestionStatus.RESPONDED_OK) {
                    letterButton.getStyleClass().add("rosco-letter-correct");
                    log.debug("Botón {} pintado en verde (correcto)", letter);
                } else if (status == QuestionStatus.RESPONDED_FAIL) {
                    letterButton.getStyleClass().add("rosco-letter-wrong");
                    log.debug("Botón {} pintado en rojo (incorrecto)", letter);
                }
            });
        }
    }

    /**
     * Manejar cuando el jugador selecciona una opción de respuesta
     * Publica AnswerSubmittedEvent al bus externo para que GameService lo procese
     */
    private void handleOptionSelected(int selectedOption) {
        if (externalBus == null) {
            log.warn("handleOptionSelected: externalBus es null");
            return;
        }

        // Obtener el texto de la respuesta seleccionada
        String answer = "";
        switch (selectedOption) {
            case 0:
                answer = option1Button.getText();
                break;
            case 1:
                answer = option2Button.getText();
                break;
            case 2:
                answer = option3Button.getText();
                break;
            case 3:
                answer = option4Button.getText();
                break;
        }

        // Validar que hay texto en la respuesta
        if (answer.isEmpty()) {
            log.warn("handleOptionSelected: la respuesta está vacía");
            return;
        }

        // Obtener la letra actual del rosco (buscar el botón con estilo "current")
        char currentLetter = 0; // Sin letra por defecto
        boolean foundCurrentLetter = false;
        
        for (Map.Entry<String, Button> entry : letterButtons.entrySet()) {
            if (entry.getValue().getStyleClass().contains("rosco-letter-current")) {
                currentLetter = entry.getKey().charAt(0);
                foundCurrentLetter = true;
                break;
            }
        }

        // Validar que se encontró una letra actual válida
        if (!foundCurrentLetter) {
            log.warn("handleOptionSelected: no se encontró letra actual en el rosco");
            return;
        }

        log.info("Opción seleccionada: {} (respuesta: '{}') para letra {}", selectedOption, answer, currentLetter);

        // Publicar evento con: playerIndex=0 (single player siempre es jugador 0), 
        // letra actual, y texto de respuesta
        AnswerSubmittedEvent event = new AnswerSubmittedEvent(0, currentLetter, answer);
        externalBus.publish(event);
    }

    /**
     * Manejar cuando el jugador presiona el botón de "Pasapalabra"
     * Por ahora solo registra el evento (falta implementar el evento en backend)
     */
    private void handleSkip() {
        log.info("Botón Pasapalabra presionado");
        // TODO: El backend necesita implementar QuestionSkippedEvent
        
    }
}