package UE_Proyecto_Ingenieria.Apalabrazos.frontend;

import java.io.IOException;
import UE_Proyecto_Ingenieria.Apalabrazos.MainApp;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.MenuController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.GameController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.ResultsController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Gestiona la navegación entre las vistas de la aplicación.
 */
public class ViewNavigator {

    private final Stage stage;

    public ViewNavigator(Stage stage) {
        this.stage = stage;
        this.stage.setTitle("Apalabrazos 2D");
    }

    public void showMenu() {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/UE_Proyecto_Ingenieria/Apalabrazos/view/menu.fxml"));
            Parent root = loader.load();
            MenuController controller = loader.getController();
            controller.setNavigator(this);
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la vista del menú", e);
        }
    }

    public void startGame(String playerOneName, String playerTwoName) {
        showGame();
    }

    public void showGame() {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/UE_Proyecto_Ingenieria/Apalabrazos/view/game.fxml"));
            Parent root = loader.load();
            GameController controller = loader.getController();
            Scene scene = new Scene(root);
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
            
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la vista de resultados", e);
        }
    }
}