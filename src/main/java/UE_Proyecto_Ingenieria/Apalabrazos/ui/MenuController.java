package UE_Proyecto_Ingenieria.Apalabrazos.ui;


import UE_Proyecto_Ingenieria.Apalabrazos.MainApp;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class MenuController {

	private MainApp mainApp;
	
    @FXML
    private TextField txtPlayer1;

    @FXML
    private TextField txtPlayer2;

    @FXML
    private Button btnStart;

    @FXML
    private Button btnExit;

    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
    }

    @FXML
    private void initialize() {
        // Inicialización extra si hace falta
    }

    @FXML
    private void handleStartGame() {
        String p1 = txtPlayer1.getText();
        String p2 = txtPlayer2.getText();

        if (p1 == null || p1.trim().isEmpty()) {
            p1 = "Jugador 1";
        }
        if (p2 == null || p2.trim().isEmpty()) {
            p2 = "Jugador 2";
        }

    }

    @FXML
    private void handleExit() {
        javafx.application.Platform.exit();
    }
}
