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
        // ====================================================================
        // IMPLEMENTACIÓN PROPUESTA: Manejo de respuestas de jugadores
        // ====================================================================
        // AUTOR: Leandro (frontend) - REQUIERE REVISIÓN DEL EQUIPO BACKEND
        // FECHA: 28 de enero de 2026
        // 
        // DESCRIPCIÓN:
        // Esta implementación completa el flujo de validación de respuestas que
        // actualmente está incompleto en el backend. El frontend publica eventos
        // AnswerSubmittedEvent cuando el jugador selecciona una respuesta, pero
        // el backend NO los procesa ni responde con AnswerValidatedEvent.
        //
        // FLUJO ACTUAL (INCOMPLETO):
        // 1. Frontend: Usuario selecciona respuesta
        // 2. Frontend: Publica AnswerSubmittedEvent(playerIndex, letter, answer)
        // 3. Backend: [VACÍO - No hay handler] ❌
        // 4. Frontend: Espera AnswerValidatedEvent (nunca llega) ❌
        //
        // FLUJO PROPUESTO (COMPLETO):
        // 1. Frontend: Usuario selecciona respuesta
        // 2. Frontend: Publica AnswerSubmittedEvent(playerIndex, letter, answer)
        // 3. Backend: handleAnswerSubmitted() valida usando Question.isCorrectIndex()
        // 4. Backend: Publica AnswerValidatedEvent(isCorrect, correctAnswer)
        // 5. Frontend: Recibe validación y actualiza UI (verde/rojo)
        //
        // JUSTIFICACIÓN ARQUITECTÓNICA:
        // - Separa responsabilidades: Frontend = UI, Backend = lógica de negocio
        // - Centraliza validación: Una única fuente de verdad (backend)
        // - Previene trampas: El frontend no tiene acceso a respuestas correctas
        // - Escalable: Permite multijugador sin exponer respuestas al cliente
        //
        // ALTERNATIVAS CONSIDERADAS:
        // A) Validar en frontend: Más rápido pero viola MVC y permite trampas
        // B) Esta implementación: Correcta arquitecturalmente, requiere backend
        //
        // REVISAR:
        // - ¿Es correcto obtener el índice de pregunta desde la letra usando AlphabetMap?
        // - ¿Debería actualizarse GameInstance.currentQuestionIndex después de validar?
        // - ¿Necesitamos actualizar GameRecord con estadísticas (correctas/incorrectas)?
        // - ¿Hay casos edge que no estamos manejando (respuesta vacía, letra inválida)?
        //
        // SI NO ESTÁS DE ACUERDO: Comenta este bloque y coordina con el equipo frontend
        // ====================================================================
        else if (event instanceof AnswerSubmittedEvent) {
            handleAnswerSubmitted((AnswerSubmittedEvent) event);
        }
    }

    /**
     * ====================================================================
     * MÉTODO PROPUESTO: Validación de respuestas de jugadores
     * ====================================================================
     * AUTOR: Leandro (frontend) - REQUIERE REVISIÓN DEL EQUIPO BACKEND
     * 
     * Maneja el evento AnswerSubmittedEvent publicado por el GameController cuando
     * un jugador selecciona una respuesta. Este método:
     * 
     * 1. Obtiene la GameInstance del jugador usando su playerIndex
     * 2. Recupera la pregunta actual basándose en la letra del rosco (A-Z)
     * 3. Extrae el índice de respuesta seleccionado del String "answer"
     *    (formato esperado: "Opción 1", "Opción 2", etc.)
     * 4. Valida la respuesta usando Question.isCorrectIndex(selectedIndex)
     * 5. Publica AnswerValidatedEvent con el resultado para que el frontend
     *    actualice la UI (botón verde si correcto, rojo si incorrecto)
     *
     * DETALLES DE IMPLEMENTACIÓN:
     * - Usa getIndexFromLetter() para convertir letra → índice pregunta
     * - Asume que "answer" contiene "Opción X" donde X es 1-4
     * - Si no puede parsear el índice, asume respuesta incorrecta por seguridad
     * - Obtiene la respuesta correcta para enviarla en caso de error
     *
     * POSIBLES MEJORAS:
     * - Añadir actualización de GameRecord con estadísticas
     * - Incrementar currentQuestionIndex después de validar
     * - Manejo más robusto de formatos de respuesta
     * - Logging de respuestas para análisis posterior
     * - Validación de que el jugador no está respondiendo fuera de turno
     *
     * @param event El evento con playerIndex, letter (A-Z), y answer (String)
     */
    private void handleAnswerSubmitted(AnswerSubmittedEvent event) {
        // Extraer datos del evento
        int playerIndex = event.getPlayerIndex();
        char letter = event.getLetter();
        String answerText = event.getAnswer();

        log.info("=== VALIDACIÓN DE RESPUESTA ===");
        log.info("Jugador: {}, Letra: {}, Respuesta: {}", playerIndex, letter, answerText);

        // PASO 1: Obtener la GameInstance del jugador
        // NOTA PARA BACKEND: Actualmente usamos playerIndex (0-based) como String
        // porque GlobalGameInstance.getPlayerInstance() espera String playerId.
        // Si tu diseño usa un Map diferente, ajusta esta línea.
        String playerId = String.valueOf(playerIndex);
        GameInstance instance = GlobalGameInstance.getPlayerInstance(playerId);

        if (instance == null) {
            log.error("ERROR: No se encontró GameInstance para jugador {}", playerId);
            // Publicar evento de error si no existe la instancia
            publishAnswerValidationError(playerIndex, letter, answerText, "Jugador no encontrado");
            return;
        }

        // PASO 2: Convertir letra (A-Z) a índice de pregunta (0-26)
        // AlphabetMap solo tiene getLetter(index), así que necesitamos buscar al revés
        // o simplemente usar el índice que viene en el evento QuestionChangedEvent.
        // SOLUCIÓN TEMPORAL: iterar sobre el mapa para encontrar el índice
        int questionIndex = getIndexFromLetter(letter);
        
        if (questionIndex < 0) {
            log.error("ERROR: Letra inválida recibida: {}", letter);
            publishAnswerValidationError(playerIndex, letter, answerText, "Letra inválida");
            return;
        }

        // PASO 3: Obtener la pregunta correspondiente a esa letra
        QuestionList questionList = instance.getQuestionList();
        if (questionList == null) {
            log.error("ERROR: QuestionList es null para jugador {}", playerId);
            publishAnswerValidationError(playerIndex, letter, answerText, "Lista de preguntas no disponible");
            return;
        }

        if (questionIndex >= questionList.getCurrentLength()) {
            log.error("ERROR: Índice de pregunta {} fuera de rango (máx: {})", 
                     questionIndex, questionList.getCurrentLength() - 1);
            publishAnswerValidationError(playerIndex, letter, answerText, "Pregunta no disponible");
            return;
        }

        Question question = questionList.getQuestionAt(questionIndex);

        // PASO 4: Parsear el índice de respuesta del texto
        // FORMATO ESPERADO: "Opción 1", "Opción 2", "Opción 3", "Opción 4"
        // Extraemos el número y lo convertimos a índice 0-based (0-3)
        int selectedIndex = parseAnswerIndex(answerText);
        
        if (selectedIndex < 0 || selectedIndex > 3) {
            log.error("ERROR: No se pudo parsear el índice de respuesta desde: {}", answerText);
            // Asumimos respuesta incorrecta si no podemos parsear
            publishAnswerValidation(playerIndex, letter, answerText, false, 
                                   question.getQuestionResponsesList().get(question.getCorrectQuestionIndex()));
            return;
        }

        // PASO 5: Validar usando el método Question.isCorrectIndex()
        // Este método compara selectedIndex con correctQuestionIndex
        boolean isCorrect = question.isCorrectIndex(selectedIndex);
        
        String correctAnswer = question.getQuestionResponsesList().get(question.getCorrectQuestionIndex());

        log.info("Respuesta seleccionada: índice {} ({})", selectedIndex, answerText);
        log.info("Respuesta correcta: índice {} ({})", question.getCorrectQuestionIndex(), correctAnswer);
        log.info("Resultado: {}", isCorrect ? "✅ CORRECTA" : "❌ INCORRECTA");

        // PASO 6: Publicar resultado al frontend
        publishAnswerValidation(playerIndex, letter, answerText, isCorrect, correctAnswer);

        // TODO PARA EL EQUIPO BACKEND:
        // - ¿Deberíamos actualizar instance.setCurrentQuestionIndex(questionIndex + 1)?
        // - ¿Deberíamos guardar estadísticas en GameRecord?
        // - ¿Deberíamos cambiar el QuestionStatus de la pregunta?
        // - ¿Hay que manejar fin de rosco (todas las preguntas respondidas)?
    }

    /**
     * Parsea el índice de respuesta desde el texto de respuesta.
     * 
     * FORMATO ESPERADO: "Opción 1", "Opción 2", "Opción 3", "Opción 4"
     * Convierte a índice 0-based: "Opción 1" → 0, "Opción 2" → 1, etc.
     * 
     * NOTA PARA BACKEND: Este parsing asume el formato exacto del frontend.
     * Si el formato cambia, este método debe actualizarse.
     * 
     * @param answerText El texto de la respuesta (ej: "Opción 1")
     * @return Índice 0-based (0-3), o -1 si no se puede parsear
     */
    private int parseAnswerIndex(String answerText) {
        try {
            // Extraer el número de "Opción X"
            // Ejemplo: "Opción 1" → "1" → 0 (índice 0-based)
            String[] parts = answerText.trim().split("\\s+");
            if (parts.length >= 2) {
                int displayIndex = Integer.parseInt(parts[1]); // "1", "2", "3", "4"
                return displayIndex - 1; // Convertir a 0-based
            }
        } catch (NumberFormatException e) {
            log.error("Error parseando índice desde: {}", answerText, e);
        }
        return -1;
    }

    /**
     * Publica un AnswerValidatedEvent hacia el frontend con el resultado de la validación.
     * 
     * @param playerIndex Índice del jugador (0-based)
     * @param letter Letra del rosco (A-Z)
     * @param answer Texto de la respuesta seleccionada
     * @param isCorrect Si la respuesta fue correcta
     * @param correctAnswer Texto de la respuesta correcta (para mostrar en UI si falla)
     */
    private void publishAnswerValidation(int playerIndex, char letter, String answer, 
                                        boolean isCorrect, String correctAnswer) {
        // CORRECCIÓN: El enum es RESPONDED_FAIL, no RESPONDED_WRONG
        QuestionStatus status = isCorrect ? QuestionStatus.RESPONDED_OK : QuestionStatus.RESPONDED_FAIL;
        
        AnswerValidatedEvent validationEvent = new AnswerValidatedEvent(
            playerIndex,
            letter,
            answer,
            status,
            correctAnswer
        );
        
        externalBus.publish(validationEvent);
        log.info("AnswerValidatedEvent publicado: {} (letra: {})", isCorrect ? "CORRECTA" : "INCORRECTA", letter);
    }

    /**
     * Publica un AnswerValidatedEvent de error cuando falla la validación por razones técnicas.
     * 
     * @param playerIndex Índice del jugador
     * @param letter Letra del rosco
     * @param answer Respuesta intentada
     * @param errorReason Razón del error (para logging)
     */
    private void publishAnswerValidationError(int playerIndex, char letter, String answer, String errorReason) {
        log.error("Error en validación: {}", errorReason);
        // Publicar como respuesta incorrecta por seguridad
        publishAnswerValidation(playerIndex, letter, answer, false, "Error: " + errorReason);
    }

    /**
     * MÉTODO AUXILIAR: Convierte una letra a su índice en AlphabetMap.
     * 
     * Como AlphabetMap solo tiene getLetter(index), necesitamos buscar al revés.
     * Este método busca la letra (case-insensitive) en el mapa y retorna su índice.
     * 
     * @param letter La letra a buscar (a-z, A-Z, ñ, Ñ)
     * @return Índice (0-26), o -1 si no se encuentra
     */
    private int getIndexFromLetter(char letter) {
        String letterStr = String.valueOf(letter).toLowerCase();
        for (Map.Entry<Integer, String> entry : AlphabetMap.getMap().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(letterStr)) {
                return entry.getKey();
            }
        }
        return -1;
    }

}
