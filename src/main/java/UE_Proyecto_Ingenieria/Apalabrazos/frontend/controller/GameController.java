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
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane; // IMPORTANTE: Añadido
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
    private GridPane questionArea; // CORRECCIÓN: Cambiado de HBox a GridPane para coincidir con el FXML

    @FXML
    private Pane roscoPane;

    @FXML
    private VBox leftOptionsArea;

    @FXML
    private VBox rightOptionsArea;

    @FXML
    private VBox liveScoresPanel; // Añadido para control de visibilidad

    @FXML
    private Label correctCountLabel; // Añadido para actualizar marcador inferior

    @FXML
    private Label incorrectCountLabel; // Añadido para actualizar marcador inferior

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

    private EventBus externalBus;
    private boolean listenerRegistered = false;
    private ViewNavigator navigator;
    private Map<String, Button> letterButtons = new HashMap<>();

    /**
     * Configura el navegador de vistas para cambiar entre pantallas
     * Lo usa ViewNavigator cuando carga este controlador
     */
    public void setNavigator(ViewNavigator navigator) {
        this.navigator = navigator;
    }

    /**
     * Recibe la configuración del jugador desde el lobby (nombre, dificultad, número de preguntas, etc.)
     * Esta info es crucial para crear el rosco con el número correcto de letras
     */
    public void setPlayerConfig(GamePlayerConfig playerConfig) {
        this.playerConfig = playerConfig;
        if (playerConfig != null) {
            if (playerConfig.getPlayer() != null) {
                this.localPlayerId = playerConfig.getPlayer().getPlayerID();
            }
            this.roomId = playerConfig.getRoomId();
        }
    }

    /**
     * Conecta el controlador al bus de eventos del GameService
     * Así puedo escuchar eventos del backend (preguntas, validaciones, timer, etc.)
     * y publicar eventos cuando el jugador hace algo (selecciona respuesta, pasa palabra)
     */
    public void setExternalBus(EventBus externalBus) {
        this.externalBus = externalBus;
        if (this.externalBus != null && !listenerRegistered) {
            this.externalBus.addListener(this);
            listenerRegistered = true;
        }
    }

    /**
     * Inicializa la interfaz cuando JavaFX carga el FXML
     * Configuro los event handlers de todos los botones (opciones A-D y pasapalabra)
     * Maximizo la ventana automáticamente para mejor experiencia visual
     * El botón de inicio empieza deshabilitado hasta que el backend confirme que todo está listo
     */
    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            if (startButton != null && startButton.getScene() != null && startButton.getScene().getWindow() != null) {
                ((javafx.stage.Stage) startButton.getScene().getWindow()).setMaximized(true);
            }
        });
        
        if (startButton != null) {
            startButton.setText("Esperando...");
            startButton.setDisable(true);
            startButton.setOnAction(event -> handleStartGame());
        }
        
        if (option1Button != null) option1Button.setOnAction(event -> handleOptionSelected(0));
        if (option2Button != null) option2Button.setOnAction(event -> handleOptionSelected(1));
        if (option3Button != null) option3Button.setOnAction(event -> handleOptionSelected(2));
        if (option4Button != null) option4Button.setOnAction(event -> handleOptionSelected(3));
        
        if (skipButton != null) {
            skipButton.setOnAction(event -> handleSkip());
        }
    }

    /**
     * Se ejecuta después de que todas las dependencias estén configuradas
     * Aquí creo el rosco con los botones circulares porque ya tengo la configuración del jugador
     * Publico GameControllerReady para avisar al backend que la UI está lista y puede empezar
     */
    public void postInitialize() {
        if (!listenerRegistered && externalBus != null) {
            externalBus.addListener(this);
            listenerRegistered = true;
        }

        createRosco();
        log.info("Rosco creado con {} letras", letterButtons.size());

        if (externalBus != null && localPlayerId != null && roomId != null) {
            externalBus.publish(new GameControllerReady(localPlayerId, roomId));
            log.info("postInitialize() completed - publishing GameControllerReady");
        }
    }

    /**
     * Crea dinámicamente los botones circulares del rosco según el número de preguntas configurado
     * Cada botón representa una letra del alfabeto (de A a Z, más Ñ si hay 27 preguntas)
     * Los guardo en un HashMap con la letra como clave para poder actualizarlos fácilmente
     * Los botones empiezan todos con estilo 'pending' (azul) hasta que se respondan
     */
    private void createRosco() {
        int numLetters = this.playerConfig.getQuestionNumber();
        double buttonSize = 42; 

        for (int i = 0; i < numLetters; i++) {
            String letter = AlphabetMap.getLetter(i);
            Button btn = new Button(letter);
            btn.setPrefSize(buttonSize, buttonSize);
            btn.setMinSize(buttonSize, buttonSize);
            btn.setMaxSize(buttonSize, buttonSize);
            btn.getStyleClass().addAll("rosco-letter", "rosco-letter-pending");
            letterButtons.put(letter, btn);
            roscoPane.getChildren().add(btn);
        }

        updateRoscoLayout();
        roscoPane.widthProperty().addListener((obs, oldVal, newVal) -> updateRoscoLayout());
        roscoPane.heightProperty().addListener((obs, oldVal, newVal) -> updateRoscoLayout());
    }

    /**
     * Calcula la posición de cada botón en forma de círculo
     * Se adapta automáticamente al tamaño del contenedor (responsive)
     * Los botones se distribuyen uniformemente alrededor del círculo empezando desde arriba
     * Uso trigonometría para calcular las coordenadas X e Y de cada letra
     */
    private void updateRoscoLayout() {
        if (letterButtons.isEmpty() || roscoPane.getWidth() <= 0 || roscoPane.getHeight() <= 0) {
            return;
        }

        int numLetters = letterButtons.size();
        double centerX = roscoPane.getWidth() / 2;
        double centerY = roscoPane.getHeight() / 2;
        double radius = Math.min(centerX, centerY) * 0.8;
        double buttonSize = 38;

        int index = 0;
        for (Button btn : letterButtons.values()) {
            double angle = Math.toRadians((360.0 / numLetters) * index - 90);
            double x = centerX + radius * Math.cos(angle) - buttonSize / 2;
            double y = centerY + radius * Math.sin(angle) - buttonSize / 2;
            btn.setLayoutX(x);
            btn.setLayoutY(y);
            index++;
        }
    }

    /**
     * Se ejecuta cuando el jugador pulsa el botón 'Empezar'
     * Oculta el botón de inicio y muestra todos los elementos del juego
     * Hace visible el rosco, los botones de opciones, la zona de pregunta y el botón pasapalabra
     * Es como una transición de la pantalla de lobby a la pantalla de juego activa
     */
    private void handleStartGame() {
        if (startButton != null) {
            startButton.setVisible(false);
            startButton.setManaged(false);
        }
        if (roscoPane != null) roscoPane.setVisible(true);
        if (playerOneCanvas != null) playerOneCanvas.setVisible(true);
        if (questionArea != null) {
            questionArea.setVisible(true);
            questionArea.setManaged(true);
        }
        if (leftOptionsArea != null) {
            leftOptionsArea.setVisible(true);
            leftOptionsArea.setManaged(true);
        }
        if (rightOptionsArea != null) {
            rightOptionsArea.setVisible(true);
            rightOptionsArea.setManaged(true);
        }
        if (skipButton != null) {
            skipButton.setVisible(true);
            skipButton.setManaged(true);
        }

        if (playerNameLabel != null && playerConfig != null && playerConfig.getPlayer() != null) {
            playerNameLabel.setVisible(true);
            playerNameLabel.setManaged(true);
            playerNameLabel.setText(playerConfig.getPlayer().getName());
        }

        if (liveScoresPanel != null) {
            liveScoresPanel.setVisible(true);
        }
    }

    /**
     * Punto de entrada para todos los eventos del backend
     * Aquí llegan TimerTickEvent (actualiza el contador), QuestionChangedEvent (nueva pregunta),
     * AnswerValidatedEvent (resultado de mi respuesta), CreatorInitGameEvent (permiso para empezar)
     * Filtro los eventos de otros jugadores comparando el playerId para no procesar cosas que no me tocan
     */
    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof TimerTickEvent) {
            int remaining = ((TimerTickEvent) event).getElapsedSeconds();
            Platform.runLater(() -> timerLabel.setText(String.valueOf(remaining)));
        } else if (event instanceof AnswerValidatedEvent) {
            handleAnswerValidated((AnswerValidatedEvent) event);
        } else if (event instanceof CreatorInitGameEvent) {
            Platform.runLater(() -> {
                if (startButton != null) {
                    startButton.setText("Empezar");
                    startButton.setDisable(false);
                }
                handleStartGame();
            });
        } else if (event instanceof QuestionChangedEvent) {
            QuestionChangedEvent questionEvent = (QuestionChangedEvent) event;
            
            if (questionEvent.getPlayerId() != null && localPlayerId != null &&
                !questionEvent.getPlayerId().equals(localPlayerId)) {
                return;
            }
            
            String letter = AlphabetMap.getLetter(questionEvent.getQuestionIndex());
            QuestionStatus status = questionEvent.getStatus();
            Button letterButton = letterButtons.get(letter);
            
            if (letterButton != null) {
                Platform.runLater(() -> {
                    for (Button btn : letterButtons.values()) {
                        btn.getStyleClass().remove("rosco-letter-current");
                    }
                    letterButton.getStyleClass().removeAll("rosco-letter-pending", "rosco-letter-correct", "rosco-letter-incorrect");
                    
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
                            letterButton.getStyleClass().add("rosco-letter-incorrect");
                            break;
                    }
                });
            }

            String questionText = questionEvent.getQuestion().getQuestionText();
            List<String> responses = questionEvent.getQuestion().getQuestionResponsesList();

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
     * Procesa la respuesta del backend cuando valida mi respuesta
     * Cambia el color del botón del rosco a verde si acerté o rojo si fallé
     * Tengo que buscar el botón en el HashMap convirtiendo la letra a minúsculas
     * Limpio todos los estilos anteriores antes de aplicar el nuevo para evitar conflictos
     */
    private void handleAnswerValidated(AnswerValidatedEvent event) {
        char letter = event.getLetter();
        QuestionStatus status = event.getStatus();
        boolean isCorrect = event.isCorrect();

        String letterStr = String.valueOf(letter).toLowerCase();
        Button letterButton = letterButtons.get(letterStr);

        if (letterButton != null) {
            Platform.runLater(() -> {
                letterButton.getStyleClass().removeAll("rosco-letter-pending", "rosco-letter-correct", "rosco-letter-incorrect", "rosco-letter-current");
                if (status == QuestionStatus.RESPONDED_OK) {
                    letterButton.getStyleClass().add("rosco-letter-correct");
                } else if (status == QuestionStatus.RESPONDED_FAIL) {
                    letterButton.getStyleClass().add("rosco-letter-incorrect");
                }
                
                // LEANDRO -> Actualizar contadores visuales con datos del evento
                if (correctCountLabel != null) {
                    correctCountLabel.setText(String.valueOf(event.getTotalCorrect()));
                }
                if (incorrectCountLabel != null) {
                    incorrectCountLabel.setText(String.valueOf(event.getTotalIncorrect()));
                }
            });
        }
    }

    /**
     * Se ejecuta cuando pulso uno de los 4 botones de respuesta (A, B, C, D)
     * Busco cuál es la letra actual del rosco (la que tiene estilo 'current')
     * Envío un AnswerSubmittedEvent al backend con el índice de la opción seleccionada
     * El formato es 'Opción X' donde X va de 1 a 4 (el backend lo parsea para validar)
     */
    private void handleOptionSelected(int selectedOption) {
        if (externalBus == null) return;
        String answer = "Opción " + (selectedOption + 1);
        char currentLetter = 0;
        boolean foundCurrentLetter = false;
        
        for (Map.Entry<String, Button> entry : letterButtons.entrySet()) {
            if (entry.getValue().getStyleClass().contains("rosco-letter-current")) {
                currentLetter = entry.getKey().charAt(0);
                foundCurrentLetter = true;
                break;
            }
        }

        if (!foundCurrentLetter) return;
        AnswerSubmittedEvent event = new AnswerSubmittedEvent(0, currentLetter, answer);
        externalBus.publish(event);
    }

    /**
     * Maneja el botón PASAR (pasapalabra)
     * Por ahora solo registra que se pulsó, falta implementar el evento en el backend
     * La idea es que marque la pregunta como 'skipped' y pase a la siguiente
     * El botón está visible pero sin funcionalidad completa todavía
     */
    private void handleSkip() {
        log.info("Botón Pasapalabra presionado");
        // Implementar lógica de publicación de evento si el backend lo soporta
    }
}