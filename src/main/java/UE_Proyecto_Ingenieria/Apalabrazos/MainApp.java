package UE_Proyecto_Ingenieria.Apalabrazos;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
        GameSessionManager gameSessionManager = GameSessionManager.getInstance();
        log.info("GameSessionManager singleton initialized and ready");
    }
}