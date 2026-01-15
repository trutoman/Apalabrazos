package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
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

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Question;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.QuestionList;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.tools.QuestionFileLoader;

/**
 * Controller responsible for the gameplay view.
 * Listens to game events and updates the UI accordingly.
 */
public class GameController implements EventListener {

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

    private GamePlayerConfig playerConfig;

    // El gameController contiene e instancia el gameservice ... discutible
    private GameService gameService;

    private ViewNavigator navigator;
    
    // Mapa para guardar los botones de cada letra
    private Map<String, Button> letterButtons = new HashMap<>();
    
    // Lista de preguntas cargadas desde JSON
    private List<Question> questions;
    
    // Índice de la pregunta actual (corresponde a la letra del rosco)
    private int currentQuestionIndex = 0;
    
    // Array de letras del rosco
    private static final String[] LETTERS = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
                                              "N", "Ñ", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
    
    // Estados de cada letra: "pending", "correct", "incorrect", "skipped"
    private String[] letterStates = new String[LETTERS.length];
    
    // Para rastrear las letras pendientes en cada ronda
    private java.util.Queue<Integer> pendingLetters = new java.util.LinkedList<>();

    public void setNavigator(ViewNavigator navigator) {
        this.navigator = navigator;
    }

    public void setPlayerConfig(GamePlayerConfig playerConfig) {
        this.playerConfig = playerConfig;
    }

