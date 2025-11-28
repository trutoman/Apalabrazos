package UE_Proyecto_Ingenieria.Apalabrazos.frontend.controller;

import UE_Proyecto_Ingenieria.Apalabrazos.frontend.ViewNavigator;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controlador de la vista de resultados.
 */
public class ResultsController {

    @FXML
    private Label playerOneResultLabel;


    private ViewNavigator navigator;

    public void setNavigator(ViewNavigator navigator) {
        this.navigator = navigator;
    }

    public void showResults() {
        playerOneResultLabel.setText("X aciertos");
    }

}