package UE_Proyecto_Ingenieria.Apalabrazos;

import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameSessionManager;
import javafx.application.Application;
import javafx.stage.Stage;

public class MainApp extends Application {

    private GameSessionManager gameSessionManager;

    @Override
    public void start(Stage primaryStage) {
        // Initialize GameSessionManager to handle multiplayer sessions
        gameSessionManager = new GameSessionManager();
        System.out.println("[MainApp] GameSessionManager initialized");

    	ViewNavigator navigator = new ViewNavigator(primaryStage);
        navigator.showMenu();
    }

    public static void main(String[] args) {
        launch(args);
    }
}