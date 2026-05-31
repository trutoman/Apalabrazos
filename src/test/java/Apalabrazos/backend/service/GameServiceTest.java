package Apalabrazos.backend.service;

import Apalabrazos.backend.events.AnswerSubmittedEvent;
import Apalabrazos.backend.events.AnswerValidatedEvent;
import Apalabrazos.backend.events.CreatorInitGameEvent;
import Apalabrazos.backend.events.EventListener;
import Apalabrazos.backend.events.ExtraTimeScoreEvent;
import Apalabrazos.backend.events.GameControllerReady;
import Apalabrazos.backend.events.GameEvent;
import Apalabrazos.backend.events.GameFinishedEvent;
import Apalabrazos.backend.events.QuestionChangedEvent;
import Apalabrazos.backend.events.QuestionLoadErrorEvent;
import Apalabrazos.backend.events.StandingsEvent;
import Apalabrazos.backend.events.TimerTickEvent;
import Apalabrazos.backend.model.GameGlobal;
import Apalabrazos.backend.model.GameInstance;
import Apalabrazos.backend.model.Question;
import Apalabrazos.backend.model.QuestionLevel;
import Apalabrazos.backend.model.QuestionList;
import Apalabrazos.backend.model.QuestionStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameServiceTest {

    @Test
    void calculateAnswerScoreReturnsZeroWhenQuestionIsNull() throws Exception {
        GameService service = new GameService();

        int score = invokeCalculateAnswerScore(service, null, QuestionStatus.RESPONDED_OK);

        assertEquals(0, score);
    }

    @Test
    void calculateAnswerScoreReturnsZeroWhenStatusIsNotRespondedOk() throws Exception {
        GameService service = new GameService();
        Question question = createQuestion("a", QuestionStatus.INIT);
        question.setPassedCount(2);

        int score = invokeCalculateAnswerScore(service, question, QuestionStatus.RESPONDED_FAIL);

        assertEquals(0, score);
    }

    @Test
    void calculateAnswerScoreUsesBaseValueWhenNoPasses() throws Exception {
        GameService service = new GameService();
        Question question = createQuestion("a", QuestionStatus.INIT);
        question.setPassedCount(0);

        int score = invokeCalculateAnswerScore(service, question, QuestionStatus.RESPONDED_OK);

        assertEquals(100, score);
    }

    @Test
    void calculateAnswerScoreAppliesPenaltyPerPass() throws Exception {
        GameService service = new GameService();
        Question question = createQuestion("a", QuestionStatus.INIT);
        question.setPassedCount(3);

        int score = invokeCalculateAnswerScore(service, question, QuestionStatus.RESPONDED_OK);

        assertEquals(70, score);
    }

    @Test
    void calculateAnswerScoreNeverReturnsNegativeValues() throws Exception {
        GameService service = new GameService();
        Question question = createQuestion("a", QuestionStatus.INIT);
        question.setPassedCount(20);

        int score = invokeCalculateAnswerScore(service, question, QuestionStatus.RESPONDED_OK);

        assertEquals(0, score);
    }

    @Test
    void findNextUnansweredIndexCircularReturnsMinusOneForNullList() throws Exception {
        GameService service = new GameService();

        int next = invokeFindNextUnansweredIndexCircular(service, null, 0);

        assertEquals(-1, next);
    }

    @Test
    void findNextUnansweredIndexCircularReturnsMinusOneForEmptyList() throws Exception {
        GameService service = new GameService();
        QuestionList list = new QuestionList();

        int next = invokeFindNextUnansweredIndexCircular(service, list, 0);

        assertEquals(-1, next);
    }

    @Test
    void findNextUnansweredIndexCircularWrapsAroundAndFindsInitQuestion() throws Exception {
        GameService service = new GameService();
        QuestionList list = new QuestionList();
        list.addQuestion(createQuestion("a", QuestionStatus.RESPONDED_OK));
        list.addQuestion(createQuestion("b", QuestionStatus.INIT));
        list.addQuestion(createQuestion("c", QuestionStatus.RESPONDED_FAIL));

        int next = invokeFindNextUnansweredIndexCircular(service, list, 2);

        assertEquals(1, next);
    }

    @Test
    void findNextUnansweredIndexCircularTreatsPassedAsCandidateBecauseItIsStoredAsInit() throws Exception {
        GameService service = new GameService();
        QuestionList list = new QuestionList();
        list.addQuestion(createQuestion("a", QuestionStatus.RESPONDED_OK));
        list.addQuestion(createQuestion("b", QuestionStatus.INIT));
        list.addQuestion(createQuestion("c", QuestionStatus.RESPONDED_FAIL));

        int next = invokeFindNextUnansweredIndexCircular(service, list, 0);

        assertEquals(1, next);
    }

    @Test
    void findNextUnansweredIndexCircularReturnsMinusOneWhenAllAreAnswered() throws Exception {
        GameService service = new GameService();
        QuestionList list = new QuestionList();
        list.addQuestion(createQuestion("a", QuestionStatus.RESPONDED_OK));
        list.addQuestion(createQuestion("b", QuestionStatus.RESPONDED_FAIL));

        int next = invokeFindNextUnansweredIndexCircular(service, list, 0);

        assertEquals(-1, next);
    }

    @Test
    void findNextUnansweredIndexCircularNormalizesOutOfRangeCurrentIndex() throws Exception {
        GameService service = new GameService();
        QuestionList list = new QuestionList();
        list.addQuestion(createQuestion("a", QuestionStatus.INIT));
        list.addQuestion(createQuestion("b", QuestionStatus.RESPONDED_OK));

        int next = invokeFindNextUnansweredIndexCircular(service, list, 99);

        assertEquals(0, next);
    }

    @Test
    void handleAnswerSubmittedCorrectAnswerUpdatesScoreAndPublishesEvents() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        GameInstance instance = service.getGameInstance().getPlayerInstance("p1");
        QuestionList list = new QuestionList();
        list.addQuestion(createQuestion("a", QuestionStatus.INIT));
        list.addQuestion(createQuestion("b", QuestionStatus.INIT));
        instance.setQuestionList(list);

        List<GameEvent> events = registerEventCollector(service);
        invokeHandleAnswerSubmitted(service, new AnswerSubmittedEvent("p1", 0, 0));

        AnswerValidatedEvent validated = waitForEvent(events, AnswerValidatedEvent.class, 1000);
        assertNotNull(validated);
        assertEquals("p1", validated.getPlayerId());
        assertEquals(QuestionStatus.RESPONDED_OK, validated.getStatus());
        assertEquals(100, validated.getScore());
        assertEquals(100, validated.getTotalScore());

        QuestionChangedEvent changed = waitForEvent(events, QuestionChangedEvent.class, 1000);
        assertNotNull(changed);
        assertEquals(1, changed.getQuestionIndex());
        assertEquals(QuestionStatus.INIT, changed.getStatus());
        assertNotNull(changed.getNextQuestion());

        assertEquals(100, instance.getTotalScore());
        assertEquals(QuestionStatus.RESPONDED_OK, list.getQuestionAt(0).getQuestionStatus());
        assertEquals(QuestionStatus.RESPONDED_OK.getValue(), list.getQuestionAt(0).getUserResponseRecorded());
        assertEquals(1, instance.getCurrentQuestionIndex());
    }

    @Test
    void handleAnswerSubmittedIncorrectAnswerKeepsScoreAtZeroAndAdvancesQuestion() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        GameInstance instance = service.getGameInstance().getPlayerInstance("p1");
        QuestionList list = new QuestionList();
        list.addQuestion(createQuestion("a", QuestionStatus.INIT));
        list.addQuestion(createQuestion("b", QuestionStatus.INIT));
        instance.setQuestionList(list);

        List<GameEvent> events = registerEventCollector(service);
        invokeHandleAnswerSubmitted(service, new AnswerSubmittedEvent("p1", 0, 2));

        AnswerValidatedEvent validated = waitForEvent(events, AnswerValidatedEvent.class, 1000);
        assertNotNull(validated);
        assertEquals(QuestionStatus.RESPONDED_FAIL, validated.getStatus());
        assertEquals(0, validated.getScore());
        assertEquals(0, validated.getTotalScore());

        assertEquals(0, instance.getTotalScore());
        assertEquals(QuestionStatus.RESPONDED_FAIL, list.getQuestionAt(0).getQuestionStatus());
        assertEquals(1, instance.getCurrentQuestionIndex());
    }

    @Test
    void handleAnswerSubmittedPassedLeavesQuestionAsInitAndMovesNext() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        GameInstance instance = service.getGameInstance().getPlayerInstance("p1");
        QuestionList list = new QuestionList();
        list.addQuestion(createQuestion("a", QuestionStatus.INIT));
        list.addQuestion(createQuestion("b", QuestionStatus.INIT));
        instance.setQuestionList(list);

        List<GameEvent> events = registerEventCollector(service);
        invokeHandleAnswerSubmitted(service, new AnswerSubmittedEvent("p1", 0, -1));

        AnswerValidatedEvent validated = waitForEvent(events, AnswerValidatedEvent.class, 1000);
        assertNotNull(validated);
        assertEquals(QuestionStatus.PASSED, validated.getStatus());
        assertEquals(0, validated.getScore());

        Question first = list.getQuestionAt(0);
        assertEquals(1, first.getPassedCount());
        assertEquals(QuestionStatus.INIT, first.getQuestionStatus());
        assertEquals(QuestionStatus.INIT.getValue(), first.getUserResponseRecorded());
        assertEquals(1, instance.getCurrentQuestionIndex());
    }

    @Test
    void handleAnswerSubmittedEmitsExtraTimeWhenPlayerFinishesRoscoWithoutFinishingGame() throws Exception {
        GameService service = new GameService();
        GameGlobal global = service.getGameInstance();
        global.setMaxPlayers(2);
        assertTrue(service.addPlayerToGame("p1"));
        assertTrue(service.addPlayerToGame("p2"));

        QuestionList oneQuestion = new QuestionList();
        oneQuestion.addQuestion(createQuestion("a", QuestionStatus.INIT));
        global.getPlayerInstance("p1").setQuestionList(oneQuestion);

        QuestionList pendingQuestion = new QuestionList();
        pendingQuestion.addQuestion(createQuestion("a", QuestionStatus.INIT));
        global.getPlayerInstance("p2").setQuestionList(pendingQuestion);

        List<GameEvent> events = registerEventCollector(service);
        invokeHandleAnswerSubmitted(service, new AnswerSubmittedEvent("p1", 0, 0));

        ExtraTimeScoreEvent extra = waitForEvent(events, ExtraTimeScoreEvent.class, 1000);
        assertNotNull(extra);
        assertEquals("p1", extra.getPlayerId());
        assertTrue(extra.getExtraTimeScore() >= 0);
        assertEquals(GameInstance.GameState.FINISHED, global.getPlayerInstance("p1").getGameInstanceState());

        GameFinishedEvent finished = waitForEvent(events, GameFinishedEvent.class, 250);
        assertNull(finished);
    }

    @Test
    void handleAnswerSubmittedPublishesGameFinishedWhenAllPlayersAreDone() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        QuestionList oneQuestion = new QuestionList();
        oneQuestion.addQuestion(createQuestion("a", QuestionStatus.INIT));
        service.getGameInstance().getPlayerInstance("p1").setQuestionList(oneQuestion);

        List<GameEvent> events = registerEventCollector(service);
        invokeHandleAnswerSubmitted(service, new AnswerSubmittedEvent("p1", 0, 0));

        GameFinishedEvent finished = waitForEvent(events, GameFinishedEvent.class, 1000);
        assertNotNull(finished);
        assertEquals(service.getMatchId(), finished.getMatchId());
        assertEquals(GameGlobal.GameGlobalState.POST, service.getGameInstance().getState());
    }

    @Test
    void loadQuestionsForAllPlayersWithTimeoutSuccessAssignsClonedListsAndStartsPlayers() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        QuestionList loaded = new QuestionList();
        loaded.addQuestion(createQuestion("a", QuestionStatus.INIT));
        loaded.addQuestion(createQuestion("b", QuestionStatus.INIT));

        setField(service, "questionPreloadStarted", true);
        setField(service, "questionLoadFuture", CompletableFuture.completedFuture(loaded));

        invokeLoadQuestionsForAllPlayersWithTimeout(service);

        GameInstance instance = service.getGameInstance().getPlayerInstance("p1");
        assertNotNull(instance.getQuestionList());
        assertEquals(2, instance.getQuestionList().getCurrentLength());
        assertFalse(loaded == instance.getQuestionList());
        assertEquals(GameInstance.GameState.PLAYING, instance.getGameInstanceState());
    }

    @Test
    void loadQuestionsForAllPlayersWithTimeoutTimeoutCancelsFuture() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        ImmediateTimeoutFuture future = new ImmediateTimeoutFuture();
        setField(service, "questionPreloadStarted", true);
        setField(service, "questionLoadFuture", future);

        TimeoutException ex = assertThrows(TimeoutException.class,
                () -> invokeLoadQuestionsForAllPlayersWithTimeout(service));
        assertEquals("forced-timeout", ex.getMessage());
        assertTrue(future.isCancelled());
    }

    @Test
    void loadQuestionsForAllPlayersWithTimeoutExecutionExceptionPropagatesCause() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        CompletableFuture<QuestionList> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("boom"));
        setField(service, "questionPreloadStarted", true);
        setField(service, "questionLoadFuture", failed);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokeLoadQuestionsForAllPlayersWithTimeout(service));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void loadQuestionsForAllPlayersWithTimeoutInterruptedWrapsException() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        CompletableFuture<QuestionList> neverCompletes = new CompletableFuture<>();
        setField(service, "questionPreloadStarted", true);
        setField(service, "questionLoadFuture", neverCompletes);

        Thread.currentThread().interrupt();
        Exception ex;
        try {
            ex = assertThrows(Exception.class, () -> invokeLoadQuestionsForAllPlayersWithTimeout(service));
        } finally {
            Thread.interrupted();
        }

        assertEquals("Question loading interrupted", ex.getMessage());
    }

    @Test
    void initGameSuccessStartsTimeServiceAndPublishesFirstQuestion() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        QuestionList loaded = new QuestionList();
        loaded.addQuestion(createQuestion("a", QuestionStatus.INIT));

        FakeTimeService fakeTime = new FakeTimeService();
        setField(service, "timeService", fakeTime);
        setField(service, "questionPreloadStarted", true);
        setField(service, "questionLoadFuture", CompletableFuture.completedFuture(loaded));

        List<GameEvent> events = registerEventCollector(service);
        service.initGame();

        QuestionChangedEvent changed = waitForEvent(events, QuestionChangedEvent.class, 1000);
        assertNotNull(changed);
        assertEquals(0, changed.getQuestionIndex());
        assertEquals(QuestionStatus.INIT, changed.getStatus());
        assertTrue(fakeTime.started);
        assertFalse(fakeTime.stopped);
        assertEquals(GameGlobal.GameGlobalState.PLAYING, service.getGameInstance().getState());
    }

    @Test
    void initGameTimeoutPublishesTimeoutErrorAndStopsTimeService() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        FakeTimeService fakeTime = new FakeTimeService();
        setField(service, "timeService", fakeTime);
        setField(service, "questionPreloadStarted", true);
        setField(service, "questionLoadFuture", new ImmediateTimeoutFuture());

        List<GameEvent> events = registerEventCollector(service);
        service.initGame();

        QuestionLoadErrorEvent error = waitForEvent(events, QuestionLoadErrorEvent.class, 1000);
        assertNotNull(error);
        assertEquals("TIMEOUT", error.getErrorReason());
        assertTrue(fakeTime.started);
        assertTrue(fakeTime.stopped);
        assertEquals(GameGlobal.GameGlobalState.POST, service.getGameInstance().getState());
    }

    @Test
    void initGameLoadFailedPublishesLoadFailedErrorAndStopsTimeService() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        FakeTimeService fakeTime = new FakeTimeService();
        CompletableFuture<QuestionList> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("generation failed"));

        setField(service, "timeService", fakeTime);
        setField(service, "questionPreloadStarted", true);
        setField(service, "questionLoadFuture", failed);

        List<GameEvent> events = registerEventCollector(service);
        service.initGame();

        QuestionLoadErrorEvent error = waitForEvent(events, QuestionLoadErrorEvent.class, 1000);
        assertNotNull(error);
        assertEquals("LOAD_FAILED", error.getErrorReason());
        assertTrue(error.getErrorMessage().contains("generation failed"));
        assertTrue(fakeTime.started);
        assertTrue(fakeTime.stopped);
        assertEquals(GameGlobal.GameGlobalState.POST, service.getGameInstance().getState());
    }

    @Test
    void checkAndInitializeDoesNothingWhenGameIsNotInitialized() throws Exception {
        TestableGameService service = new TestableGameService();
        service.getGameInstance().setState(GameGlobal.GameGlobalState.START_VALIDATED);

        List<GameEvent> events = registerEventCollector(service);
        invokeCheckAndInitialize(service);

        CreatorInitGameEvent initEvent = waitForEvent(events, CreatorInitGameEvent.class, 250);
        assertNull(initEvent);
        assertEquals(0, service.initGameCalls);
        assertFalse((boolean) getField(service, "creatorInitEventSent"));
    }

    @Test
    void checkAndInitializePublishesCreatorInitOnlyOnceAndCancelsTimeout() throws Exception {
        TestableGameService service = new TestableGameService();
        service.getGameInstance().setState(GameGlobal.GameGlobalState.INITIALIZED);

        ManualScheduledFuture timeout = new ManualScheduledFuture();
        setField(service, "controllerReadyTimeout", timeout);

        List<GameEvent> events = registerEventCollector(service);

        invokeCheckAndInitialize(service);
        invokeCheckAndInitialize(service);

        CreatorInitGameEvent first = waitForEvent(events, CreatorInitGameEvent.class, 1000);
        assertNotNull(first);
        assertEquals(1, countEvents(events, CreatorInitGameEvent.class));
        assertTrue(timeout.cancelCalled);
        assertTrue((boolean) getField(service, "creatorInitEventSent"));
        assertEquals(2, service.initGameCalls);
    }

    @Test
    void handleTimerTickIgnoresTicksWhenStateIsNotPlaying() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setState(GameGlobal.GameGlobalState.IDLE);
        setRemainingSeconds(service.getGameInstance(), 25);

        List<GameEvent> events = registerEventCollector(service);
        invokeHandleTimerTick(service, new TimerTickEvent(0, service.getMatchId()));

        assertEquals(25, service.getGameInstance().getRemainingSeconds());
        assertEquals(0, countEvents(events, TimerTickEvent.class));
        assertEquals(0, countEvents(events, StandingsEvent.class));
        assertEquals(0, countEvents(events, GameFinishedEvent.class));
    }

    @Test
    void handleTimerTickInPlayingStatePublishesTimerAndStandings() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        service.getGameInstance().setState(GameGlobal.GameGlobalState.PLAYING);
        setRemainingSeconds(service.getGameInstance(), 5);

        List<GameEvent> events = registerEventCollector(service);
        invokeHandleTimerTick(service, new TimerTickEvent(0, service.getMatchId()));

        TimerTickEvent tick = waitForEvent(events, TimerTickEvent.class, 1000);
        StandingsEvent standings = waitForEvent(events, StandingsEvent.class, 1000);

        assertNotNull(tick);
        assertNotNull(standings);
        assertEquals(4, tick.getRemainingSeconds());
        assertEquals(4, service.getGameInstance().getRemainingSeconds());
        assertEquals(0, countEvents(events, GameFinishedEvent.class));
    }

    @Test
    void handleTimerTickFinishesGameWhenTimeRunsOut() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        FakeTimeService fakeTime = new FakeTimeService();
        setField(service, "timeService", fakeTime);
        service.getGameInstance().setState(GameGlobal.GameGlobalState.PLAYING);
        setRemainingSeconds(service.getGameInstance(), 1);

        List<GameEvent> events = registerEventCollector(service);
        invokeHandleTimerTick(service, new TimerTickEvent(0, service.getMatchId()));

        GameFinishedEvent finished = waitForEvent(events, GameFinishedEvent.class, 1000);
        assertNotNull(finished);
        assertEquals(0, service.getGameInstance().getRemainingSeconds());
        assertEquals(GameGlobal.GameGlobalState.POST, service.getGameInstance().getState());
        assertTrue(fakeTime.stopped);
    }

    @Test
    void handlePlayerRoscoFinishedAwardsBonusOnlyOncePerPlayer() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        GameInstance instance = service.getGameInstance().getPlayerInstance("p1");
        setRemainingSeconds(service.getGameInstance(), 7);

        List<GameEvent> events = registerEventCollector(service);

        invokeHandlePlayerRoscoFinished(service, "p1", instance);
        invokeHandlePlayerRoscoFinished(service, "p1", instance);

        ExtraTimeScoreEvent extra = waitForEvent(events, ExtraTimeScoreEvent.class, 1000);
        assertNotNull(extra);
        assertEquals(70, extra.getExtraTimeScore());
        assertEquals(70, instance.getTotalScore());
        assertEquals(1, countEvents(events, ExtraTimeScoreEvent.class));
        assertEquals(GameInstance.GameState.FINISHED, instance.getGameInstanceState());
        assertFalse(service.shouldReceiveTimerTick("p1"));
    }

    @Test
    void onGlobalEventIgnoresTimerTicksForOtherMatches() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));
        service.getGameInstance().setState(GameGlobal.GameGlobalState.PLAYING);
        setRemainingSeconds(service.getGameInstance(), 12);

        List<GameEvent> events = registerEventCollector(service);
        invokeOnGlobalEvent(service, new TimerTickEvent(11, "other-match"));

        assertEquals(12, service.getGameInstance().getRemainingSeconds());
        assertEquals(0, countEvents(events, TimerTickEvent.class));
        assertEquals(0, countEvents(events, StandingsEvent.class));
    }

    @Test
    void onExternalEventGameControllerReadyTransitionsReadinessAndDoesNotEmitErrors() throws Exception {
        GameService service = new GameService();
        service.getGameInstance().setMaxPlayers(1);
        assertTrue(service.addPlayerToGame("p1"));

        List<GameEvent> events = registerEventCollector(service);
        invokeOnExternalEvent(service, new GameControllerReady("p1", service.getMatchId()));

        assertEquals(GameGlobal.GameGlobalState.CONTROLLER_READY, service.getGameInstance().getState());
        assertEquals(0, countEvents(events, QuestionLoadErrorEvent.class));
        assertEquals(0, countEvents(events, AnswerValidatedEvent.class));
    }

    private static int invokeCalculateAnswerScore(GameService service, Question question, QuestionStatus status)
            throws Exception {
        Method method = GameService.class.getDeclaredMethod("calculateAnswerScore", Question.class, QuestionStatus.class);
        method.setAccessible(true);
        return (int) method.invoke(service, question, status);
    }

    private static int invokeFindNextUnansweredIndexCircular(GameService service, QuestionList list, int currentIndex)
            throws Exception {
        Method method = GameService.class.getDeclaredMethod("findNextUnansweredIndexCircular", QuestionList.class,
                int.class);
        method.setAccessible(true);
        return (int) method.invoke(service, list, currentIndex);
    }

    private static void invokeHandleAnswerSubmitted(GameService service, AnswerSubmittedEvent event) throws Exception {
        Method method = GameService.class.getDeclaredMethod("handleAnswerSubmitted", AnswerSubmittedEvent.class);
        method.setAccessible(true);
        try {
            method.invoke(service, event);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    private static void invokeLoadQuestionsForAllPlayersWithTimeout(GameService service) throws Exception {
        Method method = GameService.class.getDeclaredMethod("loadQuestionsForAllPlayersWithTimeout");
        method.setAccessible(true);
        try {
            method.invoke(service);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    private static void invokeCheckAndInitialize(GameService service) throws Exception {
        Method method = GameService.class.getDeclaredMethod("checkAndInitialize");
        method.setAccessible(true);
        try {
            method.invoke(service);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    private static void invokeHandleTimerTick(GameService service, TimerTickEvent event) throws Exception {
        Method method = GameService.class.getDeclaredMethod("handleTimerTick", TimerTickEvent.class);
        method.setAccessible(true);
        try {
            method.invoke(service, event);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    private static void invokeHandlePlayerRoscoFinished(GameService service, String playerId, GameInstance instance)
            throws Exception {
        Method method = GameService.class.getDeclaredMethod("handlePlayerRoscoFinished", String.class,
                GameInstance.class);
        method.setAccessible(true);
        try {
            method.invoke(service, playerId, instance);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    private static void invokeOnGlobalEvent(GameService service, GameEvent event) throws Exception {
        Method method = GameService.class.getDeclaredMethod("onGlobalEvent", GameEvent.class);
        method.setAccessible(true);
        try {
            method.invoke(service, event);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    private static void invokeOnExternalEvent(GameService service, GameEvent event) throws Exception {
        Method method = GameService.class.getDeclaredMethod("onExternalEvent", GameEvent.class);
        method.setAccessible(true);
        try {
            method.invoke(service, event);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    private static Exception unwrap(InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof Exception ex) {
            return ex;
        }
        return new Exception(cause);
    }

    private static void setField(GameService service, String fieldName, Object value) throws Exception {
        Field field = GameService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static Object getField(GameService service, String fieldName) throws Exception {
        Field field = GameService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(service);
    }

    private static void setRemainingSeconds(GameGlobal gameGlobal, int seconds) throws Exception {
        Field field = GameGlobal.class.getDeclaredField("remainingSeconds");
        field.setAccessible(true);
        field.setInt(gameGlobal, seconds);
    }

    private static List<GameEvent> registerEventCollector(GameService service) {
        List<GameEvent> events = Collections.synchronizedList(new ArrayList<>());
        EventListener listener = event -> {
            synchronized (events) {
                events.add(event);
                events.notifyAll();
            }
        };
        service.addListener(listener);
        return events;
    }

    private static <T extends GameEvent> T waitForEvent(List<GameEvent> events, Class<T> eventType, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        synchronized (events) {
            while (System.nanoTime() < deadline) {
                for (GameEvent event : events) {
                    if (eventType.isInstance(event)) {
                        return eventType.cast(event);
                    }
                }

                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMs <= 0) {
                    break;
                }
                events.wait(remainingMs);
            }

            for (GameEvent event : events) {
                if (eventType.isInstance(event)) {
                    return eventType.cast(event);
                }
            }
        }
        return null;
    }

    private static int countEvents(List<GameEvent> events, Class<? extends GameEvent> eventType) {
        synchronized (events) {
            int count = 0;
            for (GameEvent event : events) {
                if (eventType.isInstance(event)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static Question createQuestion(String letter, QuestionStatus status) {
        Question question = new Question(
                "Pregunta " + letter,
                List.of("r1", "r2", "r3", "r4"),
                0,
                status,
                QuestionLevel.MEDIUM,
                letter,
                status.getValue());
        question.setQuestionStatus(status);
        question.setUserResponseRecorded(status.getValue());
        return question;
    }

    private static final class FakeTimeService extends TimeService {
        private boolean started;
        private boolean stopped;

        @Override
        public synchronized void start() {
            started = true;
        }

        @Override
        public synchronized void stop() {
            stopped = true;
        }
    }

    private static final class ImmediateTimeoutFuture extends CompletableFuture<QuestionList> {
        @Override
        public QuestionList get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            throw new TimeoutException("forced-timeout");
        }
    }

    private static final class ManualScheduledFuture implements ScheduledFuture<Object> {
        private boolean cancelCalled;
        private boolean done;

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed o) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelCalled = true;
            done = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelCalled;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }

    private static final class TestableGameService extends GameService {
        private int initGameCalls;

        @Override
        public void initGame() {
            initGameCalls++;
        }
    }
}
