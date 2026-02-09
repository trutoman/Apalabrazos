package UE_Proyecto_Ingenieria.Apalabrazos.ejemplos;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.PlayerState;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.MessageSender;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.MockMessageSender;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.GameSessionManager;

import java.util.UUID;

/**
 * Ejemplo de cómo usar la arquitectura de Player como "ancla" del sistema.
 * Demuestra el ciclo de vida completo de un jugador: conexión, juego, desconexión.
 */
public class PlayerArchitectureExample {

    public static void main(String[] args) {
        // 1. NIVEL 1: Nueva conexión física (WebSocket)
        // En producción, esto vendría del ConnectionHandler
        MockMessageSender mockSender = new MockMessageSender();
        UUID sessionId = UUID.randomUUID();

        // 2. NIVEL 2: Crear objeto Player (el ancla)
        Player player = new Player(sessionId, "Alice", mockSender);
        System.out.println("✓ Player creado: " + player.getName());
        System.out.println("  SessionID: " + player.getSessionId());
        System.out.println("  Estado inicial: " + player.getState());

        // 3. Registrar en el GameSessionManager (singleton)
        GameSessionManager sessionManager = GameSessionManager.getInstance();
        sessionManager.registerConnection(player);
        System.out.println("✓ Player registrado en SessionManager");
        System.out.println("  Conexiones activas: " + sessionManager.getActiveConnectionCount());

        // 4. El jugador está en el lobby, enviarle mensaje de bienvenida
        player.sendMessage("¡Bienvenido, " + player.getName() + "!");
        System.out.println("✓ Mensaje enviado al jugador");
        System.out.println("  Mensajes en mock: " + mockSender.getMessageCount());

        // 5. El jugador se une a una partida
        UUID matchId = UUID.randomUUID();
        player.setState(PlayerState.PLAYING);
        player.setCurrentMatchId(matchId);
        System.out.println("✓ Jugador se unió a partida");
        System.out.println("  Estado: " + player.getState());
        System.out.println("  En partida: " + player.isInMatch());

        // 6. NIVEL 4: El Match envía eventos al jugador
        player.sendMessage("Tu turno - Pregunta #1");
        player.sendMessage("¡Respuesta correcta! +10 puntos");
        System.out.println("✓ Mensajes de partida enviados");
        System.out.println("  Total mensajes: " + mockSender.getMessageCount());

        // 7. Partida termina
        player.setState(PlayerState.LOBBY);
        player.setCurrentMatchId(null);
        player.sendMessage("¡Partida terminada! Puntuación: 100");
        System.out.println("✓ Partida finalizada");

        // 8. El jugador se desconecta
        sessionManager.unregisterConnection(sessionId);
        System.out.println("✓ Jugador desconectado");
        System.out.println("  Conexiones activas: " + sessionManager.getActiveConnectionCount());
        System.out.println("  Estado final del jugador: " + player.getState());

        // 9. Verificar todos los mensajes enviados (útil para tests)
        System.out.println("\n=== Mensajes enviados al cliente ===");
        for (int i = 0; i < mockSender.getSentMessages().size(); i++) {
            System.out.println((i + 1) + ". " + mockSender.getSentMessages().get(i));
        }

        demonstrateMultipleConnections();
    }

    /**
     * Demuestra múltiples jugadores conectados simultáneamente
     */
    private static void demonstrateMultipleConnections() {
        System.out.println("\n=== Demostración Multi-Jugador ===");

        GameSessionManager manager = GameSessionManager.getInstance();
        manager.clearAllSessions(); // Limpiar del ejemplo anterior

        // Crear 3 jugadores
        Player alice = new Player(UUID.randomUUID(), "Alice", new MockMessageSender());
        Player bob = new Player(UUID.randomUUID(), "Bob", new MockMessageSender());
        Player charlie = new Player(UUID.randomUUID(), "Charlie", new MockMessageSender());

        // Registrarlos
        manager.registerConnection(alice);
        manager.registerConnection(bob);
        manager.registerConnection(charlie);

        System.out.println("✓ " + manager.getActiveConnectionCount() + " jugadores conectados");

        // Broadcast a todos
        manager.broadcastToAll("¡Servidor iniciando mantenimiento en 5 minutos!");
        System.out.println("✓ Broadcast enviado a todos los jugadores");

        // Mensaje específico a Bob
        manager.sendToPlayer(bob.getSessionId(), "Bob, tienes un mensaje privado");
        System.out.println("✓ Mensaje privado enviado a Bob");

        // Listar todos los jugadores
        System.out.println("\nJugadores conectados:");
        for (Player p : manager.getAllConnectedPlayers()) {
            System.out.println("  - " + p.getName() + " (" + p.getState() + ")");
        }
    }
}
