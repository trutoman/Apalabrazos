package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.tools.QuestionFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GameService modernizado con procesamiento asíncrono de eventos.
 *
 * CAMBIOS PRINCIPALES:
 * 1. EventBus → AsyncEventBus
 * 2. TimeService → ModernTimeService
 * 3. Todos los handlers ejecutan en Virtual Threads
 * 4. Métodos críticos retornan CompletableFuture
 * 5. Thread-safe con estructuras concurrentes
 *
 * BENEFICIOS:
 * - onEvent() no bloquea (cada evento en su propio virtual thread)
 * - Múltiples eventos se procesan en paralelo
 * - Mejor performance bajo carga alta
 * - Escalable a miles de jugadores simultáneos
 */
public class AsyncGameService implements EventListener {

    private static final Logger log = LoggerFactory.getLogger(AsyncGameService.class);

    private final AsyncEventBus globalBus;      // Bus global compartido
    private final AsyncEventBus externalBus;    // Bus para UI (GameController)
    private final ExecutorService virtualThreads;
    private GameGlobal globalGameInstance;
    private ModernTimeService timeService;
    private String gameSessionId;
    private boolean creatorInitEventSent = false;

    // ============================================================
    // CONSTRUCTORES
    // ============================================================

    public AsyncGameService() {
        this.globalGameInstance = new GameGlobal();
        this.globalBus = GlobalAsyncEventBus.getInstance();
        this.externalBus = new AsyncEventBus();
        this.virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
        this.gameSessionId = generateGameSessionId();

        // Registrarse a los buses
        globalBus.addListener(this);
        externalBus.addListener(this);

        log.info("AsyncGameService creado con sessionId: {}", gameSessionId);
    }

    public AsyncGameService(GamePlayerConfig playerConfig) {
        this.globalGameInstance = new GameGlobal(playerConfig);
        this.globalBus = GlobalAsyncEventBus.getInstance();
        this.externalBus = new AsyncEventBus();
        this.virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
        this.gameSessionId = generateGameSessionId();

        globalBus.addListener(this);
        externalBus.addListener(this);

        log.info("AsyncGameService creado para multijugador con sessionId: {}", gameSessionId);
    }

    // ============================================================
    // EVENT HANDLING (ASÍNCRONO)
    // ============================================================

    /**
     * Handler principal de eventos - EJECUTA EN VIRTUAL THREAD AUTOMÁTICAMENTE
     *
     * Como AsyncEventBus ejecuta cada listener en su propio virtual thread,
     * este método puede hacer operaciones costosas sin bloquear otros listeners.
     */
    @Override
    public void onEvent(GameEvent event) {
        // Pattern matching for switch (Java 21 feature)
        switch (event) {
            case TimerTickEvent tick -> handleTimerTickAsync(tick);
            case AnswerSubmittedEvent answer -> handleAnswerSubmittedAsync(answer);
            case GameControllerReady ready -> handleControllerReadyAsync(ready);
            case PlayerJoinedEvent joined -> handlePlayerJoinedAsync(joined);
            default -> log.debug("Evento no manejado: {}", event.getClass().getSimpleName());
        }
    }

    /**
     * Manejar tick del timer de forma asíncrona
     */
    private void handleTimerTickAsync(TimerTickEvent event) {
        CompletableFuture.runAsync(() -> {
            if (globalGameInstance == null ||
                globalGameInstance.getState() != GameGlobal.GameGlobalState.PLAYING) {
                return;
            }

            globalGameInstance.decrementTime();
            int remaining = globalGameInstance.getRemainingSeconds();

            log.debug("Tiempo restante: {} segundos", remaining);

            // Publicar evento actualizado (async, no bloquea)
            externalBus.publishAndForget(new TimerTickEvent(remaining));

            // Si el tiempo se agotó, finalizar juego
            if (globalGameInstance.isTimeUp()) {
                log.info("Tiempo agotado. Finalizando juego...");
                finishGameAsync();
            }
        }, virtualThreads)
        .exceptionally(ex -> {
            log.error("Error manejando TimerTickEvent: {}", ex.getMessage(), ex);
            return null;
        });
    }

