package Apalabrazos.backend.events;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de los eventos que circulan por el {@link GlobalAsyncEventBus}.
 *
 * <p>Su objetivo es dejar explícito qué tipo de mensajes se publican en el bus
 * global, quién suele emitirlos y qué componente backend debe reaccionar a
 * ellos. De esta forma el flujo interno queda más ordenado y se evitan
 * consumidores duplicados.</p>
 */
public final class GlobalBusEventCatalog {

    public enum Category {
        MATCH_COMMAND,
        MATCH_QUERY,
        MATCH_TIMER,
        MATCH_NOTIFICATION
    }

    public record Route(Category category, List<String> emitters, List<String> receivers, String notes) {
    }

    private static final String MATCH_MANAGER = "MatchManager";
    private static final String GAME_SERVICE = "GameService";

    private static final Map<Class<? extends GameEvent>, Route> ROUTES = Map.of(
            GameCreationRequestedEvent.class,
            new Route(
                    Category.MATCH_COMMAND,
                    List.of("JavalinConnectionHandler"),
                    List.of(MATCH_MANAGER),
                    "Solicitud de creación de partida recibida desde WebSocket"),
            GameStartedRequestEvent.class,
            new Route(
                    Category.MATCH_COMMAND,
                    List.of("JavalinConnectionHandler"),
                    List.of(MATCH_MANAGER),
                    "Petición para validar el inicio de una partida"),
            PlayerJoinedEvent.class,
            new Route(
                    Category.MATCH_COMMAND,
                    List.of("MatchManager.joinPlayerToMatch"),
                    List.of(MATCH_MANAGER),
                    "Comando interno de unión que MatchManager enruta a la partida correcta"),
            GetMatchInfoEvent.class,
            new Route(
                    Category.MATCH_QUERY,
                    List.of("Backend internal callers"),
                    List.of(MATCH_MANAGER),
                    "Consulta interna para resolver una partida activa"),
            TimerTickEvent.class,
            new Route(
                    Category.MATCH_TIMER,
                    List.of("TimeService"),
                    List.of(GAME_SERVICE),
                    "Tick global del reloj que solo debe consumir la partida propietaria"),
            GameMatchCreatedEvent.class,
            new Route(
                    Category.MATCH_NOTIFICATION,
                    List.of("MatchManager"),
                    List.of(),
                    "Notificación interna publicada en el bus global; actualmente no tiene consumidor backend registrado"));

    private GlobalBusEventCatalog() {
    }

    public static Optional<Route> describe(GameEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        return describe(event.getClass());
    }

    public static Optional<Route> describe(Class<? extends GameEvent> eventType) {
        return Optional.ofNullable(ROUTES.get(eventType));
    }

    public static boolean isHandledByMatchManager(GameEvent event) {
        return describe(event)
                .map(route -> route.receivers().contains(MATCH_MANAGER))
                .orElse(false);
    }

    public static boolean isHandledByGameService(GameEvent event) {
        return describe(event)
                .map(route -> route.receivers().contains(GAME_SERVICE))
                .orElse(false);
    }
}
