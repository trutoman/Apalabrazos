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

    // Número de preguntas disponibles por cada letra en el JSON
    // De momento 3, porque en el JSON que te paso hay 3 por letra.
    // Si más adelante metes 10 por letra en el JSON, solo cambia este valor a 10.
    private static final int QUESTIONS_PER_LETTER = 3;

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

            // 1) Cargamos TODO el banco de preguntas del JSON
            QuestionList allQuestions = loader.loadQuestions();

            // 2) Construimos el rosco: 1 pregunta aleatoria por letra
            int requested = event.getGamePlayerConfig().getQuestionNumber();
            QuestionList roscoQuestions = buildRoscoQuestions(allQuestions, requested);

            singleGameInstance.setQuestionList(roscoQuestions);
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
            Question currentQuestion = questionList.getQuestionList().get(questionIndex);
            // Tener en cuenta que las preguntas dan vueltas y puede ser que tengamos que
            // gestionar un estado distinto en las segundas vueltas
            QuestionStatus status = QuestionStatus.INIT;
            QuestionChangedEvent questionChangedEvent =
                    new QuestionChangedEvent(questionIndex, status, currentQuestion);
            gameBusPublish(questionChangedEvent);
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
        } else if (event instanceof TimerTickEvent) {
            this.singleGameInstance.decrementTimeCounter(1);
            TimerTickEvent controllerTickEvent = new TimerTickEvent(this.singleGameInstance.getTimeCounter());
            gameBusPublish(controllerTickEvent);
        }
    }
    /**
     * Construye la lista de preguntas del rosco: una pregunta aleatoria por letra.
     *
     * Convención:
     *  - El JSON contiene todas las preguntas ordenadas por letras.
     *  - Para cada letra hay QUESTIONS_PER_LETTER preguntas seguidas.
     *  - A: índices   0 .. (QUESTIONS_PER_LETTER-1)
     *  - B: índices   QUESTIONS_PER_LETTER .. (2*QUESTIONS_PER_LETTER -1)
     *  - ...
     *
     * @param allQuestions Banco completo de preguntas cargado del JSON.
     * @param questionNumber Número de preguntas que se quieren usar (por ejemplo 27).
     * @return QuestionList con una pregunta por letra.
     */
    private QuestionList buildRoscoQuestions(QuestionList allQuestions, int questionNumber) {
        int totalLetters = AlphabetMap.MAP.size(); // Normalmente 27 (incluye ñ)
        // No permitimos más preguntas que letras disponibles
        int roscoSize = Math.min(questionNumber, totalLetters);

        int available = allQuestions.getCurrentLength();
        int needed = roscoSize * QUESTIONS_PER_LETTER;
        if (available < needed) {
            throw new IllegalStateException(
                    "No hay suficientes preguntas en el JSON. Necesarias: "
                            + needed + ", disponibles: " + available);
        }

        List<Question> source = allQuestions.getQuestionList();
        List<Question> selected = new ArrayList<>(roscoSize);
        Random random = new Random();

        for (int letterIndex = 0; letterIndex < roscoSize; letterIndex++) {
            int start = letterIndex * QUESTIONS_PER_LETTER;
            int end = start + QUESTIONS_PER_LETTER; // exclusivo

            // Sublista de preguntas correspondientes a esa letra
            List<Question> pool = source.subList(start, end);
            Question chosen = pool.get(random.nextInt(pool.size()));
            selected.add(chosen);
        }

        // max_length lo ponemos al tamaño real del rosco
        return new QuestionList(selected, roscoSize);
    }

}
