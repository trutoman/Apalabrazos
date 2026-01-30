package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.tools.QuestionFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;

/**
 * Service that manages the game logic and publishes events.
 * This is where the business logic lives - it listens to user events
 * and publishes state change events.
 */
public class GameService implements EventListener {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final EventBus eventBus;
    private final EventBus externalBus;
    private GameGlobal GlobalGameInstance;

    // Controla que el evento de inicio para el controlador se publique una sola vez
    private boolean creatorInitEventSent = false;

    // Listeners separados para evitar rebotes entre buses
    private final EventListener globalListener = this::onGlobalEvent;
    private final EventListener externalListener = this::onExternalEvent;


    private TimeService timeService;
    private String gameSessionId; // UUID único para la partida

    public GameService() {
        this.GlobalGameInstance = new GameGlobal();
        this.eventBus = GlobalEventBus.getInstance();
        this.externalBus = new EventBus();
        this.gameSessionId = generateGameSessionId();
        // Registrarse con listeners separados (evita rebotes entre buses)
        eventBus.addListener(globalListener);
        externalBus.addListener(externalListener);
    }

    public GameService(GamePlayerConfig playerConfig) {
        // Configurar la instancia global del juego para multijugador
        this.GlobalGameInstance = new GameGlobal(playerConfig);
        this.eventBus = GlobalEventBus.getInstance();
        this.externalBus = new EventBus();
        this.gameSessionId = generateGameSessionId();
        // Registrarse con listeners separados (evita rebotes entre buses)
        eventBus.addListener(globalListener);
        externalBus.addListener(externalListener);
    }

    /**
     * Add a listener to the external bus (e.g., GameController)
     */
    public void addListener(EventListener listener) {
        externalBus.addListener(listener);
    }

    /**
     * Remove a listener from the external bus
     */
    public void removeListener(EventListener listener) {
        externalBus.removeListener(listener);
    }

    /**
     * Called when GameSessionManager validates that the game start request is valid
     * This method will be invoked only after validating that the requester is the creator
     */
    public void GameStartedValid() {
        // Transicionar a START_VALIDATED en la máquina de estados
        GlobalGameInstance.transitionStartValidated();
        checkAndInitialize();
    }

    /**
     * Initialize a new game - starts the timer and changes state to PLAYING
     */
    public void initGame() {
        // Inicializar y arrancar el TimeService
        if (this.timeService == null) {
            this.timeService = new TimeService();
        }
        this.timeService.start();

        // Cambiar el estado del GameGlobal a PLAYING
        if (this.GlobalGameInstance != null) {
            this.GlobalGameInstance.setState(GameGlobal.GameGlobalState.PLAYING);
        }

        // Cargar preguntas para todos y publicar la primera
        loadQuestionsForAllPlayers();
        publishQuestionForAllPlayers(-1, QuestionStatus.INIT);

        log.info("Juego iniciado. TimeService iniciado");
    }

    /**
     * Carga las preguntas y las asigna a cada instancia de jugador
     */
    private void loadQuestionsForAllPlayers() {
        try {
            QuestionFileLoader loader = new QuestionFileLoader();
            int numberOfQuestions = GlobalGameInstance.getNumberOfQuestions();

            // Cargar y limitar la lista de preguntas
            QuestionList questionList = loader.loadQuestions(numberOfQuestions);

            // Asignar a cada instancia de jugador
            for (GameInstance instance : GlobalGameInstance.getAllPlayerInstances()) {
                instance.setQuestionList(questionList);
                instance.start();
            }
        } catch (IOException e) {
            log.error("Error al cargar preguntas: {}", e.getMessage(), e);
        }
    }

