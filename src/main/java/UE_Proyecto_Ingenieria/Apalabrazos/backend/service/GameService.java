package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.tools.QuestionFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
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
        publishQuestionForAllPlayers(0, QuestionStatus.INIT);

        log.info("Juego iniciado. TimeService iniciado");
    }

    /**
     * Carga las preguntas y las asigna a cada instancia de jugador
     */
    private void loadQuestionsForAllPlayers() {
        try {
            QuestionFileLoader loader = new QuestionFileLoader();
            
            // Obtener el nivel de dificultad desde la configuración global
            QuestionLevel difficultyLevel = GlobalGameInstance.getDifficulty();
            if (difficultyLevel == null) {
                difficultyLevel = QuestionLevel.EASY; // Valor por defecto
            }

            // Cargar 27 preguntas filtradas por nivel de dificultad
            QuestionList questionList = loader.loadQuestionsByLevel(difficultyLevel);

            // Asignar a cada instancia de jugador
            for (GameInstance instance : GlobalGameInstance.getAllPlayerInstances()) {
                instance.setQuestionList(questionList);
                instance.start();
            }
            
            log.info("Cargadas {} preguntas de nivel {} para {} jugador(es)", 
                     questionList.getCurrentLength(), difficultyLevel, 
                     GlobalGameInstance.getPlayerCount());
        } catch (IOException e) {
            log.error("Error al cargar preguntas: {}", e.getMessage(), e);
        }
    }

    /**
     * Publica la pregunta indicada por índice para todos los jugadores
     */
    private void publishQuestionForAllPlayers(int questionIndex, QuestionStatus status) {
        for (String playerId : GlobalGameInstance.getAllPlayerIds()) {
            publishQuestionForPlayer(playerId, questionIndex, status);
        }
    }

    /**
     * Publica un QuestionChangedEvent para un jugador y pregunta concretos
     */
    public void publishQuestionForPlayer(String playerId, int questionIndex, QuestionStatus status) {
        GameInstance instance = GlobalGameInstance.getPlayerInstance(playerId);
        if (instance == null) {
            log.warn("No GameInstance for player {}", playerId);
            return;
        }

        QuestionList list = instance.getQuestionList();
        if (list == null || questionIndex < 0 || questionIndex >= list.getCurrentLength()) {
            log.warn("Invalid question index {} for player {}", questionIndex, playerId);
            return;
        }

        Question question = list.getQuestionAt(questionIndex);
        QuestionChangedEvent event = new QuestionChangedEvent(questionIndex, status, question, playerId);
        externalBus.publish(event);
        log.info("Pregunta {} publicada para jugador {}", questionIndex, playerId);
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
        }
        // LEANDRO -> Manejo de respuestas de jugadores. El backend original no procesaba
        // AnswerSubmittedEvent, así que agregamos validación completa aquí.
        else if (event instanceof AnswerSubmittedEvent) {
            handleAnswerSubmitted((AnswerSubmittedEvent) event);
        }
        // LEANDRO -> FIN validación de respuestas
    }

    // LEANDRO -> Métodos agregados para validar respuestas de jugadores
    
    /**
     * Maneja la validación de respuestas cuando el jugador selecciona una opción.
     * Valida usando Question.isCorrectIndex() y publica AnswerValidatedEvent al frontend.
     * 
     * @param event Evento con playerIndex, letra del rosco, y respuesta seleccionada
     */
    private void handleAnswerSubmitted(AnswerSubmittedEvent event) {
        final int playerIndex = event.getPlayerIndex();
        final char roscoLetter = event.getLetter();
        final String selectedAnswer = event.getAnswer();

        log.info("=== VALIDACIÓN DE RESPUESTA ===");
        log.info("Jugador: {}, Letra: {}, Respuesta: {}", playerIndex, roscoLetter, selectedAnswer);

        // LEANDRO -> Resolver playerId real (single player usa el único jugador del game)
        final String playerId = resolvePlayerId(playerIndex);
        final GameInstance instance = GlobalGameInstance.getPlayerInstance(playerId);

        if (instance == null) {
            log.error("GameInstance no encontrada para jugador: {}", playerId);
            publishValidationError(playerIndex, roscoLetter, selectedAnswer, "Jugador no encontrado");
            return;
        }

        // LEANDRO -> Convertir letra del rosco a índice de pregunta (a=0, b=1, etc.)
        final int questionIndex = letterToIndex(roscoLetter);
        if (questionIndex < 0) {
            log.error("Letra del rosco inválida: '{}'", roscoLetter);
            publishValidationError(playerIndex, roscoLetter, selectedAnswer, "Letra inválida");
            return;
        }

        final QuestionList questionList = instance.getQuestionList();
        if (!isValidQuestionIndex(questionIndex, questionList)) {
            log.error("Índice de pregunta {} fuera de rango", questionIndex);
            publishValidationError(playerIndex, roscoLetter, selectedAnswer, "Pregunta no disponible");
            return;
        }

        final Question question = questionList.getQuestionAt(questionIndex);
        final String correctAnswerText = question.getQuestionResponsesList().get(question.getCorrectQuestionIndex());

        // LEANDRO -> Parsear "Opción X" a índice (0-3)
        final int selectedIndex = parseOptionIndex(selectedAnswer);
        if (!isValidOptionIndex(selectedIndex)) {
            log.error("No se pudo parsear respuesta: '{}'", selectedAnswer);
            publishValidation(playerIndex, roscoLetter, selectedAnswer, false, correctAnswerText);
            return;
        }

        // LEANDRO -> Validar respuesta y publicar resultado
        final boolean isCorrect = question.isCorrectIndex(selectedIndex);
        log.info("Respuesta: índice {} - {}", selectedIndex, isCorrect ? "✅ CORRECTA" : "❌ INCORRECTA");
        publishValidation(playerIndex, roscoLetter, selectedAnswer, isCorrect, correctAnswerText);

        // LEANDRO -> Avanzar a siguiente pregunta (circular)
        advanceToNextQuestion(instance, questionList, playerId, questionIndex);
    }

    /**
     * Convierte "Opción X" (1-4) a índice 0-based (0-3).
     * @param optionText Texto de la opción (ej: "Opción 1")
     * @return Índice 0-based (0-3), o -1 si no se puede parsear
     */
    private int parseOptionIndex(String optionText) {
        if (optionText == null || optionText.trim().isEmpty()) {
            return -1;
        }
        
        try {
            final String[] parts = optionText.trim().split("\\s+");
            if (parts.length >= 2) {
                final int displayNumber = Integer.parseInt(parts[1]);
                return displayNumber - 1; // "Opción 1" → índice 0
            }
        } catch (NumberFormatException e) {
            log.debug("No se pudo parsear: '{}'", optionText);
        }
        return -1;
    }

    /**
     * Publica AnswerValidatedEvent al frontend con el resultado de la validación.
     */
    private void publishValidation(int playerIndex, char letter, String answer, 
                                   boolean isCorrect, String correctAnswer) {
        final QuestionStatus status = isCorrect ? QuestionStatus.RESPONDED_OK : QuestionStatus.RESPONDED_FAIL;
        final AnswerValidatedEvent event = new AnswerValidatedEvent(
            playerIndex, letter, answer, status, correctAnswer
        );
        
        externalBus.publish(event);
        log.debug("Validación publicada: {} para letra '{}'", status, letter);
    }

    /**
     * Publica evento de error cuando falla la validación.
     */
    private void publishValidationError(int playerIndex, char letter, String answer, String reason) {
        publishValidation(playerIndex, letter, answer, false, "Error: " + reason);
    }

    /**
     * Convierte letra del rosco a su índice (a=0, b=1, ..., z=25).
     * AlphabetMap solo tiene getLetter(index), así que hacemos búsqueda inversa.
     */
    private int letterToIndex(char letter) {
        final String letterLower = String.valueOf(letter).toLowerCase();
        return AlphabetMap.getMap().entrySet().stream()
            .filter(entry -> entry.getValue().equalsIgnoreCase(letterLower))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(-1);
    }

    /**
     * Resuelve el playerId real según el contexto (single/multiplayer).
     */
    private String resolvePlayerId(int playerIndex) {
        if (GlobalGameInstance.getPlayerCount() == 1) {
            return GlobalGameInstance.getAllPlayerIds().iterator().next();
        }
        return String.valueOf(playerIndex);
    }

    /**
     * Valida que el índice de pregunta sea válido para la lista.
     */
    private boolean isValidQuestionIndex(int index, QuestionList questionList) {
        return questionList != null && index >= 0 && index < questionList.getCurrentLength();
    }

    /**
     * Valida que el índice de opción sea válido (0-3).
     */
    private boolean isValidOptionIndex(int index) {
        return index >= 0 && index <= 3;
    }

    /**
     * Avanza a la siguiente pregunta de forma circular.
     */
    private void advanceToNextQuestion(GameInstance instance, QuestionList questionList, 
                                       String playerId, int currentIndex) {
        final int nextIndex = (currentIndex + 1) % questionList.getCurrentLength();
        instance.setCurrentQuestionIndex(nextIndex);
        
        final String nextLetter = AlphabetMap.getLetter(nextIndex);
        log.info("Siguiente pregunta: {} ({})", nextIndex, nextLetter);
        
        publishQuestionForPlayer(playerId, nextIndex, QuestionStatus.INIT);
    }
    
    // LEANDRO -> FIN validación de respuestas (~200 líneas, 10 métodos)

}
