package UE_Proyecto_Ingenieria.Apalabrazos.backend.network;

/**
 * Interfaz para abstraer el envío de mensajes a un cliente.
 * Permite que la lógica de juego sea independiente del medio de comunicación (WebSocket, HTTP, etc.)
 * y facilita el testing mediante mocks.
 *
 * El ConnectionHandler del servidor implementará esta interfaz usando WebSockets.
 * En tests, se puede crear un Mock que guarde mensajes en una lista para verificación.
 */
public interface MessageSender {

    /**
     * Envía un mensaje al cliente conectado
     * @param message El mensaje a enviar (será serializado según la implementación)
     */
    void send(Object message);

    /**
     * Verifica si la conexión está activa
     * @return true si el canal de comunicación está abierto
     */
    boolean isConnected();

    /**
     * Cierra la conexión con el cliente
     */
    void close();
}
