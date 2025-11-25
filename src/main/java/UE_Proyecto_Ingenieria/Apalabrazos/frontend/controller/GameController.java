package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller responsible for the gameplay view.
 * Listens to game events and updates the UI accordingly.
 */
public class GameController implements EventListener {

    @FXML
    private Label questionLabel;

    @FXML
    private Label timerLabel;

    // Utilizamos el event bus unico para suscribirnos y publicar eventos
    private EventBus eventBus;
    private ViewNavigator navigator;
    private int currentPlayerIndex;

    public void setNavigator(ViewNavigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        this.eventBus = EventBus.getInstance();
        // Registrarse como listener de eventos
        eventBus.addListener(this);
    }

    /**
     * Recibir y procesar eventos del juego
     */
    @Override
    public void onEvent(GameEvent event) {
        // Verificar el tipo de evento y llamar al método apropiado
        if (event instanceof QuestionChangedEvent) {
            onQuestionChanged((QuestionChangedEvent) event);
        } else if (event instanceof AnswerValidatedEvent) {
            onAnswerValidated((AnswerValidatedEvent) event);
        } else if (event instanceof TimerTickEvent) {
            onTimerTick((TimerTickEvent) event);
        } else if (event instanceof TurnEndedEvent) {
            onTurnEnded((TurnEndedEvent) event);
        } else if (event instanceof GameFinishedEvent) {
            onGameFinished((GameFinishedEvent) event);
        }
    }

    /**
     * Handle question changed event
     */
    private void onQuestionChanged(QuestionChangedEvent event) {
        Platform.runLater(() -> {
            currentPlayerIndex = event.getPlayerIndex();
            if (questionLabel != null) {
                questionLabel.setText("Letra: " + event.getLetter() +
                                    " - Pregunta " + (event.getQuestionIndex() + 1));
            }
        });
    }

    /**
     * Handle answer validated event
     */
    private void onAnswerValidated(AnswerValidatedEvent event) {
        Platform.runLater(() -> {
            String message = event.isCorrect()
                ? "¡Correcto!"
                : "Incorrecto. La respuesta era: " + event.getCorrectAnswer();
            // TODO: Show feedback to user (toast, dialog, etc.)
            System.out.println(message);
        });
    }

    /**
     * Handle timer tick event
     */
    private void onTimerTick(TimerTickEvent event) {
        if (event.getPlayerIndex() == currentPlayerIndex) {
            Platform.runLater(() -> {
                if (timerLabel != null) {
                    int minutes = event.getRemainingSeconds() / 60;
                    int seconds = event.getRemainingSeconds() % 60;
                    timerLabel.setText(String.format("%02d:%02d", minutes, seconds));
                }
            });
        }
    }

    /**
     * Handle turn ended event
     */
    private void onTurnEnded(TurnEndedEvent event) {
        Platform.runLater(() -> {
            String message = String.format("Turno finalizado. Aciertos: %d/%d",
                event.getCorrectAnswers(), event.getTotalQuestions());
            System.out.println(message);
            // TODO: Show turn summary
        });
    }

    /**
     * Handle game finished event
     */
    private void onGameFinished(GameFinishedEvent event) {
        Platform.runLater(() -> {
            // Navigate to results screen
            if (navigator != null) {
                navigator.showResults();
            }
        });
    }

    /**
     * Called when user submits an answer
     */
    public void submitAnswer(String answer) {
        eventBus.publish(new AnswerSubmittedEvent(currentPlayerIndex, 'a', answer));
    }
}