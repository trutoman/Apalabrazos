package UE_Proyecto_Ingenieria.Apalabrazos.ejemplos;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.ConnectionHandler;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameSessionManager;

import java.util.UUID;

/**
 * Ejemplo que simula el flujo completo desde WebSocket hasta Game Session.
 *
 * Demuestra:
 * 1. Nivel 1: Conexión física (simulada)
 * 2. Nivel 2: Creación de Player y registro en GameSessionManager
 * 3. Nivel 3: Envío de mensajes a través del canal abstracto
 * 4. Nivel 4: Lógica de juego (simulada)
 */
public class FullConnectionFlowExample {

    public static void main(String[] args) {
        System.out.println("=== Full Connection Flow Example ===\n");

        // Crear un ConnectionHandler simulado
        SimulatedConnectionHandler handler = new SimulatedConnectionHandler();

        // NIVEL 1: Simulación de cliente conectándose
        System.out.println("↓ NIVEL 1: Cliente conecta al WebSocket");
        Object fakeWebSocketSession = new Object(); // Simular sesión WebSocket
        handler.onOpen(fakeWebSocketSession, "Alice");

        // Obtener el Player creado
        Player alice = GameSessionManager.getInstance()
            .getAllConnectedPlayers().get(0);
        UUID aliceSessionId = alice.getSessionId();
        System.out.println("  ✓ SessionID asignado: " + aliceSessionId);
        System.out.println("  ✓ Estado: " + alice.getState());
        System.out.println("  ✓ Conectado: " + alice.isConnected());

        // NIVEL 2: El cliente envía un mensaje (crear partida)
        System.out.println("\n↓ NIVEL 2: Cliente envía mensaje 'crear partida'");
        handler.onMessage(aliceSessionId,
                         "{\"action\": \"createGame\", \"difficulty\": \"HARD\"}");

        // NIVEL 3: Broadcast a todos los clientes
        System.out.println("\n↓ NIVEL 3: Servidor notifica a todos");
        handler.broadcastToAll("Partida creada por Alice");

        // NIVEL 4: Simulación de evento de juego
        System.out.println("\n↓ NIVEL 4: Event Bus publica GameStartedEvent");
        System.out.println("  → AsyncEventBus ejecuta listeners en virtual threads");
        alice.sendMessage("Tu turno - ¡Elige una letra!");
        alice.sendMessage("Pregunta: ¿Cuál es la capital de Francia?");

        // Cliente responde
        System.out.println("\n↓ Cliente responde");
        handler.onMessage(aliceSessionId,
                         "{\"action\": \"answer\", \"letter\": \"P\"}");
        alice.sendMessage("¡Correcto! +10 puntos");

        // Simular otro cliente
        System.out.println("\n\n=== Segundo Cliente ===");
        Object fakeSession2 = new Object();
        handler.onOpen(fakeSession2, "Bob");

        Player bob = null;
        for (Player p : GameSessionManager.getInstance().getAllConnectedPlayers()) {
            if (p.getName().equals("Bob")) {
                bob = p;
                break;
            }
        }

        System.out.println("✓ Bob conectado (SessionID: " + bob.getSessionId() + ")");
        System.out.println("✓ Jugadores conectados: " +
                          GameSessionManager.getInstance().getActiveConnectionCount());

        // Desconexión
        System.out.println("\n\n=== Desconexión ===");
        handler.onClose(aliceSessionId);
        handler.onClose(bob.getSessionId());
        System.out.println("✓ Jugadores conectados: " +
                          GameSessionManager.getInstance().getActiveConnectionCount());

        System.out.println("\n=== Flujo Completado ===");
    }

    /**
     * Implementación simulada de ConnectionHandler para demostración
     */
    static class SimulatedConnectionHandler extends ConnectionHandler {

        public void onOpen(Object session, String username) {
            onClientConnect(session, username);
        }

        public void onMessage(UUID sessionId, String messageContent) {
            onClientMessage(sessionId, messageContent);
        }

        public void onClose(UUID sessionId) {
            onClientDisconnect(sessionId);
        }
    }
}
