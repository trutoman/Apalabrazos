package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import java.util.ArrayList;
import java.util.List;

/**
 * EventBus simplificado para comunicación entre componentes del juego.
 * Permite publicar eventos y recibir notificaciones cuando ocurren.
 *
 * Patrón Singleton: solo existe una instancia compartida en toda la aplicación.
 */
public class EventBus {

    // La única instancia del EventBus (patrón Singleton)
    private static EventBus instance;

    // Listeners que escuchan diferentes tipos de eventos
    private List<EventListener> listeners;

    /**
     * Constructor privado para evitar crear múltiples instancias
     */
    private EventBus() {
        this.listeners = new ArrayList<>();
    }

    /**
     * Obtener la única instancia del EventBus
     * @return La instancia compartida del EventBus
     */
    public static EventBus getInstance() {
        if (instance == null) {
            instance = new EventBus();
        }
        return instance;
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
                System.err.println("Error al procesar evento: " + e.getMessage());
                e.printStackTrace();
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
