package UE_Proyecto_Ingenieria.Apalabrazos.frontend;

import java.io.IOException;
import UE_Proyecto_Ingenieria.Apalabrazos.MainApp;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.MenuController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.GameController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.ResultsController;
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

    // Esta función se llama desde el controlador de menu cuando se pulsa singleplayer button
    public void startGame(GamePlayerConfig playerOneConfig) {
        GameService gameService = new GameService();

        // Agregar el jugador al GameInstance del servicio
        if (playerOneConfig != null && playerOneConfig.getPlayer() != null) {
            gameService.getGameInstance().addPlayer(playerOneConfig.getPlayer());
        }
        
        showGame(playerOneConfig);
    }

    public void showGame(GamePlayerConfig playerOneConfig) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/UE_Proyecto_Ingenieria/Apalabrazos/view/game.fxml"));
            Parent root = loader.load();
            GameController controller = loader.getController();
            controller.setNavigator(this);
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
}