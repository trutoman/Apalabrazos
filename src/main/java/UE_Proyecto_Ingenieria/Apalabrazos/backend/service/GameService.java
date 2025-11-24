package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.*;

import java.util.List;

/**
 * Service that manages the game logic and publishes events.
 * This is where the business logic lives - it listens to user events
 * and publishes state change events.
 */
public class GameService implements EventListener {

    private final EventBus eventBus;
    private GameGlobal gameGlobal;

    public GameService() {
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
        // Create players
        Player playerOne = new Player();
        playerOne.setName(event.getPlayerOneName());

        Player playerTwo = new Player();
        playerTwo.setName(event.getPlayerTwoName());

        // Create game players with question lists
        GamePlayer gamePlayerOne = new GamePlayer();
        gamePlayerOne.setPlayer(playerOne);
        gamePlayerOne.setQuestionList(new QuestionList()); // TODO: Load questions

        GamePlayer gamePlayerTwo = new GamePlayer();
        gamePlayerTwo.setPlayer(playerTwo);
        gamePlayerTwo.setQuestionList(new QuestionList()); // TODO: Load questions

        // Initialize global game
        this.gameGlobal = new GameGlobal();
        this.gameGlobal.setGamePlayer1(gamePlayerOne);
        this.gameGlobal.setGamePlayer2(gamePlayerTwo);
        this.gameGlobal.setCurrentPlayerIndex(0); // Start with player 1

        // Notify that first question is ready
        notifyQuestionChanged(0);
    }

    /**
     * Handle answer submission
     */
    private void handleAnswerSubmitted(AnswerSubmittedEvent event) {
        if (gameGlobal == null) {
            return;
        }

        GamePlayer currentPlayer = event.getPlayerIndex() == 0
            ? gameGlobal.getGamePlayer1()
            : gameGlobal.getGamePlayer2();

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
            notifyQuestionChanged(event.getPlayerIndex());
        } else {
            // All questions answered, end turn
            endTurn(event.getPlayerIndex());
        }
    }

    /**
     * End the current player's turn
     */
    private void endTurn(int playerIndex) {
        GamePlayer player = playerIndex == 0
            ? gameGlobal.getGamePlayer1()
            : gameGlobal.getGamePlayer2();

        GameRecord record = player.getGameResult();

        // Publish turn ended event
        eventBus.publish(new TurnEndedEvent(
            playerIndex,
            record.getCorrectAnswers(),
            player.getQuestionList().getCurrentLength()
        ));

        // Check if both players finished
        if (playerIndex == 0) {
            // Switch to player 2
            gameGlobal.setCurrentPlayerIndex(1);
            notifyQuestionChanged(1);
        } else {
            // Game finished
            endGame();
        }
    }

    /**
     * End the game and publish final results
     */
    private void endGame() {
        eventBus.publish(new GameFinishedEvent(
            gameGlobal.getGamePlayer1().getGameResult(),
            gameGlobal.getGamePlayer2().getGameResult()
        ));
    }

    /**
     * Notify that the current question has changed
     */
    private void notifyQuestionChanged(int playerIndex) {
        GamePlayer player = playerIndex == 0
            ? gameGlobal.getGamePlayer1()
            : gameGlobal.getGamePlayer2();

        int questionIndex = player.getCurrentQuestionIndex();
        QuestionList questionList = player.getQuestionList();

        if (questionIndex < questionList.getCurrentLength()) {
            // Get the letter from AlphabetMap using the question index
            String letterStr = AlphabetMap.getLetter(questionIndex);
            char letter = letterStr.charAt(0);

            eventBus.publish(new QuestionChangedEvent(
                playerIndex,
                questionIndex,
                letter
            ));
        }
    }

    /**
     * Get the current game state
     */
    public GameGlobal getGameGlobal() {
        return gameGlobal;
    }
}
