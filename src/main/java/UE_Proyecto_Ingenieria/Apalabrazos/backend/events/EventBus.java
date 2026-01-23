package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EventBus simplificado para comunicación entre componentes del juego.
 * Permite publicar eventos y recibir notificaciones cuando ocurren.
 *
 * Cada instancia es independiente. Se puede crear una instancia global compartida
 * o instancias locales según sea necesario.
 */
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    // Listeners que escuchan diferentes tipos de eventos
    private List<EventListener> listeners;

    /**
     * Constructor público para crear instancias independientes del bus
     */
    public EventBus() {
        this.listeners = new ArrayList<>();
    }

    /**
     * Registrar un listener para recibir eventos
     * @param listener El objeto que quiere recibir eventos
     */
    public void addListener(EventListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Quitar un listener para dejar de recibir eventos
     * @param listener El objeto que ya no quiere recibir eventos
     */
    public void removeListener(EventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Publicar un evento para que todos los listeners lo reciban
     * @param event El evento que ha ocurrido
     */
    public void publish(GameEvent event) {
        // Notificar a todos los listeners
        for (EventListener listener : new ArrayList<>(listeners)) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("Error processing event: {}", e.getMessage(), e);

            }
        }
    }

    /**
     * Limpiar todos los listeners (útil para testing o reset)
     */
    public void clear() {
        listeners.clear();
    }
}