    /**
     * Publica la pregunta indicada por índice para todos los jugadores
     */
    private void publishQuestionForAllPlayers(int questionIndex, QuestionStatus status) {
        for (String playerId : GlobalGameInstance.getAllPlayerIds()) {
            GameInstance instance = GlobalGameInstance.getPlayerInstance(playerId);
            if (instance != null) {
                QuestionList list = instance.getQuestionList();
                Question currentQuestion = null;
                if (list != null && questionIndex >= -1 && questionIndex < list.getCurrentLength()) {
                    currentQuestion = list.getQuestionAt(questionIndex);
                }
                publishQuestionForPlayer(playerId, questionIndex, status, currentQuestion);
            }
        }
    }

    /**
     * Publica un QuestionChangedEvent para un jugador y pregunta concretos
     */
    public void publishQuestionForPlayer(String playerId, int questionIndex, QuestionStatus status) {
        publishQuestionForPlayer(playerId, questionIndex, status, null);
    }

    /**
     * Publica un QuestionChangedEvent para un jugador y pregunta concretos, incluyendo la siguiente pregunta
     */
    public void publishQuestionForPlayer(String playerId, int questionIndex, QuestionStatus status, Question nextQuestion) {
        GameInstance instance = GlobalGameInstance.getPlayerInstance(playerId);
        if (instance == null) {
            log.warn("No GameInstance for player {}", playerId);
            return;
        }

        QuestionChangedEvent event = new QuestionChangedEvent(questionIndex, status, playerId, nextQuestion);
        log.info("Publicando Pregunta {} para jugador {} (nextQuestion: {})", questionIndex, playerId,
            nextQuestion != null ? "sí" : "no");
        externalBus.publish(event);
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
            log.warn("addPlayerToGame: playerId inválido");
            return false;
        }

        GameGlobal global = this.GlobalGameInstance;
        if (global == null) {
            log.error("addPlayerToGame: GlobalGameInstance es null");
            return false;
        }

        if (global.hasPlayer(playerId)) {
            log.info("addPlayerToGame: jugador ya en la partida: {}", playerId);
            return false;
        }

        if (global.getPlayerCount() >= global.getMaxPlayers()) {
            log.warn("addPlayerToGame: partida llena ({}/{})", global.getPlayerCount(), global.getMaxPlayers());
            return false;
        }

        // Extraer el nombre del jugador del playerId (formato: nombre-xxxx)
        String playerName = playerId.contains("-") ? playerId.substring(0, playerId.lastIndexOf("-")) : playerId;

        // Crear Player y asignarle manualmente el playerId recibido para mantener consistencia
        Player player = new Player(playerName, playerName);
        player.setPlayerID(playerId);  // Establecer el ID exacto recibido

        GameInstance instance = new GameInstance(
            global.getGameDuration(),
            player,
            global.getDifficulty(),
            global.getNumberOfQuestions(),
            global.getGameType()
        );

