package Apalabrazos.backend.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalBusEventCatalogTest {

    @Test
    void shouldClassifyMatchManagerCommandEvents() {
        GlobalBusEventCatalog.Route route = GlobalBusEventCatalog.describe(
                new PlayerJoinedEvent("player-1", "match-1", "Alice"))
                .orElseThrow();

        assertEquals(GlobalBusEventCatalog.Category.MATCH_COMMAND, route.category());
        assertTrue(route.emitters().contains("MatchManager.joinPlayerToMatch"));
        assertTrue(route.receivers().contains("MatchManager"));
        assertTrue(GlobalBusEventCatalog.isHandledByMatchManager(new PlayerJoinedEvent("player-1", "match-1")));
        assertFalse(GlobalBusEventCatalog.isHandledByGameService(new PlayerJoinedEvent("player-1", "match-1")));
    }

    @Test
    void shouldClassifyTimerTicksAsGameServiceGlobalEvents() {
        GlobalBusEventCatalog.Route route = GlobalBusEventCatalog.describe(new TimerTickEvent(0, "match-1"))
                .orElseThrow();

        assertEquals(GlobalBusEventCatalog.Category.MATCH_TIMER, route.category());
        assertTrue(route.emitters().contains("TimeService"));
        assertTrue(route.receivers().contains("GameService"));
        assertTrue(GlobalBusEventCatalog.isHandledByGameService(new TimerTickEvent(0, "match-1")));
        assertFalse(GlobalBusEventCatalog.isHandledByMatchManager(new TimerTickEvent(0, "match-1")));
    }

    @Test
    void shouldExposeGlobalNotificationsWithoutRegisteredReceiver() {
        GlobalBusEventCatalog.Route route = GlobalBusEventCatalog.describe(GameMatchCreatedEvent.class)
                .orElseThrow();

        assertEquals(GlobalBusEventCatalog.Category.MATCH_NOTIFICATION, route.category());
        assertTrue(route.receivers().isEmpty());
        assertTrue(route.notes().contains("no tiene consumidor backend registrado"));
    }
}
