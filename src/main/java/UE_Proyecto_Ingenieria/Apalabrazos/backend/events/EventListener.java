package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

/**
 * Interfaz para objetos que quieren recibir eventos del EventBus.
 * Cualquier clase que implemente esta interfaz puede registrarse
 * en el EventBus y recibir notificaciones de eventos.
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
