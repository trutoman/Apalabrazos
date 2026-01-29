package UE_Proyecto_Ingenieria.Apalabrazos.frontend;

import java.io.IOException;
import UE_Proyecto_Ingenieria.Apalabrazos.MainApp;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.MenuController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.GameController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.ResultsController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.LobbyController;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.PlayerJoinedEvent;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.EventBus;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.GamePlayerConfig;
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
    private static final int SCENE_HEIGHT = 720;

    // public ViewNavigator(Stage stage) {
    //     this.stage = stage;
    //     this.stage.setTitle("Apalabrazos 2D");
    //     // Permitir que la ventana se ajuste al contenido
    //     this.stage.setResizable(true);
    //     this.stage.sizeToScene();
    // }

    public ViewNavigator(Stage stage) {
        this.stage = stage;
        this.stage.setTitle("Apalabrazos 2D");
        // Tamaño inicial
        this.stage.sizeToScene();
        this.stage.setMinWidth(800);
        this.stage.setMinHeight(600);
        this.stage.setResizable(true);
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

    public void showGame(GamePlayerConfig playerOneConfig, EventBus externalBus) {
        try {
            System.out.println("[ViewNavigator] showGame llamado con externalBus: " + externalBus);
            System.out.println("[ViewNavigator] Cargando FXML...");
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/UE_Proyecto_Ingenieria/Apalabrazos/view/game.fxml"));
            System.out.println("[ViewNavigator] Haciendo loader.load()...");
            Parent root = loader.load();
            System.out.println("[ViewNavigator] FXML cargado, obteniendo controller...");
            GameController controller = loader.getController();
            System.out.println("[ViewNavigator] Controller obtenido: " + controller);
            System.out.println("[ViewNavigator] Llamando setNavigator...");
            controller.setNavigator(this);
            System.out.println("[ViewNavigator] Llamando setPlayerConfig...");
            controller.setPlayerConfig(playerOneConfig);
            System.out.println("[ViewNavigator] Llamando setExternalBus con: " + externalBus);
            controller.setExternalBus(externalBus);
            System.out.println("[ViewNavigator] setExternalBus completado");
            System.out.println("[ViewNavigator] Llamando postInitialize...");
            controller.postInitialize();  // Llamar después de todas las dependencias
            System.out.println("[ViewNavigator] Creando Scene...");
            Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
            stage.setScene(scene);
            stage.show();
            System.out.println("[ViewNavigator] showGame completado exitosamente");
        } catch (IOException e) {
            System.err.println("[ViewNavigator ERROR] IOException: " + e.getMessage());
            e.printStackTrace();
            throw new IllegalStateException("No se pudo cargar la vista del juego", e);
        } catch (Exception e) {
            System.err.println("[ViewNavigator ERROR] Exception: " + e.getMessage());
            e.printStackTrace();
            throw e;
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
            // Resetear tamaños fijos previos
            stage.setWidth(Double.NaN);
            stage.setHeight(Double.NaN);
            stage.sizeToScene();
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la vista de matchmaking", e);
        }
    }
}