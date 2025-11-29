package UE_Proyecto_Ingenieria.Apalabrazos;

import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
    	ViewNavigator navigator = new ViewNavigator(primaryStage);
        navigator.showMenu();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
