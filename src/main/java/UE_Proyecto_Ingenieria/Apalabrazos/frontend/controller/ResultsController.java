package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventBus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventListener;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GameEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GameFinishedEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GameRecord;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Controller for displaying the top 5 leaderboard.
 */
public class ResultsController implements EventListener {
    
    @FXML
    private Button goToMenuButton;

    private ViewNavigator navigator;
    private EventBus eventBus;

    public void setNavigator(ViewNavigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        this.eventBus = EventBus.getInstance();
        // Registrarse como listener de eventos
        eventBus.addListener(this);
        
        // Configurar botón
        if (goToMenuButton != null) {
            goToMenuButton.setOnAction(event -> handleGoToMenu());
        }
    }
    
    private void handleGoToMenu() {
        if (navigator != null) {
            navigator.showMenu();
        }
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
     * Por ahora muestra datos de ejemplo
     * TODO: Implementar carga de datos reales desde el sistema de guardado
     */
    private void displayResults(GameRecord playerOne, GameRecord playerTwo) {
        // Cuando implementes el sistema de guardado, aquí cargarás los datos reales
        // y actualizarás los labels de la tabla dinámicamente
    }

    public void showResults() {
        // This method can be called externally if needed
        // Results are automatically displayed via events
    }
}