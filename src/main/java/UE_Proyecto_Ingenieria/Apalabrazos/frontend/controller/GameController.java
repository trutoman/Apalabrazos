package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.AlphabetMap;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameService;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionList;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionStatus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.tools.QuestionFileLoader;

/**
 * Controller responsible for the gameplay view.
 * Listens to game events and updates the UI accordingly.
 */
public class GameController implements EventListener {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    @FXML
    private Label questionLabel;

    @FXML
    private Label optionALabel;

    @FXML
    private Label optionBLabel;

    @FXML
    private Label optionCLabel;

    @FXML
    private Label optionDLabel;

    @FXML
    private Label timerLabel;

    @FXML
    private Pane roscoPane;

    @FXML
    private Button startButton;

    @FXML
    private VBox questionArea;

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

    @FXML
    private Canvas playerOneCanvas;

    @FXML
    private Canvas rivalCanvas;

    @FXML
    private VBox leftOptionsArea;

    @FXML
    private VBox rightOptionsArea;

    @FXML
    private Label correctCountLabel;

    @FXML
    private Label incorrectCountLabel;

    @FXML
    private Label skippedCountLabel;


    private EventBus externalBus;
    private boolean listenerRegistered = false;
    private String localPlayerId;
    private String roomId;

    private GamePlayerConfig playerConfig;

    // El gameController contiene e instancia el gameservice ... discutible
    private GameService gameService;

    private ViewNavigator navigator;

    // Mapa para guardar los botones de cada letra
    private Map<String, Button> letterButtons = new HashMap<>();

    // Índice de la pregunta actual (se actualiza con cada QuestionChangedEvent)
    private int currentQuestionIndex = 0;

