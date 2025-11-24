package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventBus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.GameStartedEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

/**
 * Controller for the initial menu where player names are entered.
 */
public class MenuController {

    @FXML
    private TextField playerOneField;

    @FXML
    private TextField playerTwoField;

    @FXML
    private Button startButton;

    private ViewNavigator navigator;
    private EventBus eventBus;

    public void setNavigator(ViewNavigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        this.eventBus = EventBus.getInstance();
        startButton.setOnAction(event -> handleStartGame());
    }

    private void handleStartGame() {
        String playerOne = playerOneField.getText() == null || playerOneField.getText().trim().isEmpty()
                ? "Jugador 1"
                : playerOneField.getText().trim();
        String playerTwo = playerTwoField.getText() == null || playerTwoField.getText().trim().isEmpty()
                ? "Jugador 2"
                : playerTwoField.getText().trim();

        // Publish event instead of calling navigator directly
        eventBus.publish(new GameStartedEvent(playerOne, playerTwo));

        // Navigate to game view
        navigator.startGame(playerOne, playerTwo);
    }
}