    @FXML
    public void initialize() {
        // Iniciar el gameService
        gameService = new GameService();
        gameService.addListener(this);

        // Configurar el botón de inicio
        if (startButton != null) {
            startButton.setOnAction(event -> handleStartGame());
        }
        
        // Configurar el botón de pasar
        if (skipButton != null) {
            skipButton.setOnAction(event -> handlePasapalabra());
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
        }
        
        // Cargar preguntas desde JSON
        loadQuestions();
        
        // Inicializar estados de letras
        initializeLetterStates();
        
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
     * Recrear el rosco cuando cambia el tamaño
     */
    private void recreateRosco() {
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
        if (skipButton != null) {
            skipButton.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #5dade2; -fx-text-fill: #ecf0f1; -fx-border-radius: 65; -fx-background-radius: 65; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 3);");
        }
    }
    
    /**
     * Manejador para cuando se suelta el botón PASAR
     */
    @FXML
    private void handleSkipReleased() {
        if (skipButton != null) {
            skipButton.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #3498db; -fx-text-fill: white; -fx-border-radius: 65; -fx-background-radius: 65; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 3);");
        }
    }

    /**
     * Crear el rosco de letras en forma circular
     */
    private void createRosco() {
        if (roscoPane == null) {
            System.err.println("[ERROR] roscoPane es null - verifica que esté definido en el FXML con fx:id=\"roscoPane\"");
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
        
        // NO ocultar el rosco inicialmente - dejarlo visible pero sin contenido hasta START
        // roscoPane.setVisible(false);
        // roscoPane.setManaged(false);
    }

    /**
     * Handle start game button click
     */
    private void handleStartGame() {
        System.out.println("[DEBUG] handleStartGame() llamado");
        
        // Ocultar el botón de inicio
        if (startButton != null) {
            startButton.setVisible(false);
            startButton.setManaged(false);
            System.out.println("[DEBUG] Botón de inicio ocultado");
        }
        
        // Mostrar el rosco
        if (roscoPane != null) {
            roscoPane.setVisible(true);
            roscoPane.setManaged(true);
            System.out.println("[DEBUG] Rosco mostrado");
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
            System.out.println("[DEBUG] Área de preguntas mostrada");
        }
        
        // Mostrar el botón de pasapalabra
        if (skipButton != null) {
            skipButton.setVisible(true);
            skipButton.setManaged(true);
        }
        
        // Mostrar la primera pregunta pendiente
        System.out.println("[DEBUG] Llamando a showNextPendingQuestion()...");
        showNextPendingQuestion();
        
        // Publicar evento de inicio de juego
        gameService.publish(new GameStartedEvent(this.playerConfig));
        System.out.println("[DEBUG] handleStartGame() completado");
    }

    /**
     * Configurar dibujo de círculos en canvas
     */
    private void setupCanvasCircle(Canvas canvas) {
        if (canvas == null) return;
        // Canvas está preparado para futuros dibujos si es necesario
        // Por ahora solo se define el tamaño
        canvas.setWidth(400);
        canvas.setHeight(400);
    }
    
    /**
     * Cargar preguntas desde el archivo JSON
     */
    private void loadQuestions() {
        System.out.println("[DEBUG] Iniciando carga de preguntas...");
        try {
            QuestionFileLoader loader = new QuestionFileLoader();
            System.out.println("[DEBUG] QuestionFileLoader creado");
            QuestionList questionList = loader.loadQuestions();
            System.out.println("[DEBUG] QuestionList obtenido");
            questions = questionList.getQuestionList();
            System.out.println("[DEBUG] Cargadas " + questions.size() + " preguntas");
            
            // Verificar que tenemos preguntas
            if (questions != null && !questions.isEmpty()) {
                System.out.println("[DEBUG] Primera pregunta: " + questions.get(0).getQuestionText());
            } else {
                System.err.println("[ERROR] La lista de preguntas está vacía!");
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Error cargando preguntas: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ERROR] Error inesperado: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Configurar los eventos de los botones de opciones
     */
    private void setupOptionButtons() {
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
        for (int i = 0; i < LETTERS.length; i++) {
            letterStates[i] = "pending";
            pendingLetters.add(i);
        }
    }
    
    /**
     * Mostrar la siguiente pregunta pendiente
     */
    private void showNextPendingQuestion() {
        System.out.println("[DEBUG] showNextPendingQuestion() llamado");
        System.out.println("[DEBUG] questions == null? " + (questions == null));
        
        if (questions != null) {
            System.out.println("[DEBUG] questions.size(): " + questions.size());
        }
        
        if (questions == null || questions.isEmpty()) {
            System.err.println("[ERROR] No hay preguntas disponibles");
            if (questionLabel != null) {
                questionLabel.setText("No hay preguntas disponibles");
            }
            return;
        }
        
        // Si no hay más preguntas pendientes, terminar el juego
        if (pendingLetters.isEmpty()) {
            System.out.println("[INFO] Juego terminado - No hay más preguntas pendientes");
            gameService.publish(new GameFinishedEvent(null, null));
            return;
        }
        
        // Obtener el siguiente índice pendiente
        currentQuestionIndex = pendingLetters.peek();
        System.out.println("[DEBUG] currentQuestionIndex: " + currentQuestionIndex);
        
        if (currentQuestionIndex >= questions.size()) {
            System.err.println("[ERROR] Índice fuera de rango: " + currentQuestionIndex + " >= " + questions.size());
            return;
        }
        
        // Obtener la pregunta actual
        Question currentQuestion = questions.get(currentQuestionIndex);
        String currentLetter = LETTERS[currentQuestionIndex];
        
        System.out.println("[DEBUG] Mostrando pregunta para letra: " + currentLetter);
        System.out.println("[DEBUG] Texto pregunta: " + currentQuestion.getQuestionText());
        
        // Actualizar el estado visual de la letra actual (resaltar en azul)
        updateLetterState(currentLetter, "current");
        
        // Mostrar la pregunta
        String questionText = "Letra " + currentLetter + ": " + currentQuestion.getQuestionText();
        System.out.println("[DEBUG] Estableciendo texto en questionLabel: " + questionText);
        System.out.println("[DEBUG] questionLabel == null? " + (questionLabel == null));
        
        if (questionLabel != null) {
            questionLabel.setText(questionText);
            questionLabel.setWrapText(true);
            System.out.println("[DEBUG] Texto establecido correctamente: '" + questionLabel.getText() + "'");
        }
        
        // Mostrar las opciones en los labels
        List<String> options = currentQuestion.getQuestionResponsesList();
        System.out.println("[DEBUG] Número de opciones: " + options.size());
        System.out.println("[DEBUG] Opciones: " + options);
        
        if (optionALabel != null && options.size() > 0) {
            optionALabel.setText("A) " + options.get(0));
            System.out.println("[DEBUG] Opción A establecida: " + options.get(0));
        }
        if (optionBLabel != null && options.size() > 1) {
            optionBLabel.setText("B) " + options.get(1));
            System.out.println("[DEBUG] Opción B establecida: " + options.get(1));
        }
        if (optionCLabel != null && options.size() > 2) {
            optionCLabel.setText("C) " + options.get(2));
            System.out.println("[DEBUG] Opción C establecida: " + options.get(2));
        }
        if (optionDLabel != null && options.size() > 3) {
            optionDLabel.setText("D) " + options.get(3));
            System.out.println("[DEBUG] Opción D establecida: " + options.get(3));
        }
        
        // Habilitar botones
        enableButtons();
        System.out.println("[DEBUG] showNextPendingQuestion() completado");
    }
    
    /**
     * Habilitar todos los botones de respuesta
     */
    private void enableButtons() {
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
        if (questions == null || currentQuestionIndex >= questions.size()) {
            return;
        }
        
        Question currentQuestion = questions.get(currentQuestionIndex);
        String currentLetter = LETTERS[currentQuestionIndex];
        boolean isCorrect = currentQuestion.isCorrectIndex(selectedIndex);
        
        // Actualizar el estado de la letra
        letterStates[currentQuestionIndex] = isCorrect ? "correct" : "incorrect";
        
        // Quitar de la cola de pendientes
        pendingLetters.poll();
        
        // Actualizar el estado visual de la letra
        updateLetterState(currentLetter, isCorrect ? "correct" : "incorrect");
        
        // Deshabilitar botones temporalmente
        disableButtons();
        
        // Avanzar a la siguiente pregunta después de un breve delay
        new Thread(() -> {
            try {
                Thread.sleep(800); // 0.8 segundos de delay
                Platform.runLater(() -> {
                    showNextPendingQuestion();
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Manejar el botón Pasapalabra
     */
    private void handlePasapalabra() {
        if (pendingLetters.isEmpty()) {
            return;
        }
        
        String currentLetter = LETTERS[currentQuestionIndex];
        System.out.println("Pasapalabra en letra: " + currentLetter);
        
        // Marcar la letra como saltada (azul claro)
        updateLetterState(currentLetter, "skipped");
        
        // Mover la pregunta actual al final de la cola
        Integer skippedIndex = pendingLetters.poll();
        if (skippedIndex != null) {
            pendingLetters.add(skippedIndex);
        }
        
        // Mostrar la siguiente pregunta inmediatamente
        showNextPendingQuestion();
    }
    
    /**
     * Actualizar el color de una letra según su estado
     */
    public void updateLetterState(String letter, String state) {
        Button btn = letterButtons.get(letter.toUpperCase());
        if (btn != null) {
            Platform.runLater(() -> {
                // Remover todas las clases de estado previas
                btn.getStyleClass().removeAll("rosco-letter-pending", "rosco-letter-correct", 
                                              "rosco-letter-incorrect", "rosco-letter-current", "rosco-letter-skipped");
                
                // Agregar la clase correspondiente al nuevo estado
                switch (state.toLowerCase()) {
                    case "correct":
                        btn.getStyleClass().add("rosco-letter-correct");
                        break;
                    case "incorrect":
                        btn.getStyleClass().add("rosco-letter-incorrect");
                        break;
                    case "current":
                        btn.getStyleClass().add("rosco-letter-current");
                        break;
                    case "skipped":
                        btn.getStyleClass().add("rosco-letter-skipped");
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
        // Verificar el tipo de evento y llamar al método apropiado
        if (event instanceof TimerTickEvent) {
            int remaining = ((TimerTickEvent) event).getElapsedSeconds();
            Platform.runLater(() -> timerLabel.setText(String.valueOf(remaining)));
        } else if (event instanceof GameFinishedEvent) {
            // Navegar a la pantalla de resultados cuando termina el juego
            Platform.runLater(() -> {
                if (navigator != null) {
                    navigator.showResults();
                }
            });
        }
    }
}