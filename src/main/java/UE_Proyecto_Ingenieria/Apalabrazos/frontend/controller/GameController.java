package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

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
    private Canvas playerOneCanvas;

    @FXML
    private Canvas rivalCanvas;

    @FXML
    private Button startButton;

    @FXML
    private VBox questionArea;

    // Utilizamos el event bus unico para suscribirnos y publicar eventos
    private EventBus eventBus;
    private ViewNavigator navigator;
    private int currentPlayerIndex = 0;
    private GamePlayerConfig playerConfig;

    // El gameController contiene e instancia el gameservice ... discutible
    private GameService gameService;

    public void setNavigator(ViewNavigator navigator) {
        this.navigator = navigator;
    }

    public void setPlayerConfig(GamePlayerConfig playerConfig) {
        this.playerConfig = playerConfig;
    }

    @FXML
    public void initialize() {
        // Iniciiamos el gameService
        gameService = new GameService();
        gameService.addListener(this);

        // Configurar el botón de inicio
        if (startButton != null) {
            startButton.setOnAction(event -> handleStartGame());
        }

        // Dibujar círculos centrados en los canvas (y redibujar en cambios de tamaño)
        setupCanvasCircle(playerOneCanvas);
        setupCanvasCircle(rivalCanvas);
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
        // Publicar evento de inicio de juego
        gameService.publish(new GameStartedEvent(this.playerConfig));
    }

    private void setupCanvasCircle(Canvas canvas) {
        if (canvas == null) return;
        Runnable draw = () -> drawCenteredCircle(canvas);
        // Dibujo inicial
        draw.run();
        // Redibujar si cambia el tamaño
        canvas.widthProperty().addListener((obs, o, n) -> draw.run());
        canvas.heightProperty().addListener((obs, o, n) -> draw.run());
    }

    private void drawCenteredCircle(Canvas canvas) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // Avoid drawing (and creating RT textures) with non-positive sizes
        if (w <= 0 || h <= 0) {
            return;
        }
        double d = Math.min(w, h); // diámetro máximo que cabe
        double x = (w - d) / 2.0;
        double y = (h - d) / 2.0;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        // Limpiar
        gc.clearRect(0, 0, w, h);
        // Estilo del círculo
        gc.setStroke(Color.web("#3498db"));
        gc.setLineWidth(4.0);
        // Dibujar contorno del círculo ocupando todo el canvas
        gc.strokeOval(x, y, d, d);
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
        }
    }
}