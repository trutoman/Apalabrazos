package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameService;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
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
    private Label timerLabel;

    @FXML
    private Pane roscoPane;

    @FXML
    private Button startButton;

    @FXML
    private VBox questionArea;
    
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

        // Crear el rosco con botones circulares
        createRosco();
        
        // Cargar preguntas desde JSON
        loadQuestions();
        
        // Inicializar estados de letras
        initializeLetterStates();
        
        // Configurar eventos de los botones de opciones
        setupOptionButtons();
    }

    /**
     * Crear el rosco de letras en forma circular
     */
    private void createRosco() {
        int numLetters = LETTERS.length;
        double centerX = 200; // Centro del rosco
        double centerY = 200;
        double radius = 180; // Radio del círculo
        double buttonSize = 42; // Tamaño de cada botón
        
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
        
        // Ocultar el rosco inicialmente
        roscoPane.setVisible(false);
        roscoPane.setManaged(false);
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
        
        // Mostrar la zona de preguntas
        if (questionArea != null) {
            questionArea.setVisible(true);
            questionArea.setManaged(true);
            System.out.println("[DEBUG] Área de preguntas mostrada");
        }
        
        // Mostrar las áreas de botones
        if (leftButtonsArea != null) {
            leftButtonsArea.setVisible(true);
            leftButtonsArea.setManaged(true);
        }
        if (rightButtonsArea != null) {
            rightButtonsArea.setVisible(true);
            rightButtonsArea.setManaged(true);
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
        
        // Mostrar las opciones
        List<String> options = currentQuestion.getQuestionResponsesList();
        System.out.println("[DEBUG] Número de opciones: " + options.size());
        System.out.println("[DEBUG] Opciones: " + options);
        
        System.out.println("[DEBUG] option1Button == null? " + (option1Button == null));
        System.out.println("[DEBUG] option2Button == null? " + (option2Button == null));
        System.out.println("[DEBUG] option3Button == null? " + (option3Button == null));
        System.out.println("[DEBUG] option4Button == null? " + (option4Button == null));
        
        if (option1Button != null && options.size() > 0) {
            String text1 = options.get(0);
            option1Button.setText(text1);
            option1Button.setWrapText(true);
            System.out.println("[DEBUG] Opción 1 establecida: " + text1);
        }
        if (option2Button != null && options.size() > 1) {
            String text2 = options.get(1);
            option2Button.setText(text2);
            option2Button.setWrapText(true);
            System.out.println("[DEBUG] Opción 2 establecida: " + text2);
        }
        if (option3Button != null && options.size() > 2) {
            String text3 = options.get(2);
            option3Button.setText(text3);
            option3Button.setWrapText(true);
            System.out.println("[DEBUG] Opción 3 establecida: " + text3);
        }
        if (option4Button != null && options.size() > 3) {
            String text4 = options.get(3);
            option4Button.setText(text4);
            option4Button.setWrapText(true);
            System.out.println("[DEBUG] Opción 4 establecida: " + text4);
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
        
        // Restaurar el color según el estado actual de la letra
        String currentState = letterStates[currentQuestionIndex];
        updateLetterState(currentLetter, currentState);
        
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
                                              "rosco-letter-incorrect", "rosco-letter-current");
                
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