package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.tools.QuestionFileLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that manages the game logic and publishes events.
 * This is where the business logic lives - it listens to user events
 * and publishes state change events.
 */
public class GameService implements EventListener {

    private final EventBus eventBus;
    private GameSingleInstance singleGameInstance;
    private TimeService timeService;
    private List<EventListener> listeners;

    public GameService() {
        this.singleGameInstance = new GameSingleInstance();
        this.eventBus = EventBus.getInstance();
        // Yo tambien tengo mis listeners, el gamecontroller
        this.listeners = new ArrayList<>();
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
     * Recibir y procesar eventos
     */
    @Override
    public void onEvent(GameEvent event) {
        // Verificar el tipo de evento y llamar al método apropiado
        if (event instanceof GameStartedEvent) {
            handleGameStarted((GameStartedEvent) event);
        } else if (event instanceof TimerTickEvent) {
            this.singleGameInstance.decrementTimeCounter(1);
            TimerTickEvent controllerTickEvent = new TimerTickEvent( this.singleGameInstance.getTimeCounter());
            for (EventListener l : listeners) {
                l.onEvent(controllerTickEvent);
            }
        }
    }

    /**
     * Initialize a new game
     */
    private void handleGameStarted(GameStartedEvent event) {

        // Create players from event
        singleGameInstance.setPlayer(event.getGamePlayerConfig().getPlayer());
        singleGameInstance.setTimeCounter(event.getGamePlayerConfig().getTimerSeconds());
        singleGameInstance.setCurrentQuestionIndex(0);
        singleGameInstance.setDifficulty(event.getGamePlayerConfig().getDifficultyLevel());
        // Cargar preguntas desde el archivo
        try {
            QuestionFileLoader loader = new QuestionFileLoader();
            QuestionList questions = loader.loadQuestions(event.getGamePlayerConfig().getQuestionNumber());
            singleGameInstance.setQuestionList(questions);
        } catch (IOException e) {
            System.err.println("Error cargando preguntas: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        // Iniciar servicio de tiempo
        timeService = new TimeService();
        timeService.start();

        // Notify that first question is ready
        notifyQuestionChanged();
    }

    /**
     * Notify that the current question has changed for a player
     */
    private void notifyQuestionChanged() {
        int questionIndex = singleGameInstance.getCurrentQuestionIndex();
        QuestionList questionList = singleGameInstance.getQuestionList();

        if (questionIndex < questionList.getCurrentLength()) {
            // Get the letter from AlphabetMap using the question index
            String letterStr = AlphabetMap.getLetter(questionIndex);
            char letter = letterStr.charAt(0);

            eventBus.publish(new QuestionChangedEvent(
                0,  // Single player is always index 0
                questionIndex,
                letter
            ));
        }
    }

    /**
     * Get the current game state
     */
    public GameSingleInstance getGameInstance() {
        return singleGameInstance;
    }

    /**
     * Exponer segundos transcurridos (si el servicio está activo)
     */
    public int getElapsedSeconds() {
        return timeService != null ? timeService.getElapsedSeconds() : 0;
    }
}
