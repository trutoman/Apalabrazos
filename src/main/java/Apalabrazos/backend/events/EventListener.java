package Apalabrazos.backend.events;

/**
 * Interfaz para objetos que quieren recibir eventos del bus asíncrono.
 * Cualquier clase que implemente esta interfaz puede registrarse
 * en el bus de eventos y recibir notificaciones cuando ocurren.
 */
public interface EventListener {

    /**
     * Método que se llama cuando ocurre un evento.
     * La clase que implementa esta interfaz debe decidir
     * qué hacer con cada tipo de evento.
     *
     * @param event El evento que ha ocurrido
     */
    void onEvent(GameEvent event);
}
