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
    private GameInstance singleGameInstance;
    private TimeService timeService;
    private List<EventListener> listeners;
    private String gameSessionId; // UUID único para la partida

    public GameService() {
        this.singleGameInstance = new GameInstance();
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
        // Se asume que la lista de jugadores ya está poblada en GameInstance (n jugadores)
        // Cargar preguntas desde el archivo y preparar estado inicial común
        try {
            QuestionFileLoader loader = new QuestionFileLoader();
            QuestionList questions = loader.loadQuestions(event.getGamePlayerConfig().getQuestionNumber());
            this.singleGameInstance.setQuestionList(questions);
            this.singleGameInstance.setTimeCounter(event.getGamePlayerConfig().getTimerSeconds());
            this.singleGameInstance.setCurrentQuestionIndex(0);
            this.singleGameInstance.setDifficulty(event.getGamePlayerConfig().getDifficultyLevel());
            this.singleGameInstance.setGameType(event.getGamePlayerConfig().getGameType());
            // No añadimos jugadores aquí: ya vienen preparados en GameInstance
        } catch (IOException e) {
            System.err.println("Error cargando preguntas: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Notificar la primera pregunta a todos los jugadores registrados
        notifyQuestionChanged();

        // Iniciar servicio de tiempo compartido para la partida
        timeService = new TimeService();
        timeService.start();
    }

    /**
     * Notify that the current question has changed for a player
     */
    private void notifyQuestionChanged() {
        int questionIndex = singleGameInstance.getCurrentQuestionIndex();
        QuestionList questionList = singleGameInstance.getQuestionList();

        if (questionList == null) {
            System.err.println("[WARN] notifyQuestionChanged: questionList es null");
            return;
        }

        if (questionIndex < questionList.getCurrentLength()) {
            Question currentQuestion = questionList.getQuestionList().get(questionIndex);
            QuestionStatus status = QuestionStatus.INIT;

            // Enviar un evento por cada jugador con su playerId para que cada controlador filtre
            for (Player p : singleGameInstance.getPlayers()) {
                String playerId = (p != null) ? p.getPlayerID() : null;
                QuestionChangedEvent event = new QuestionChangedEvent(questionIndex, status, currentQuestion, playerId);
                gameBusPublish(event);
            }
        }
    }

    /**
     * Get the current game state
     */
    public GameInstance getGameInstance() {
        return singleGameInstance;
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
            handlePlayerJoined((PlayerJoinedEvent) event);
        } else if (event instanceof TimerTickEvent) {
            this.singleGameInstance.decrementTimeCounter(1);
            TimerTickEvent controllerTickEvent = new TimerTickEvent(this.singleGameInstance.getTimeCounter());
            gameBusPublish(controllerTickEvent);
        }
    }

    /**
     * Procesar la solicitud de unión de un jugador.
     */
    private void handlePlayerJoined(PlayerJoinedEvent event) {
        Player incoming = event.getPlayer();
        if (incoming == null) {
            return;
        }
        String incomingId = incoming.getPlayerID();

        boolean alreadyPresent = this.singleGameInstance.getPlayers().stream()
                .anyMatch(p -> p != null && p.getPlayerID().equals(incomingId));
        if (!alreadyPresent) {
            this.singleGameInstance.addPlayer(incoming);
        }
    }

}
