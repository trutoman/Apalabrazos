package Apalabrazos.backend.service;

import Apalabrazos.backend.events.*;
import Apalabrazos.backend.model.*;
import Apalabrazos.backend.tools.QuestionFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service that manages the game logic and publishes events.
 * This is where the business logic lives - it listens to user events
 * and publishes state change events.
 */
public class GameService implements EventListener {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    private static final int BASE_QUESTION_SCORE = 100;
    private static final int SCORE_PENALTY_PER_PASS = 10;

    private final AsyncEventBus eventBus;
    private final AsyncEventBus externalBus;
    private GameGlobal GlobalGameInstance;

    // Controla que el evento de inicio para el controlador se publique una sola vez
    private boolean creatorInitEventSent = false;

    // Timeout de espera de GameControllerReady de todos los jugadores
    private final ScheduledExecutorService controllerReadyScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> controllerReadyTimeout;

    // Listeners separados para evitar rebotes entre buses
    private final EventListener globalListener = this::onGlobalEvent;
    private final EventListener externalListener = this::onExternalEvent;


    private TimeService timeService;
    private String matchId; // UUID único para la partida
    private String creatorPlayerId; // ID del jugador que creó esta partida
    private String gameName; // Nombre de la partida

    public GameService() {
        this.GlobalGameInstance = new GameGlobal();
        this.eventBus = GlobalAsyncEventBus.getInstance();
        this.externalBus = new AsyncEventBus();
        this.matchId = generateMatchId();
        // Registrarse con listeners separados (evita rebotes entre buses)
        eventBus.addListener(globalListener);
        externalBus.addListener(externalListener);
    }

    public GameService(GamePlayerConfig playerConfig) {
        // Configurar la instancia global del juego para multijugador
        this.GlobalGameInstance = new GameGlobal(playerConfig);
        this.eventBus = GlobalAsyncEventBus.getInstance();
        this.externalBus = new AsyncEventBus();
        this.matchId = generateMatchId();
        // Registrarse con listeners separados (evita rebotes entre buses)
        eventBus.addListener(globalListener);
        externalBus.addListener(externalListener);
    }

    /**
     * Set the ID of the player who created this game session
     *
     * @param creatorId The player ID of the creator
     */
    public void setCreatorPlayerId(String creatorId) {
        this.creatorPlayerId = creatorId;
    }

    /**
     * Get the ID of the player who created this game session
     *
     * @return The player ID of the creator, or null if not set
     */
    public String getCreatorPlayerId() {
        return creatorPlayerId;
    }

    /**
     * Set the name of this game session
     *
     * @param name The game name
     */
    public void setGameName(String name) {
        this.gameName = name;
    }

    /**
     * Get the name of this game session
     *
     * @return The game name, or null if not set
     */
    public String getGameName() {
        return gameName;
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
        // Arrancar el timeout: si no todos confirman en N segundos, cancelar la partida
        int timeoutSecs = GlobalGameInstance.getControllerReadyTimeoutSeconds();
        controllerReadyTimeout = controllerReadyScheduler.schedule(() -> {
            if (!GlobalGameInstance.isGameInitialized()
                    && GlobalGameInstance.getState() != GameGlobal.GameGlobalState.PLAYING) {
                log.warn("Timeout ({} s) esperando GameControllerReady de todos los jugadores. Cancelando partida {}.", timeoutSecs, matchId);
                cancelGameDueToTimeout();
            }
        }, timeoutSecs, TimeUnit.SECONDS);
        checkAndInitialize();
    }