    // Array de letras del rosco
    private static final String[] LETTERS = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
                                              "N", "Ñ", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};

    // Estados de cada letra: "pending", "correct", "incorrect", "skipped"
    private String[] letterStates = new String[LETTERS.length];

    // Para rastrear las letras pendientes en cada ronda
    private java.util.Queue<Integer> pendingLetters = new java.util.LinkedList<>();

    public void setNavigator(ViewNavigator navigator) {
        log.debug("setNavigator(navigator={})", navigator);
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
        log.debug("setExternalBus(externalBus={})", externalBus);
        this.externalBus = externalBus;
        if (this.externalBus != null && !listenerRegistered) {
            this.externalBus.addListener(this);
            listenerRegistered = true;
        }
    }

    @FXML
    public void initialize() {
        log.debug("initialize()");
        // Configurar el botón de inicio
        if (startButton != null) {
            // Mientras se valida el inicio desde el lobby, mostrar "Esperando..."
            startButton.setText("Esperando...");
            startButton.setDisable(true);
            startButton.setOnAction(event -> handleStartGame());
        }

        // Crear el rosco con botones circulares
        if (roscoPane != null) {
            // Listener para recalcular cuando cambie el tamaño
            roscoPane.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (roscoPane.getHeight() > 0) {
                    recreateRosco();
                }
            });
            roscoPane.heightProperty().addListener((obs, oldVal, newVal) -> {
                if (roscoPane.getWidth() > 0) {
                    recreateRosco();
                }
            });
            // Crear el rosco cuando el layout ya tenga tamaño
            Platform.runLater(this::recreateRosco);
        }

        // Inicializar estados de letras
        initializeLetterStates();

        // Crear el rosco inmediatamente para que letterButtons esté listo
        if (roscoPane != null && letterButtons.isEmpty()) {
            createRosco();
        }

        // Configurar eventos de los botones de opciones
        setupOptionButtons();

        // Dibujar círculos centrados en los canvas (y redibujar en cambios de tamaño)
        if (playerOneCanvas != null) {
            setupCanvasCircle(playerOneCanvas);
        }
        if (rivalCanvas != null) {
            setupCanvasCircle(rivalCanvas);
        }
    }

    /**
     * Llamar a este método después de configurar todas las dependencias
     * (setNavigator, setPlayerConfig, setExternalBus)
     */
    public void postInitialize() {
        log.debug("postInitialize()");
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
     * Recrear el rosco cuando cambia el tamaño
     */
    private void recreateRosco() {
        log.debug("recreateRosco()");
        if (roscoPane == null || roscoPane.getWidth() <= 0 || roscoPane.getHeight() <= 0) {
            return;
        }
        roscoPane.getChildren().clear();
        letterButtons.clear();
        createRosco();

        // Restaurar estados visuales
        for (int i = 0; i < LETTERS.length; i++) {
            updateLetterState(LETTERS[i], letterStates[i]);
        }
    }

    /**
     * Manejador para cuando se presiona el botón PASAR
     */
    @FXML
    private void handleSkipPressed() {
        log.debug("handleSkipPressed()");
        if (skipButton != null) {
            skipButton.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #5dade2; -fx-text-fill: #ecf0f1; -fx-border-radius: 65; -fx-background-radius: 65; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 3);");
        }
    }

    /**
     * Manejador para cuando se suelta el botón PASAR
     */
    @FXML
    private void handleSkipReleased() {
        log.debug("handleSkipReleased()");
        if (skipButton != null) {
            skipButton.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #3498db; -fx-text-fill: white; -fx-border-radius: 65; -fx-background-radius: 65; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 3);");
        }
    }

    /**
     * Crear el rosco de letras en forma circular
     */
    private void createRosco() {
        log.debug("createRosco()");
        if (roscoPane == null) {
            log.error("roscoPane es null - verifica que esté definido en el FXML con fx:id=\"roscoPane\"");
            return;
        }

        // Obtener el tamaño actual del pane (o usar el tamaño preferido si aún no se ha renderizado)
        double paneWidth = roscoPane.getWidth() > 0 ? roscoPane.getWidth() : roscoPane.getPrefWidth();
        double paneHeight = roscoPane.getHeight() > 0 ? roscoPane.getHeight() : roscoPane.getPrefHeight();

        // Si aún no tenemos tamaño, usar un valor por defecto
        if (paneWidth <= 0) paneWidth = 550;
        if (paneHeight <= 0) paneHeight = 550;

        int numLetters = LETTERS.length;
        double centerX = paneWidth / 2;
        double centerY = paneHeight / 2;
        // Radio proporcional al tamaño del contenedor (37% del tamaño mínimo)
        double radius = Math.min(paneWidth, paneHeight) * 0.37;
        double buttonSize = Math.min(44, radius / 5); // Tamaño proporcional pero no menor a 30
        if (buttonSize < 30) buttonSize = 30;

        for (int i = 0; i < numLetters; i++) {
            String letter = LETTERS[i];

            // Calcular posición en círculo (empezar desde arriba, -90 grados)
            double angle = Math.toRadians((360.0 / numLetters) * i - 90);
            double x = centerX + radius * Math.cos(angle) - buttonSize / 2;
            double y = centerY + radius * Math.sin(angle) - buttonSize / 2;

            // Crear botón circular
            Button btn = new Button(letter);
            btn.setPrefSize(buttonSize, buttonSize);
            btn.setMinSize(buttonSize, buttonSize);
            btn.setMaxSize(buttonSize, buttonSize);
            btn.setLayoutX(x);
            btn.setLayoutY(y);

            // Aplicar clase CSS base del rosco
            btn.getStyleClass().addAll("rosco-letter", "rosco-letter-pending");

            // Guardar referencia al botón
            letterButtons.put(letter, btn);

            // Agregar al panel
            roscoPane.getChildren().add(btn);
        }
    }

    /**
     * Handle start game button click
     */
    private void handleStartGame() {
        log.debug("handleStartGame()");

        // Ocultar el botón de inicio
        if (startButton != null) {
            startButton.setVisible(false);
            startButton.setManaged(false);
            log.debug("Botón de inicio ocultado");
        }

        // Mostrar el rosco
        if (roscoPane != null) {
            roscoPane.setVisible(true);
            roscoPane.setManaged(true);
            log.debug("Rosco mostrado");
        }

        // Mostrar las áreas de opciones
        if (leftOptionsArea != null) {
            leftOptionsArea.setVisible(true);
            leftOptionsArea.setManaged(true);
        }
        if (rightOptionsArea != null) {
            rightOptionsArea.setVisible(true);
            rightOptionsArea.setManaged(true);
        }

        // Mostrar la zona de preguntas
        if (questionArea != null) {
            questionArea.setVisible(true);
            questionArea.setManaged(true);
            log.debug("Área de preguntas mostrada");
        }

        // Mostrar el botón de pasapalabra
        if (skipButton != null) {
            skipButton.setVisible(true);
            skipButton.setManaged(true);
        }
    }

    /**
     * Configurar dibujo de círculos en canvas
     */
    private void setupCanvasCircle(Canvas canvas) {
        log.debug("setupCanvasCircle(canvas={})", canvas);
        if (canvas == null) return;
        // Canvas está preparado para futuros dibujos si es necesario
        // Por ahora solo se define el tamaño
        canvas.setWidth(400);
        canvas.setHeight(400);
    }

    /**
     * Configurar los eventos de los botones de opciones
     */
    private void setupOptionButtons() {
        log.debug("setupOptionButtons()");
        if (option1Button != null) {
            option1Button.setOnAction(event -> handleAnswer(0));
        }
        if (option2Button != null) {
            option2Button.setOnAction(event -> handleAnswer(1));
        }
        if (option3Button != null) {
            option3Button.setOnAction(event -> handleAnswer(2));
        }
        if (option4Button != null) {
            option4Button.setOnAction(event -> handleAnswer(3));
        }
        if (skipButton != null) {
            skipButton.setOnAction(event -> handlePasapalabra());
        }
    }

    /**
     * Inicializar todos los estados de letras como pendientes
     */
    private void initializeLetterStates() {
        log.debug("initializeLetterStates()");
        for (int i = 0; i < LETTERS.length; i++) {
            letterStates[i] = "pending";
            pendingLetters.add(i);
        }
    }

    /**
     * Habilitar todos los botones de respuesta
     */
    private void enableButtons() {
        log.debug("enableButtons()");
        if (option1Button != null) option1Button.setDisable(false);
        if (option2Button != null) option2Button.setDisable(false);
        if (option3Button != null) option3Button.setDisable(false);
        if (option4Button != null) option4Button.setDisable(false);
        if (skipButton != null) skipButton.setDisable(false);
    }

    /**
     * Deshabilitar todos los botones de respuesta
     */
    private void disableButtons() {
        log.debug("disableButtons()");
        if (option1Button != null) option1Button.setDisable(true);
        if (option2Button != null) option2Button.setDisable(true);
        if (option3Button != null) option3Button.setDisable(true);
        if (option4Button != null) option4Button.setDisable(true);
        if (skipButton != null) skipButton.setDisable(true);
    }

    /**
     * Manejar la respuesta del usuario
     */
    private void handleAnswer(int selectedIndex) {
        log.debug("handleAnswer(selectedIndex={})", selectedIndex);

        if (currentQuestionIndex == -1) {
            log.warn("No hay pregunta actual disponible");
            return;
        }

        // Publicar evento con el índice actual que viene del último QuestionChangedEvent
        publishAnswerSubmitted(currentQuestionIndex, selectedIndex);
    }

    /**
     * Publicar evento de respuesta enviada al GameService
     */
    private void publishAnswerSubmitted(int questionIndex, int selectedOption) {
        if (externalBus != null && localPlayerId != null) {
            AnswerSubmittedEvent event = new AnswerSubmittedEvent(localPlayerId, questionIndex, selectedOption);
            log.info("Enviando Respuesta - PlayerId: {}, QuestionIndex: {}, SelectedOption: {}",
                     localPlayerId, questionIndex, selectedOption);
            externalBus.publish(event);

        } else {
            log.warn("No se puede publicar AnswerSubmittedEvent: externalBus o localPlayerId es null");
        }
    }

    /**
     * Manejar el botón Pasapalabra
     */
    private void handlePasapalabra() {
        log.debug("handlePasapalabra()");
        if (currentQuestionIndex == -1) {
            log.warn("No hay pregunta actual disponible");
            return;
        }

        // Publicar AnswerSubmittedEvent con selectedOption = -1 para indicar pasapalabra
        publishAnswerSubmitted(currentQuestionIndex, -1);
        log.info("Pasapalabra publicado para pregunta: {}", currentQuestionIndex);
    }

    /**
     * Actualizar el color de una letra según su estado
     */
    public void updateLetterState(String letter, String state) {
        log.debug("updateLetterState(letter={}, state={})", letter, state);
        Button btn = letterButtons.get(letter.toUpperCase());
        if (btn != null) {
            Platform.runLater(() -> {
                // Remover todas las clases de estado previas
                btn.getStyleClass().removeAll("rosco-letter-pending", "rosco-letter-correct",
                                              "rosco-letter-incorrect", "rosco-letter-current");

                // Agregar la clase correspondiente al nuevo estado
                // Estados guardados: "responded_ok", "responded_fail", "init", "passed", "pending"
                switch (state.toLowerCase()) {
                    case "responded_ok":
                        btn.getStyleClass().add("rosco-letter-correct");
                        break;
                    case "responded_fail":
                        btn.getStyleClass().add("rosco-letter-incorrect");
                        break;
                    case "init":
                        btn.getStyleClass().add("rosco-letter-current");
                        break;
                    case "passed":
                        btn.getStyleClass().add("rosco-letter-pending");
                        break;
                    case "pending":
                        btn.getStyleClass().add("rosco-letter-pending");
                        break;
                }
            });
        }
    }

    /**
     * Recibir y procesar eventos del juego
     */
    @Override
    public void onEvent(GameEvent event) {
        log.debug("onEvent(event={})", event);
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
            String playerId = questionEvent.getPlayerId();
            int questionIndex = questionEvent.getQuestionIndex();
            QuestionStatus status = questionEvent.getStatus();
            Question nextQuestion = questionEvent.getNextQuestion();
            handleQuestionChangedEvent(playerId, questionIndex, status, nextQuestion);
        }
    }

    /**
     * Manejar el evento de cambio de pregunta
     */
    private void handleQuestionChangedEvent(String playerId, int questionIndex, QuestionStatus status, Question nextQuestion) {
        log.debug("handleQuestionChangedEvent(playerId={}, questionIndex={}, status={}, nextQuestion={})",
                  playerId, questionIndex, status, nextQuestion);

        // Filtrar por destinatario si el evento viene dirigido a un jugador concreto
        if (playerId != null && localPlayerId != null && !playerId.equals(localPlayerId)) {
            log.debug("Evento ignorado: destinado a {} pero este controlador es de {}",
                     playerId, localPlayerId);
            return; // Evento para otro jugador; ignorar
        }

        String letter;
        // Obtener la letra correspondiente al índice
        if (questionIndex == -1) {
            letter = AlphabetMap.getLetter(0);
        } else {
            letter = AlphabetMap.getLetter(questionIndex);
        }

        log.info("handleQuestionChangedEvent - letra: {}, status: {}, questionIndex: {}", letter, status, questionIndex);
        log.info("letterButtons keys: {}", letterButtons.keySet());

        // Actualizar el botón del rosco según el estado de la pregunta
        Button letterButton = letterButtons.get(letter.toUpperCase());
        if (letterButton != null) {
            // Guardar el estado en letterStates
            int letterIndex = AlphabetMap.getIndex(letter);
            if (letterIndex >= 0 && letterIndex < letterStates.length) {
                letterStates[letterIndex] = status.toString().toLowerCase();
                log.info("Estado de letra guardado - letterIndex: {}, state: {}", letterIndex, letterStates[letterIndex]);
            }

            Platform.runLater(() -> {
                letterButton.getStyleClass().removeAll("rosco-letter-pending", "rosco-letter-correct", "rosco-letter-incorrect", "rosco-letter-current");
                switch (status) {
                    case INIT:
                        letterButton.getStyleClass().add("rosco-letter-current");
                        break;
                    case PASSED:
                        letterButton.getStyleClass().add("rosco-letter-skipped");
                        break;
                    case RESPONDED_OK:
                        letterButton.getStyleClass().add("rosco-letter-correct");
                        break;
                    case RESPONDED_FAIL:
                        letterButton.getStyleClass().add("rosco-letter-incorrect");
                        log.info("Aplicada clase 'rosco-letter-incorrect' a letra: {}", letter);
                        break;
                    default:
                        letterButton.getStyleClass().add("rosco-letter-pending");
                }
            });
        } else {
            log.warn("BOTÓN NO ENCONTRADO para letra: {} (buscado como: {})", letter, letter.toUpperCase());
            log.warn("Letras disponibles: {}", letterButtons.keySet());
        }

        // Mostrar la siguiente pregunta si existe
        if (nextQuestion != null) {
            String questionText = nextQuestion.getQuestionText();
            List<String> responses = nextQuestion.getQuestionResponsesList();

            Platform.runLater(() -> {
                questionLabel.setText(questionText);
                if (responses.size() > 0) optionALabel.setText("A) " + responses.get(0));
                if (responses.size() > 1) optionBLabel.setText("B) " + responses.get(1));
                if (responses.size() > 2) optionCLabel.setText("C) " + responses.get(2));
                if (responses.size() > 3) optionDLabel.setText("D) " + responses.get(3));
            });
            this.currentQuestionIndex = questionIndex + 1;
        }
    }
}