        // Insertar la GameInstance en el mapa playerInstances del GameGlobal usando el playerId original
        global.addPlayerInstance(playerId, instance);
        log.info("Jugador agregado: {} (nombre: {}, total: {})", playerId, playerName, global.getPlayerCount());
        return true;
    }

    /**
     * Publicar un evento hacia los listeners del GameService (como GameController)
     * mediante el bus externo
     *
     * @param event El evento a publicar
     */
    public void publishExternal(GameEvent event) {
        externalBus.publish(event);
    }

    /**
     * Get the external EventBus instance for this GameService.
     * Controllers can use this bus to publish events and receive game updates.
     *
     * @return EventBus instance for external communication
     */
    public EventBus getExternalBus() {
        return externalBus;
    }

    /**
     * Verifica si ambas condiciones se cumplen (ControllerReady + StartValidated)
     * Si sí, invoca initGame()
     */
    private void checkAndInitialize() {
        if (GlobalGameInstance.isGameInitialized()) {
            log.info("Ambas condiciones cumplidas (Controller + Start Validation) - notificando al GameController");
            if (!creatorInitEventSent) {
                externalBus.publish(new CreatorInitGameEvent());
                creatorInitEventSent = true;
            }
            initGame();
        }
    }

    /**
     * Compatibilidad: si alguien llama a publish() se enruta como evento global.
     */
    @Override
    public void onEvent(GameEvent event) {
        onGlobalEvent(event);
    }

    // Maneja eventos del bus global (GameSessionManager, TimeService, etc.)
    private void onGlobalEvent(GameEvent event) {
        if (event instanceof PlayerJoinedEvent) {
            PlayerJoinedEvent join = (PlayerJoinedEvent) event;
            addPlayerToGame(join.getPlayerID());
        } else if (event instanceof TimerTickEvent) {
            // Reenviar al bus externo para que el GameController actualice UI
            log.debug("Reenviando TimerTickEvent al externalBus: {} segundos", ((TimerTickEvent)event).getElapsedSeconds());
            publishExternal(event);
        } else if (event instanceof GameControllerReady) {
            GameControllerReady ready = (GameControllerReady) event;
            log.info("GameControllerReady received from playerId: {}", ready.getPlayerId());
            GlobalGameInstance.transitionControllerReady();
            checkAndInitialize();
        }
    }

    // Maneja eventos que vienen del bus externo (publicados por GameController)
    private void onExternalEvent(GameEvent event) {
        if (event instanceof GameControllerReady) {
            GameControllerReady ready = (GameControllerReady) event;
            log.info("GameControllerReady received from playerId: {} (external bus)", ready.getPlayerId());
            GlobalGameInstance.transitionControllerReady();
            checkAndInitialize();
        } else if (event instanceof TimerTickEvent) {
            // No reenviar TimerTickEvent al mismo bus para evitar bucles
            return;
        } else if (event instanceof AnswerSubmittedEvent) {
            AnswerSubmittedEvent answerEvent = (AnswerSubmittedEvent) event;
            handleAnswerSubmitted(answerEvent);
        }
    }

    /**
     * Procesar la respuesta enviada por un jugador
     */
    private void handleAnswerSubmitted(AnswerSubmittedEvent event) {
        String playerId = event.getPlayerId();
        int questionIndex = event.getQuestionIndex();
        int selectedOption = event.getSelectedOption();

        log.info("Procesando respuesta - PlayerId: {}, QuestionIndex: {}, SelectedOption: {}",
                 playerId, questionIndex, selectedOption);

        // Obtener la instancia del jugador
        GameInstance playerInstance = GlobalGameInstance.getPlayerInstance(playerId);
        if (playerInstance == null) {
            log.warn("No se encontró GameInstance para el jugador: {}", playerId);
            return;
        }

        // Actualizar índice actual de la pregunta en la instancia del jugador
        playerInstance.setNextCurrentQuestionIndex(questionIndex);

        // Obtener la pregunta
        QuestionList questionList = playerInstance.getQuestionList();
        if (questionList == null || questionIndex < 0 || questionIndex >= questionList.getCurrentLength()) {
            log.warn("Índice de pregunta inválido: {} para jugador: {}", questionIndex, playerId);
            return;
        }

        Question question = questionList.getQuestionAt(questionIndex);
        boolean isCorrect = question.isCorrectIndex(selectedOption);

        log.info("Respuesta {} para jugador {} en pregunta {} (opción {})",
                 isCorrect ? "CORRECTA" : "INCORRECTA", playerId, questionIndex, selectedOption);

        // Calcular la siguiente pregunta (si existe)
        Question nextQuestion = null;
        int nextQuestionIndex = questionIndex + 1;
        if (nextQuestionIndex < questionList.getCurrentLength()) {
            nextQuestion = questionList.getQuestionAt(nextQuestionIndex);
        }

        // Publicar evento de validación de respuesta con la siguiente pregunta
        QuestionStatus newStatus = isCorrect ? QuestionStatus.RESPONDED_OK : QuestionStatus.RESPONDED_FAIL;
        publishQuestionForPlayer(playerId, questionIndex, newStatus, nextQuestion);
    }

}