    /**
     * Procesar respuesta de jugador de forma asíncrona
     */
    private void handleAnswerSubmittedAsync(AnswerSubmittedEvent event) {
        CompletableFuture.runAsync(() -> {
            String playerId = event.getPlayerId();
            int questionIndex = event.getQuestionIndex();
            int selectedOption = event.getSelectedOption();

            log.info("Procesando respuesta - PlayerId: {}, QuestionIndex: {}, SelectedOption: {}",
                     playerId, questionIndex, selectedOption);

            GameInstance playerInstance = globalGameInstance.getPlayerInstance(playerId);
            if (playerInstance == null) {
                log.warn("No se encontró GameInstance para el jugador: {}", playerId);
                return;
            }

            playerInstance.setNextCurrentQuestionIndex(questionIndex);

            QuestionList questionList = playerInstance.getQuestionList();
            if (questionList == null || questionIndex < 0 ||
                questionIndex >= questionList.getCurrentLength()) {
                log.warn("Índice de pregunta inválido: {} para jugador: {}", questionIndex, playerId);
                return;
            }

            Question question = questionList.getQuestionAt(questionIndex);
            QuestionStatus newStatus;

            if (selectedOption == -1) {
                newStatus = QuestionStatus.PASSED;
                log.info("Jugador {} pasó la pregunta {}", playerId, questionIndex);
            } else {
                boolean isCorrect = question.isCorrectIndex(selectedOption);
                newStatus = isCorrect ? QuestionStatus.RESPONDED_OK : QuestionStatus.RESPONDED_FAIL;
                log.info("Respuesta {} para jugador {} en pregunta {}",
                         isCorrect ? "CORRECTA" : "INCORRECTA", playerId, questionIndex);
            }

            question.setUserResponseRecorded(newStatus.getValue());

            // Calcular siguiente pregunta
            Question nextQuestion = null;
            int nextQuestionIndex = questionIndex + 1;
            if (nextQuestionIndex < questionList.getCurrentLength()) {
                nextQuestion = questionList.getQuestionAt(nextQuestionIndex);
            }

            publishQuestionForPlayer(playerId, questionIndex, newStatus, nextQuestion);
        }, virtualThreads)
        .exceptionally(ex -> {
            log.error("Error procesando respuesta: {}", ex.getMessage(), ex);
            return null;
        });
    }

    /**
     * Manejar GameControllerReady de forma asíncrona
     */
    private void handleControllerReadyAsync(GameControllerReady event) {
        CompletableFuture.runAsync(() -> {
            log.info("GameControllerReady recibido de playerId: {}", event.getPlayerId());
            globalGameInstance.transitionControllerReady();
            checkAndInitialize();
        }, virtualThreads);
    }

    /**
     * Manejar PlayerJoinedEvent de forma asíncrona
     */
    private void handlePlayerJoinedAsync(PlayerJoinedEvent event) {
        CompletableFuture.runAsync(() -> {
            addPlayerToGame(event.getPlayerID());
        }, virtualThreads);
    }

    // ============================================================
    // GAME LIFECYCLE (ASÍNCRONO)
    // ============================================================

    /**
     * Inicializar juego de forma asíncrona
     * Retorna CompletableFuture para que el llamador pueda esperar si lo necesita
     */
    public CompletableFuture<Void> initGameAsync() {
        return CompletableFuture.runAsync(() -> {
            log.info("Iniciando juego asíncronamente...");

            // Iniciar TimeService
            if (this.timeService == null) {
                this.timeService = new ModernTimeService(externalBus);
            }
            this.timeService.start();

            // Cambiar estado
            if (this.globalGameInstance != null) {
                this.globalGameInstance.setState(GameGlobal.GameGlobalState.PLAYING);
            }

            // Cargar preguntas y publicar primera pregunta
            loadQuestionsForAllPlayers();
            publishQuestionForAllPlayers(-1, QuestionStatus.INIT);

            log.info("Juego iniciado. ModernTimeService ejecutándose");
        }, virtualThreads);
    }

    /**
     * Finalizar juego de forma asíncrona
     */
    private CompletableFuture<Void> finishGameAsync() {
        return CompletableFuture.runAsync(() -> {
            if (globalGameInstance != null) {
                globalGameInstance.setState(GameGlobal.GameGlobalState.POST);
            }

            if (timeService != null) {
                timeService.shutdown();
            }

            // Obtener GameRecords
            GameRecord playerOneRecord = null;
            GameRecord playerTwoRecord = null;

            if (globalGameInstance != null) {
                var instances = new java.util.ArrayList<>(globalGameInstance.getAllPlayerInstances());
                if (!instances.isEmpty()) {
                    playerOneRecord = instances.get(0).getGameResult();
                }
                if (instances.size() > 1) {
                    playerTwoRecord = instances.get(1).getGameResult();
                }
            }

            // Publicar evento de fin de juego
            externalBus.publishAndForget(new GameFinishedEvent(playerOneRecord, playerTwoRecord));
            log.info("Juego finalizado");
        }, virtualThreads);
    }

