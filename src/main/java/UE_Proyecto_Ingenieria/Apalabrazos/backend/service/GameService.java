package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.tools.QuestionFileLoader;

import java.io.IOException;
import java.util.List;

/**
 * Service that manages the game logic and publishes events.
 * This is where the business logic lives - it listens to user events
 * and publishes state change events.
 */
public class GameService implements EventListener {

    private final EventBus eventBus;
    private GameSingleInstance singleGameInstance;

    public GameService() {
        this.singleGameInstance = new GameSingleInstance();
        this.eventBus = EventBus.getInstance();
        // Registrarse como listener de eventos
        eventBus.addListener(this);
    }

    /**
     * Recibir y procesar eventos
     */
    @Override
    public void onEvent(GameEvent event) {
        // Verificar el tipo de evento y llamar al método apropiado
        if (event instanceof GameStartedEvent) {
            handleGameStarted((GameStartedEvent) event);
        } else if (event instanceof AnswerSubmittedEvent) {
            handleAnswerSubmitted((AnswerSubmittedEvent) event);
        }
    }

    /**
     * Initialize a new game
     */
    private void handleGameStarted(GameStartedEvent event) {
         // Create players from event TODO: players are generated before event
        Player playerOne = new Player();
        playerOne.setName(event.getGamePlayerConfig().getPlayerName());
        singleGameInstance.setPlayer(playerOne);
        singleGameInstance.setTimeCounter(event.getGamePlayerConfig().getTimerSeconds());

        // Cargar preguntas desde el archivo
        try {
            QuestionFileLoader loader = new QuestionFileLoader();
            QuestionList questions = loader.loadQuestions(); // Usa archivo por defecto
            singleGameInstance.setQuestionList(questions);
        } catch (IOException e) {
            System.err.println("Error cargando preguntas: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Notify that first question is ready
        notifyQuestionChanged();
    }

    /**
     * Handle answer submission
     */
    private void handleAnswerSubmitted(AnswerSubmittedEvent event) {
        if (singleGameInstance == null) {
            return;
        }

        // En modo single player, siempre usamos singleGameInstance
        GameSingleInstance currentPlayer = singleGameInstance;

        // Get the current question
        int questionIndex = currentPlayer.getCurrentQuestionIndex();
        QuestionList questionList = currentPlayer.getQuestionList();

        if (questionIndex >= questionList.getCurrentLength()) {
            // No more questions, end turn
            endTurn(event.getPlayerIndex());
            return;
        }

        Question question = questionList.getQuestionAt(questionIndex);

        // Validate answer (assuming answer is the index of the selected response)
        int selectedIndex = -1;
        try {
            selectedIndex = Integer.parseInt(event.getAnswer());
        } catch (NumberFormatException e) {
            // If not a number, check if it matches any response text
            List<String> responses = question.getQuestionResponsesList();
            for (int i = 0; i < responses.size(); i++) {
                if (responses.get(i).equalsIgnoreCase(event.getAnswer().trim())) {
                    selectedIndex = i;
                    break;
                }
            }
        }

        boolean isCorrect = selectedIndex >= 0 && question.isCorrectIndex(selectedIndex);
        QuestionStatus status = isCorrect ? QuestionStatus.RESPONDED_OK : QuestionStatus.RESPONDED_FAIL;
        question.setQuestionStatus(status);

        // Update game record
        GameRecord record = currentPlayer.getGameResult();
        if (isCorrect) {
            record.setCorrectAnswers(record.getCorrectAnswers() + 1);
        } else {
            record.setIncorrectAnswers(record.getIncorrectAnswers() + 1);
        }

        // Publish validation result
        eventBus.publish(new AnswerValidatedEvent(
            event.getPlayerIndex(),
            event.getLetter(),
            event.getAnswer(),
            status,
            question.getCorrectResponse()
        ));

        // Move to next question
        currentPlayer.setCurrentQuestionIndex(questionIndex + 1);

        if (questionIndex + 1 < questionList.getCurrentLength()) {
            notifyQuestionChanged();
        } else {
            // All questions answered, end turn
            endTurn(event.getPlayerIndex());
        }
    }

    /**
     * End the current player's turn
     */
    private void endTurn(int playerIndex) {
        GameRecord record = singleGameInstance.getGameResult();

        // Publish turn ended event
        eventBus.publish(new TurnEndedEvent(
            playerIndex,
            record.getCorrectAnswers(),
            singleGameInstance.getQuestionList().getCurrentLength()
        ));

        // Game finished (single player mode)
        endGame();
    }

    /**
     * End the game and publish final results
     */
    private void endGame() {
        // En modo single player, el segundo record es null
        eventBus.publish(new GameFinishedEvent(
            singleGameInstance.getGameResult(),
            null  // No hay segundo jugador en modo single player
        ));
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
}
