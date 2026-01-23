package UE_Proyecto_Ingenieria.Apalabrazos;

import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Application;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    private GameSessionManager gameSessionManager;

    @Override
    public void start(Stage primaryStage) {
        // Initialize GameSessionManager to handle multiplayer sessions
        gameSessionManager = new GameSessionManager();
        log.info("GameSessionManager initialized");

    	ViewNavigator navigator = new ViewNavigator(primaryStage);
        navigator.showMenu();
    }

    public static void main(String[] args) {
        launch(args);
    }
}