    /**
     * Verificar si se puede inicializar el juego
     */
    private void checkAndInitialize() {
        if (globalGameInstance.isGameInitialized()) {
            log.info("Condiciones cumplidas (Controller + Start Validation)");
            if (!creatorInitEventSent) {
                externalBus.publishAndForget(new CreatorInitGameEvent());
                creatorInitEventSent = true;
            }
            initGameAsync(); // No bloqueante
        }
    }

    // ============================================================
    // MÉTODOS AUXILIARES (sin cambios significativos)
    // ============================================================

    public void GameStartedValid() {
        globalGameInstance.transitionStartValidated();
        checkAndInitialize();
    }

    private void loadQuestionsForAllPlayers() {
        try {
            QuestionFileLoader loader = new QuestionFileLoader();
            int numberOfQuestions = globalGameInstance.getNumberOfQuestions();
            QuestionList questionList = loader.loadQuestions(numberOfQuestions);

            for (GameInstance instance : globalGameInstance.getAllPlayerInstances()) {
                instance.setQuestionList(questionList);
                instance.start();
            }
        } catch (IOException e) {
            log.error("Error al cargar preguntas: {}", e.getMessage(), e);
        }
    }

    private void publishQuestionForAllPlayers(int questionIndex, QuestionStatus status) {
        for (String playerId : globalGameInstance.getAllPlayerIds()) {
            GameInstance instance = globalGameInstance.getPlayerInstance(playerId);
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

    public void publishQuestionForPlayer(String playerId, int questionIndex,
                                        QuestionStatus status, Question nextQuestion) {
        GameInstance instance = globalGameInstance.getPlayerInstance(playerId);
        if (instance == null) {
            log.warn("No GameInstance for player {}", playerId);
            return;
        }

        int[] totals = instance.getCorrectIncorrectTotals();
        int totalCorrect = totals[0];
        int totalIncorrect = totals[1];

        QuestionChangedEvent event = new QuestionChangedEvent(
            questionIndex, status, playerId, nextQuestion, totalCorrect, totalIncorrect
        );

        externalBus.publishAndForget(event);
    }

    public boolean addPlayerToGame(String playerId) {
        if (playerId == null || playerId.isEmpty()) {
            log.warn("addPlayerToGame: playerId inválido");
            return false;
        }

        GameGlobal global = this.globalGameInstance;
        if (global == null || global.hasPlayer(playerId)) {
            return false;
        }

        if (global.getPlayerCount() >= global.getMaxPlayers()) {
            log.warn("Partida llena ({}/{})", global.getPlayerCount(), global.getMaxPlayers());
            return false;
        }

        String playerName = playerId.contains("-") ?
            playerId.substring(0, playerId.lastIndexOf("-")) : playerId;

        Player player = new Player(playerName, playerName);
        player.setPlayerID(playerId);

        GameInstance instance = new GameInstance(
            global.getGameDuration(),
            player,
            global.getDifficulty(),
            global.getNumberOfQuestions(),
            global.getGameType()
        );

        global.addPlayerInstance(playerId, instance);
        log.info("Jugador agregado: {} (total: {})", playerId, global.getPlayerCount());
        return true;
    }

    // ============================================================
    // GETTERS Y UTILIDADES
    // ============================================================

    public void addListener(EventListener listener) {
        externalBus.addListener(listener);
    }

    public void removeListener(EventListener listener) {
        externalBus.removeListener(listener);
    }

    public AsyncEventBus getExternalBus() {
        return externalBus;
    }

    public GameGlobal getGameInstance() {
        return globalGameInstance;
    }

    public int getRemainingSeconds() {
        return globalGameInstance != null ? globalGameInstance.getRemainingSeconds() : 0;
    }

    public String getGameSessionId() {
        return gameSessionId;
    }

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
     * Cerrar el servicio y liberar recursos
     */
    public void shutdown() {
        if (timeService != null) {
            timeService.shutdown();
        }
        virtualThreads.shutdown();
        externalBus.shutdown();
        log.info("AsyncGameService cerrado");
    }
}
