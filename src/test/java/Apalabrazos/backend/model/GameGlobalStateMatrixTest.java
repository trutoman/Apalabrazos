package Apalabrazos.backend.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameGlobalStateMatrixTest {

    @Test
    void singlePlayer_readyThenStartValidated_reachesInitialized() {
        GameGlobal game = gameWithPlayers(1);

        boolean initializedOnReady = game.transitionControllerReady("p1");
        boolean initializedOnStartValidated = game.transitionStartValidated();

        assertFalse(initializedOnReady);
        assertTrue(initializedOnStartValidated);
        assertEquals(GameGlobal.GameGlobalState.INITIALIZED, game.getState());
        assertTrue(game.isGameInitialized());
    }

    @Test
    void singlePlayer_startValidatedThenReady_reachesInitialized() {
        GameGlobal game = gameWithPlayers(1);

        boolean initializedOnStartValidated = game.transitionStartValidated();
        boolean initializedOnReady = game.transitionControllerReady("p1");

        assertFalse(initializedOnStartValidated);
        assertTrue(initializedOnReady);
        assertEquals(GameGlobal.GameGlobalState.INITIALIZED, game.getState());
        assertTrue(game.isGameInitialized());
    }

    @Test
    void twoPlayers_startValidatedThenAllReady_reachesInitializedOnlyAfterSecondReady() {
        GameGlobal game = gameWithPlayers(2);

        boolean initializedOnStartValidated = game.transitionStartValidated();
        boolean initializedAfterFirstReady = game.transitionControllerReady("p1");
        boolean initializedAfterSecondReady = game.transitionControllerReady("p2");

        assertFalse(initializedOnStartValidated);
        assertFalse(initializedAfterFirstReady);
        assertTrue(initializedAfterSecondReady);
        assertEquals(GameGlobal.GameGlobalState.INITIALIZED, game.getState());
    }

    @Test
    void twoPlayers_allReadyThenStartValidated_reachesInitializedOnlyAfterStartValidated() {
        GameGlobal game = gameWithPlayers(2);

        boolean initializedAfterFirstReady = game.transitionControllerReady("p1");
        boolean initializedAfterSecondReady = game.transitionControllerReady("p2");
        boolean initializedOnStartValidated = game.transitionStartValidated();

        assertFalse(initializedAfterFirstReady);
        assertFalse(initializedAfterSecondReady);
        assertTrue(initializedOnStartValidated);
        assertEquals(GameGlobal.GameGlobalState.INITIALIZED, game.getState());
    }

    @Test
    void repeatedControllerReadyForSamePlayer_doesNotAdvanceUntilAllRegisteredPlayersAreReady() {
        GameGlobal game = gameWithPlayers(2);

        boolean firstCall = game.transitionControllerReady("p1");
        boolean secondCall = game.transitionControllerReady("p1");

        assertFalse(firstCall);
        assertFalse(secondCall);
        assertEquals(GameGlobal.GameGlobalState.IDLE, game.getState());

        boolean callWithSecondPlayer = game.transitionControllerReady("p2");

        assertFalse(callWithSecondPlayer);
        assertEquals(GameGlobal.GameGlobalState.CONTROLLER_READY, game.getState());
    }

    @Test
    void repeatedTransitionStartValidated_keepsStateAndDoesNotInitialize() {
        GameGlobal game = gameWithPlayers(2);

        boolean firstCall = game.transitionStartValidated();
        boolean secondCall = game.transitionStartValidated();

        assertFalse(firstCall);
        assertFalse(secondCall);
        assertEquals(GameGlobal.GameGlobalState.START_VALIDATED, game.getState());
    }

    @Test
    void unregisteredPlayerReady_doesNotInitializeByItself() {
        GameGlobal game = gameWithPlayers(2);

        boolean initialized = game.transitionControllerReady("ghost");

        assertFalse(initialized);
        assertEquals(GameGlobal.GameGlobalState.IDLE, game.getState());
    }

    @Test
    void unregisteredPlayerReady_doesNotBlockLaterInitializationWhenRealPlayersBecomeReady() {
        GameGlobal game = gameWithPlayers(2);

        assertFalse(game.transitionStartValidated());
        assertFalse(game.transitionControllerReady("ghost"));
        assertFalse(game.transitionControllerReady("p1"));

        boolean initialized = game.transitionControllerReady("p2");

        assertTrue(initialized);
        assertEquals(GameGlobal.GameGlobalState.INITIALIZED, game.getState());
    }

    @Test
    void noPlayers_neverTransitionsToControllerReadyOrInitialized() {
        GameGlobal game = new GameGlobal();

        boolean initializedOnReady = game.transitionControllerReady("any");
        boolean initializedOnStartValidated = game.transitionStartValidated();

        assertFalse(initializedOnReady);
        assertFalse(initializedOnStartValidated);
        assertEquals(GameGlobal.GameGlobalState.START_VALIDATED, game.getState());
    }

    @Test
    void onceInitialized_subsequentTransitionCallsAreIdempotent() {
        GameGlobal game = gameWithPlayers(1);
        assertFalse(game.transitionStartValidated());
        assertTrue(game.transitionControllerReady("p1"));
        assertEquals(GameGlobal.GameGlobalState.INITIALIZED, game.getState());

        boolean onRepeatedStartValidated = game.transitionStartValidated();
        boolean onRepeatedControllerReady = game.transitionControllerReady("p1");

        assertFalse(onRepeatedStartValidated);
        assertFalse(onRepeatedControllerReady);
        assertEquals(GameGlobal.GameGlobalState.INITIALIZED, game.getState());
    }

    @Test
    void concurrentControllerReadyAndStartValidatedConvergeToInitializedOnce() throws Exception {
        GameGlobal game = gameWithPlayers(2);

        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Callable<Boolean> startValidatedTask = () -> {
                ready.await();
                return game.transitionStartValidated();
            };
            Callable<Boolean> firstPlayerTask = () -> {
                ready.await();
                return game.transitionControllerReady("p1");
            };
            Callable<Boolean> secondPlayerTask = () -> {
                ready.await();
                return game.transitionControllerReady("p2");
            };

            Future<Boolean> startValidated = executor.submit(startValidatedTask);
            Future<Boolean> firstPlayer = executor.submit(firstPlayerTask);
            Future<Boolean> secondPlayer = executor.submit(secondPlayerTask);

            ready.countDown();

            List<Boolean> results = List.of(startValidated.get(), firstPlayer.get(), secondPlayer.get());

            assertEquals(GameGlobal.GameGlobalState.INITIALIZED, game.getState());
            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            assertTrue(game.isGameInitialized());
        } finally {
            executor.shutdownNow();
        }
    }

    private static GameGlobal gameWithPlayers(int count) {
        GameGlobal game = new GameGlobal();
        for (int i = 1; i <= count; i++) {
            game.addPlayerInstance("p" + i, new GameInstance());
        }
        return game;
    }
}