    private void cancelGameDueToTimeout() {
        GlobalGameInstance.setState(GameGlobal.GameGlobalState.POST);
        externalBus.publish(new GameFinishedEvent(null, null));
        log.info("Partida {} cancelada por timeout de GameControllerReady.", matchId);
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

        // Cargar preguntas para todos y publicar la primera (letra A)
        loadQuestionsForAllPlayers();
        publishQuestionForAllPlayers(0, QuestionStatus.INIT);

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
            QuestionList baseQuestionList = loader.loadQuestions(numberOfQuestions);

            // Asignar a cada instancia de jugador
            for (GameInstance instance : GlobalGameInstance.getAllPlayerInstances()) {
                // Cada jugador debe tener su propia copia para que su progreso sea independiente.
                instance.setQuestionList(cloneQuestionList(baseQuestionList));
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

        // Calcular totales de aciertos y fallos usando método de GameInstance
        int[] totals = instance.getCorrectIncorrectTotals();
        int totalCorrect = totals[0];
        int totalIncorrect = totals[1];

        QuestionChangedEvent event = new QuestionChangedEvent(questionIndex, status, playerId, nextQuestion, totalCorrect, totalIncorrect);
        log.info("Dando Resultado anterior y Publicando Pregunta {} para jugador {} (nextQuestion: {}, correct: {}, incorrect: {})",
            questionIndex, playerId, nextQuestion != null ? "sí" : "no", totalCorrect, totalIncorrect);
        if (nextQuestion == null) {
            log.warn("[QUESTION-PUBLISH] nextQuestion is null for playerId={}, questionIndex={}, status={}",
                    playerId, questionIndex, status);
        } else {
            int responsesCount = nextQuestion.getQuestionResponsesList() != null
                    ? nextQuestion.getQuestionResponsesList().size()
                    : 0;
            log.info("[QUESTION-PUBLISH] playerId={}, questionIndex={}, status={}, question='{}', responsesCount={}",
                    playerId,
                    questionIndex,
                    status,
                    nextQuestion.getQuestionText(),
                    responsesCount);
        }
        externalBus.publish(event);
    }

    /**
     * Get the current global game state
     */
    public GameGlobal getGameInstance() {
        return GlobalGameInstance;
    }

    /**
     * Obtener segundos restantes en la partida
     */
    public int getRemainingSeconds() {
        return GlobalGameInstance != null ? GlobalGameInstance.getRemainingSeconds() : 0;
    }

    /**
     * Obtener el ID único de esta sesión de juego
     * @return String con 16 caracteres alfanuméricos
     */
    public String getMatchId() {
        return matchId;
    }

    /**
     * Generar un UUID único para la partida: 16 caracteres alfanuméricos
     * @return String con 16 caracteres (mayúsculas, minúsculas y dígitos)
     */
    private String generateMatchId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder matchId = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            matchId.append(chars.charAt(random.nextInt(chars.length())));
        }
        return matchId.toString();
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
        return addPlayerToGame(playerId, null);
    }

    /**
     * Agregar un jugador a la partida asociada a este servicio usando, si existe,
     * el nombre explícito recibido en el flujo de join.
     */
    public boolean addPlayerToGame(String playerId, String explicitPlayerName) {
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

        String playerName = (explicitPlayerName != null && !explicitPlayerName.isBlank())
                ? explicitPlayerName.trim()
                : (playerId.contains("-") ? playerId.substring(0, playerId.lastIndexOf("-")) : playerId);

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
     * Publicar un evento y esperar a que los listeners externos lo procesen.
     * Se usa en flujos donde el orden observable por el cliente importa.
     */
    public void publishExternalAndWait(GameEvent event) {
        externalBus.publishAndWait(event);
    }

    /**
     * Get the external AsyncEventBus instance for this GameService.
     * Controllers can use this bus to publish events and receive game updates.
     *
     * @return AsyncEventBus instance for external communication
     */
    public AsyncEventBus getExternalBus() {
        return externalBus;
    }

    /**
     * Verifica si ambas condiciones se cumplen (ControllerReady + StartValidated)
     * Si sí, invoca initGame()
     */
    private void checkAndInitialize() {
        if (GlobalGameInstance.isGameInitialized()) {
            log.info("Ambas condiciones cumplidas (Controller + Start Validation) - notificando al GameController");
            // Cancelar el timeout ya que todos los jugadores confirmaron a tiempo
            if (controllerReadyTimeout != null && !controllerReadyTimeout.isDone()) {
                controllerReadyTimeout.cancel(false);
                log.info("Timeout de GameControllerReady cancelado para partida {}.", matchId);
            }
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
            addPlayerToGame(join.getPlayerID(), join.getPlayerName());
        } else if (event instanceof TimerTickEvent) {
            handleTimerTick((TimerTickEvent) event);
        } else if (event instanceof GameControllerReady) {
            GameControllerReady ready = (GameControllerReady) event;
            log.info("GameControllerReady received from playerId: {}", ready.getPlayerId());
            GlobalGameInstance.transitionControllerReady(ready.getPlayerId());
            checkAndInitialize();
        }
    }

    // Maneja eventos que vienen del bus externo (publicados por GameController)
    private void onExternalEvent(GameEvent event) {
        if (event instanceof GameControllerReady) {
            GameControllerReady ready = (GameControllerReady) event;
            log.info("GameControllerReady received from playerId: {} (external bus)", ready.getPlayerId());
            GlobalGameInstance.transitionControllerReady(ready.getPlayerId());
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
     * Manejar el tick del timer: decrementar tiempo y verificar si se agotó
     */
    private void handleTimerTick(TimerTickEvent event) {
        if (GlobalGameInstance != null && GlobalGameInstance.getState() == GameGlobal.GameGlobalState.PLAYING) {
            GlobalGameInstance.decrementTime();
            int remaining = GlobalGameInstance.getRemainingSeconds();

            // Publicar evento actualizado con tiempo restante
            log.debug("Tiempo restante: {} segundos", remaining);
            publishExternal(new TimerTickEvent(remaining));

            // Si el tiempo se agotó, finalizar juego
            if (GlobalGameInstance.isTimeUp()) {
                log.info("Tiempo agotado. Finalizando juego...");
                finishGame();
            }
        }
    }

    /**
     * Finalizar el juego
     */
    private void finishGame() {
        if (GlobalGameInstance != null) {
            GlobalGameInstance.setState(GameGlobal.GameGlobalState.POST);
        }
        if (timeService != null) {
            timeService.stop();
        }

        // Obtener los GameRecords de los jugadores (si existen)
        GameRecord playerOneRecord = null;
        GameRecord playerTwoRecord = null;

        if (GlobalGameInstance != null) {
            java.util.List<GameInstance> instances = new java.util.ArrayList<>(GlobalGameInstance.getAllPlayerInstances());
            if (instances.size() > 0) {
                playerOneRecord = buildFinalRecord(instances.get(0));
            }
            if (instances.size() > 1) {
                playerTwoRecord = buildFinalRecord(instances.get(1));
            }
        }

        publishExternal(new GameFinishedEvent(playerOneRecord, playerTwoRecord));
        log.info("Juego finalizado");
    }

    private GameRecord buildFinalRecord(GameInstance instance) {
        if (instance == null) {
            return null;
        }

        GameRecord record = instance.getGameResult();
        if (record == null) {
            record = new GameRecord();
            instance.setGameResult(record);
        }

        int[] totals = instance.getCorrectIncorrectTotals();
        int correctAnswers = totals[0];
        int incorrectAnswers = totals[1];

        int passedQuestions = 0;
        QuestionList questionList = instance.getQuestionList();
        if (questionList != null) {
            for (int i = 0; i < questionList.getCurrentLength(); i++) {
                Question q = questionList.getQuestionAt(i);
                if (q != null) {
                    passedQuestions += q.getPassedCount();
                }
            }
        }

        int totalTime = 0;
        if (GlobalGameInstance != null) {
            totalTime = Math.max(0, GlobalGameInstance.getGameDuration() - GlobalGameInstance.getRemainingSeconds());
        }

        record.setCorrectAnswers(correctAnswers);
        record.setIncorrectAnswers(incorrectAnswers);
        record.setPassedQuestions(passedQuestions);
        record.setTotalTime(totalTime);
        record.setScore(instance.getTotalScore());

        return record;
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

        // Obtener la pregunta
        QuestionList questionList = playerInstance.getQuestionList();
        if (questionList == null || questionIndex < 0 || questionIndex >= questionList.getCurrentLength()) {
            log.warn("Índice de pregunta inválido: {} para jugador: {}", questionIndex, playerId);
            return;
        }

        Question question = questionList.getQuestionAt(questionIndex);

        QuestionStatus newStatus = QuestionStatus.RESPONDED_FAIL;

        if (selectedOption == -1) {
            newStatus = QuestionStatus.PASSED;
            question.incrementPassedCount();
            log.info("Jugador {} pasó la pregunta {}", playerId, questionIndex);
        } else {
            boolean isCorrect = question.isCorrectIndex(selectedOption);
            log.info("Respuesta {} para jugador {} en pregunta {} (opción {})",
                 isCorrect ? "CORRECTA" : "INCORRECTA", playerId, questionIndex, selectedOption);
            // Publicar evento de validación de respuesta con la siguiente pregunta
            newStatus = isCorrect ? QuestionStatus.RESPONDED_OK : QuestionStatus.RESPONDED_FAIL;
        }

        // Registrar estado: PASSED conserva la pregunta como no respondida (INIT) para el recorrido circular.
        if (newStatus == QuestionStatus.PASSED) {
            question.setQuestionStatus(QuestionStatus.INIT);
            question.setUserResponseRecorded(QuestionStatus.INIT.getValue());
        } else {
            question.setQuestionStatus(newStatus);
            question.setUserResponseRecorded(newStatus.getValue());
        }
        int questionScore = calculateAnswerScore(question, newStatus);
        playerInstance.addToTotalScore(questionScore);
        int totalScore = playerInstance.getTotalScore();

        int[] totals = playerInstance.getCorrectIncorrectTotals();
        int totalCorrect = totals[0];
        int totalIncorrect = totals[1];

        String selectedAnswer = null;
        if (selectedOption >= 0 && selectedOption < question.getQuestionResponsesList().size()) {
            selectedAnswer = question.getQuestionResponsesList().get(selectedOption);
        }

        String questionLetter = question.getQuestionLetter();
        String correctAnswer = question.getCorrectResponse();

        publishExternalAndWait(new AnswerValidatedEvent(
            playerId,
            questionIndex,
            questionLetter,
            selectedAnswer,
            newStatus,
            correctAnswer,
            questionScore,
            totalScore,
            totalCorrect,
            totalIncorrect));

        // Siguiente pregunta circular: buscar siempre la siguiente NO respondida.
        int nextQuestionIndex = findNextUnansweredIndexCircular(questionList, questionIndex);
        Question nextQuestion = nextQuestionIndex >= 0 ? questionList.getQuestionAt(nextQuestionIndex) : null;
        int publishQuestionIndex = nextQuestionIndex >= 0 ? nextQuestionIndex : questionIndex;
        playerInstance.setNextCurrentQuestionIndex(publishQuestionIndex);

        QuestionStatus nextQuestionStatus = nextQuestion != null ? QuestionStatus.INIT : null;
        publishQuestionForPlayer(playerId, publishQuestionIndex, nextQuestionStatus, nextQuestion);
    }

    private QuestionList cloneQuestionList(QuestionList source) {
        QuestionList clone = new QuestionList();
        if (source == null) {
            return clone;
        }

        for (int i = 0; i < source.getCurrentLength(); i++) {
            Question q = source.getQuestionAt(i);
            if (q == null) {
                continue;
            }
            Question copy = new Question(
                q.getQuestionText(),
                q.getQuestionResponsesList(),
                q.getCorrectQuestionIndex(),
                QuestionStatus.INIT,
                q.getQuestionLevel(),
                q.getQuestionLetter(),
                QuestionStatus.INIT.getValue());
            clone.addQuestion(copy);
        }
        return clone;
    }

    /**
     * Busca la siguiente pregunta no respondida en una lista circular.
     * Una pregunta se considera respondida si su estado es RESPONDED_OK o RESPONDED_FAIL.
     * PASSED se almacena como INIT, por lo tanto sigue siendo candidata.
     *
     * @return índice de la siguiente no respondida, o -1 si no quedan.
     */
    private int findNextUnansweredIndexCircular(QuestionList questionList, int currentIndex) {
        if (questionList == null) {
            return -1;
        }

        int size = questionList.getCurrentLength();
        if (size <= 0) {
            return -1;
        }

        int normalizedCurrent = currentIndex;
        if (normalizedCurrent < 0 || normalizedCurrent >= size) {
            normalizedCurrent = 0;
        }

        for (int step = 1; step <= size; step++) {
            int candidateIndex = (normalizedCurrent + step) % size;
            Question candidate = questionList.getQuestionAt(candidateIndex);
            if (candidate == null) {
                continue;
            }
            String response = candidate.getUserResponseRecorded();
            boolean answered = "responsed_ok".equals(response) || "responsed_fail".equals(response);
            if (!answered) {
                return candidateIndex;
            }
        }

        return -1;
    }

    /**
     * Calcula la puntuacion de la respuesta actual para una pregunta concreta.
     * RESPONDED_OK: 100 - (10 * passedCount), acotado a 0.
     * RESPONDED_FAIL/PASSED: 0.
     */
    private int calculateAnswerScore(Question question, QuestionStatus status) {
        if (question == null || status != QuestionStatus.RESPONDED_OK) {
            return 0;
        }

        int score = BASE_QUESTION_SCORE - (SCORE_PENALTY_PER_PASS * question.getPassedCount());
        return Math.max(0, score);
    }

}
