package UE_Proyecto_Ingenieria.Apalabrazos;

import java.io.IOException;

import UE_Proyecto_Ingenieria.Apalabrazos.ui.MenuController;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        primaryStage.setTitle("Pasapalabra 2D");
        showMenuView();
    }

    public void showMenuView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UE_Proyecto_Ingenieria/Apalabrazos/menu_view.fxml"));
            Parent root = loader.load();

            MenuController controller = loader.getController();
            controller.setMainApp(this);

            Scene scene = new Scene(root, 1024, 768);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showGameView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UE_Proyecto_Ingenieria/Apalabrazos/game_view.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1024, 768);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showResultsView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/UE_Proyecto_Ingenieria/Apalabrazos/results_view.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1024, 768);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
