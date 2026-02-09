package UE_Proyecto_Ingenieria.Apalabrazos.ejemplos;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.PlayerState;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.MockMessageSender;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flujo completo sin dependencias externas.
 * Demuestra cómo funciona el puente de WebSocket → Player → GameSessionManager.
 */
public class WebSocketFlowSimulation {

    // Simulamos el GameSessionManager sin usar el real (que tiene dependencias logging)
    private static Map<UUID, Player> activeConnections = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("=== WebSocket Connection Flow ===\n");

        // NIVEL 1: Simulación de cliente conectando a WebSocket
        System.out.println("┌─ NIVEL 1: WebSocket Server recibe conexión");
        Object fakeWebSocketSession = new Object();
        simulate_onWebSocketConnect(fakeWebSocketSession, "Alice");

        Player alice = activeConnections.values().iterator().next();
        UUID aliceSessionId = alice.getSessionId();

        System.out.println("│  ✓ Sesión creada para Alice");
        System.out.println("│  • SessionID: " + aliceSessionId);
        System.out.println("│  • Estado: " + alice.getState());
        System.out.println("│  • Conectado: " + alice.isConnected());

        // NIVEL 2: El cliente envía datos
        System.out.println("│\n├─ NIVEL 2: Cliente envía mensaje");
        System.out.println("│  Recibido: {\"action\": \"createGame\", \"difficulty\": \"HARD\"}");
        System.out.println("│  → GameSessionManager.onEvent() procesa en virtual thread");

        // NIVEL 3: Notificación a cliente
        System.out.println("│\n├─ NIVEL 3: Respuesta al cliente");
        alice.sendMessage("Partida creada");
        alice.sendMessage("Esperando jugadores...");
        System.out.println("│  ✓ 2 mensajes encolados en MockMessageSender");
        System.out.println("│  ✓ MockMessageSender.getMessageCount() = " +
                          ((MockMessageSender)alice.getSender()).getMessageCount());

        // NIVEL 4: Segundo cliente se conecta
        System.out.println("│\n├─ NIVEL 4: Segundo cliente conecta");
        Object fakeSession2 = new Object();
        simulate_onWebSocketConnect(fakeSession2, "Bob");

        Player bob = null;
        for (Player p : activeConnections.values()) {
            if (p.getName().equals("Bob")) {
                bob = p;
                break;
            }
        }

        System.out.println("│  ✓ Bob conectado (Sesiones activas: " + activeConnections.size() + ")");

        // Simulación de evento de partida
        System.out.println("│\n├─ Simulación: GameService lanza GameStartedEvent");
        System.out.println("│  → AsyncEventBus ejecuta listeners en virtual threads");
        alice.sendMessage("Turno de Alice - Elige una letra");
        bob.sendMessage("Turno de Bob - Esperando...");
        alice.sendMessage("Pregunta: ¿Cuál es la capital de Francia?");

        // Cliente responde
        System.out.println("│\n├─ Alice responde");
        System.out.println("│  {\"action\": \"answer\", \"letter\": \"P\"}");
        alice.sendMessage("✓ ¡Correcto! Letra P - París +10 puntos");
        bob.sendMessage("Alice encontró Paris");

        // Desconexión
        System.out.println("│\n└─ DESCONEXIÓN");
        simulate_onWebSocketDisconnect(aliceSessionId);
        simulate_onWebSocketDisconnect(bob.getSessionId());

        System.out.println("   ✓ Alice desconectada (Estado: " + alice.getState() + ")");
        System.out.println("   ✓ Bob desconectado");
        System.out.println("   ✓ Sesiones activas: " + activeConnections.size());

        // Mostrar mensajes que se enviaron
        System.out.println("\n=== Mensajes enviados a Alice ===");
        MockMessageSender aliceSender = (MockMessageSender) alice.getSender();
        for (int i = 0; i < aliceSender.getSentMessages().size(); i++) {
            System.out.println((i+1) + ". " + aliceSender.getSentMessages().get(i));
        }

        System.out.println("\n=== Mensajes enviados a Bob ===");
        MockMessageSender bobSender = (MockMessageSender) bob.getSender();
        for (int i = 0; i < bobSender.getSentMessages().size(); i++) {
            System.out.println((i+1) + ". " + bobSender.getSentMessages().get(i));
        }

        System.out.println("\n✓ Flujo completo simulado exitosamente");
    }

    // Simulación de eventos WebSocket

    private static void simulate_onWebSocketConnect(Object session, String username) {
        UUID sessionId = UUID.randomUUID();
        MockMessageSender sender = new MockMessageSender();
        Player player = new Player(sessionId, username, sender);
        activeConnections.put(sessionId, player);
        player.sendMessage("Welcome, " + username + "!");
    }

    private static void simulate_onWebSocketDisconnect(UUID sessionId) {
        Player player = activeConnections.remove(sessionId);
        if (player != null) {
            player.disconnect();
        }
    }
}
