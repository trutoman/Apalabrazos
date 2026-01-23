package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventBus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GlobalEventBus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventListener;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GameEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GameFinishedEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GameRecord;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller for displaying the final scores of the match.
 */
public class ResultsController implements EventListener {

    @FXML
    private Label playerOneResultLabel;

    @FXML
    private Label playerTwoResultLabel;

    private ViewNavigator navigator;
    private EventBus eventBus;

    public void setNavigator(ViewNavigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        this.eventBus = GlobalEventBus.getInstance();
        // Registrarse como listener de eventos
        eventBus.addListener(this);
    }

    /**
     * Recibir y procesar eventos
     */
    @Override
    public void onEvent(GameEvent event) {
        // Solo nos interesa el evento de juego terminado
        if (event instanceof GameFinishedEvent) {
            onGameFinished((GameFinishedEvent) event);
        }
    }

    /**
     * Handle game finished event
     */
    private void onGameFinished(GameFinishedEvent event) {
        Platform.runLater(() -> {
            displayResults(event.getPlayerOneRecord(), event.getPlayerTwoRecord());
        });
    }

    /**
     * Display the results
     */
    private void displayResults(GameRecord playerOne, GameRecord playerTwo) {
        if (playerOneResultLabel != null) {
            playerOneResultLabel.setText(formatResult(playerOne));
        }
        if (playerTwoResultLabel != null) {
            playerTwoResultLabel.setText(formatResult(playerTwo));
        }
    }

    /**
     * Format a game record for display
     */
    private String formatResult(GameRecord record) {
        return String.format("Aciertos: %d | Fallos: %d | Puntuación: %.1f%%",
            record.getCorrectAnswers(),
            record.getIncorrectAnswers(),
            record.getScorePercentage());
    }

    public void showResults() {
        // This method can be called externally if needed
        // Results are automatically displayed via events
    }
}