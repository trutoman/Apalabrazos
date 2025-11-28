package UE_Proyecto_Ingenieria.Apalabrazos;

import java.io.IOException;

import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.MenuController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.GameController;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller.ResultsController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Clase principal de la aplicación Apalabrazos.
 */
public class MainApp extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
    	ViewNavigator navigator = new ViewNavigator(primaryStage);
        navigator.showMenu();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
