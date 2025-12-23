package UE_Proyecto_Ingenieria.Apalabrazos.frontend;

import java.io.IOException;
import UE_Proyecto_Ingenieria.Apalabrazos.MainApp;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.MenuController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.GameController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.ResultsController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.LobbyController;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.PlayerJoinedEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Utility class that orchestrates switching between main application views.
 */
public class ViewNavigator {

    private final Stage stage;
    private static final int SCENE_WIDTH = 1280;
    private static final int SCENE_HEIGHT = 550;

    public ViewNavigator(Stage stage) {
        this.stage = stage;
        this.stage.setTitle("Apalabrazos 2D");
        // Permitir que la ventana se ajuste al contenido
        this.stage.setResizable(true);
        this.stage.sizeToScene();
    }


    // Función de entrada de la aplicación para mostrar el menú principal
    public void showMenu() {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/UE_Proyecto_Ingenieria/Apalabrazos/view/menu.fxml"));
            Parent root = loader.load();
            MenuController controller = loader.getController();
            controller.setNavigator(this);
            Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la vista del menú", e);
        }
    }

    public void showGame(GamePlayerConfig playerOneConfig, GameService gameService) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/UE_Proyecto_Ingenieria/Apalabrazos/view/game.fxml"));
            Parent root = loader.load();
            GameController controller = loader.getController();
            controller.setNavigator(this);
            controller.setGameService(gameService);
            controller.setPlayerConfig(playerOneConfig);
            Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la vista del juego", e);
        }
    }

    public void showResults() {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/UE_Proyecto_Ingenieria/Apalabrazos/view/results.fxml"));
            Parent root = loader.load();
            ResultsController controller = loader.getController();
            controller.setNavigator(this);

            Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la vista de resultados", e);
        }
    }

    // Mostrar la sala de espera (matchmaking lobby)
    public void showLobby() {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/UE_Proyecto_Ingenieria/Apalabrazos/view/lobby.fxml"));
            Parent root = loader.load();
            LobbyController controller = loader.getController();
            controller.setNavigator(this);
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.sizeToScene();
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la vista de matchmaking", e);
        }
    }
}