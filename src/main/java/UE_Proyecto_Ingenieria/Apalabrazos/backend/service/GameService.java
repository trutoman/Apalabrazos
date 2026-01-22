package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.tools.QuestionFileLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Service that manages the game logic and publishes events.
 * This is where the business logic lives - it listens to user events
 * and publishes state change events.
 */
public class GameService implements EventListener {

    private final EventBus eventBus;
    private GameGlobal GlobalGameInstance;

    private TimeService timeService;
    private List<EventListener> listeners;
    private String gameSessionId; // UUID único para la partida

    public GameService() {
        this.GlobalGameInstance = new GameGlobal();
        this.eventBus = EventBus.getInstance();
        // Yo tambien tengo mis listeners, el gamecontroller
        this.listeners = new ArrayList<>();
        this.gameSessionId = generateGameSessionId();
        // Registrarse como listener de eventos
        eventBus.addListener(this);
    }

    public GameService(GamePlayerConfig playerConfig) {
        // Configurar la instancia global del juego para multijugador
        this.GlobalGameInstance = new GameGlobal(playerConfig);
        this.eventBus = EventBus.getInstance();
        // Yo tambien tengo mis listeners, el gamecontroller
        this.listeners = new ArrayList<>();
        this.gameSessionId = generateGameSessionId();
        // Registrarse como listener de eventos
        eventBus.addListener(this);
    }

    /**
     * Añadir un listener que escuchará eventos desde este servicio
     */
    public void addListener(EventListener listener) {
        listeners.add(listener);
    }

    /**
     * Eliminar un listener (útil al cerrar vistas)
     */
    public void removeListener(EventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Publicar un evento al servicio (no al bus directamente)
     * El GameController invoca este método para enviar eventos al servicio
     * que los procesará localmente
     */
    public void publish(GameEvent event) {
        // Procesar el evento localmente en lugar de publicarlo al bus
        onEvent(event);
    }

    /**
     * Initialize a new game
     */
    private void handleGameStarted(GameStartedEvent event) {
        // En multijugador, cada jugador tiene su propia GameInstance
        // Aquí simplemente registramos que el juego comenzó
        // Las preguntas y configuración ya están en GameGlobal
        System.out.println("[GameService] Juego iniciado: " + event.getGamePlayerConfig().getPlayer().getName());
    }

    /**
     * Notify that the current question has changed for a player
     */
    private void notifyQuestionChanged() {
        // En multijugador, cada instancia del juego notifica sus propios cambios
        System.out.println("[GameService] Cambio de pregunta notificado");
    }

    /**
     * Get the current global game state
     */
    public GameGlobal getGameInstance() {
        return GlobalGameInstance;
    }

    /**
     * Exponer segundos transcurridos (si el servicio está activo)
     */
    public int getElapsedSeconds() {
        return timeService != null ? timeService.getElapsedSeconds() : 0;
    }

    /**
     * Obtener el ID único de esta sesión de juego
     * @return String con 16 caracteres alfanuméricos
     */
    public String getGameSessionId() {
        return gameSessionId;
    }

    /**
     * Generar un UUID único para la partida: 16 caracteres alfanuméricos
     * @return String con 16 caracteres (mayúsculas, minúsculas y dígitos)
     */
    private String generateGameSessionId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sessionId = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            sessionId.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sessionId.toString();
    }

    /**
     * Agregar un jugador a la partida asociada a este servicio.
     * Valida duplicados y capacidad máxima, y prepara su GameInstance con
     * la cantidad de preguntas configurada en el GameGlobal.
     *
     * @param playerId ID único del jugador
     * @return true si el jugador fue agregado; false si ya existía o no hay capacidad
     */
    public boolean addPlayerToGame(String playerId) {
        if (playerId == null || playerId.isEmpty()) {
            System.err.println("[GameService] addPlayerToGame: playerId inválido");
            return false;
        }

        GameGlobal global = this.GlobalGameInstance;
        if (global == null) {
            System.err.println("[GameService] addPlayerToGame: GlobalGameInstance es null");
            return false;
        }

        if (global.hasPlayer(playerId)) {
            System.out.println("[GameService] addPlayerToGame: jugador ya en la partida: " + playerId);
            return false;
        }

        if (global.getPlayerCount() >= global.getMaxPlayers()) {
            System.out.println("[GameService] addPlayerToGame: partida llena (" + global.getPlayerCount() + "/" + global.getMaxPlayers() + ")");
            return false;
        }

        GameInstance instance = new GameInstance();
            //instance.initializeQuestions(global.getNumberOfQuestions(), global.getDifficulty());
        global.addPlayerInstance(playerId, instance);
        System.out.println("[GameService] Jugador agregado: " + playerId + " (total: " + global.getPlayerCount() + ")");
        return true;
    }

    /**
     * Publicar un evento hacia los listeners del GameService (como GameController)
     * Este método centraliza la comunicación del servicio hacia las vistas
     *
     * @param event El evento a publicar a los listeners
     */
    private void gameBusPublish(GameEvent event) {
        // Notificar a todos los listeners registrados
        for (EventListener listener : this.listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                System.err.println("[ERROR] gameBusPublish: Error al notificar listener con evento "
                    + event.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Recibir y procesar eventos
     */
    @Override
    public void onEvent(GameEvent event) {
        // Verificar el tipo de evento y llamar al método apropiado
        if (event instanceof GameStartedEvent) {
            handleGameStarted((GameStartedEvent) event);
        } else if (event instanceof PlayerJoinedEvent) {
            PlayerJoinedEvent join = (PlayerJoinedEvent) event;
            addPlayerToGame(join.getPlayerID());
        }
    }

}
