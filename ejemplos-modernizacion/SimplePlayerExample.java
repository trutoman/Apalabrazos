package UE_Proyecto_Ingenieria.Apalabrazos.ejemplos;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.Player;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.PlayerState;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.network.MockMessageSender;

import java.util.UUID;

/**
 * Ejemplo simplificado de la arquitectura Player.
 * Demuestra el concepto sin dependencias externas.
 */
public class SimplePlayerExample {

    public static void main(String[] args) {
        System.out.println("=== Player Architecture Demo ===\n");

        // 1. Nueva conexión - crear MessageSender mock
        MockMessageSender mockSender = new MockMessageSender();
        UUID sessionId = UUID.randomUUID();

        // 2. Crear Player (el ancla de la arquitectura)
        Player player = new Player(sessionId, "Alice", mockSender);
        System.out.println("✓ Player creado: " + player.getName());
        System.out.println("  SessionID: " + player.getSessionId());
        System.out.println("  PlayerID: " + player.getPlayerID());
        System.out.println("  Estado: " + player.getState());
        System.out.println("  Conectado: " + player.isConnected());

        // 3. Enviar mensaje de bienvenida
        player.sendMessage("¡Bienvenido, " + player.getName() + "!");
        System.out.println("\n✓ Mensaje de bienvenida enviado");

        // 4. El jugador entra en matchmaking
        player.setState(PlayerState.MATCHMAKING);
        player.sendMessage("Buscando partida...");
        System.out.println("✓ Estado cambiado a MATCHMAKING");

        // 5. El jugador se une a una partida
        UUID matchId = UUID.randomUUID();
        player.setState(PlayerState.PLAYING);
        player.setCurrentMatchId(matchId);
        System.out.println("✓ Jugador unido a partida: " + matchId);
        System.out.println("  En partida: " + player.isInMatch());

        // 6. Enviar eventos de la partida
        player.sendMessage("Tu turno - Pregunta #1: ¿Cuál es la capital de Francia?");
        player.sendMessage("¡Respuesta correcta! +10 puntos");
        player.sendMessage("Tu turno - Pregunta #2: ¿Cuántos planetas hay en el sistema solar?");
        player.sendMessage("¡Respuesta correcta! +10 puntos");
        System.out.println("✓ Eventos de partida enviados");

        // 7. Partida termina
        player.setState(PlayerState.LOBBY);
        player.setCurrentMatchId(null);
        player.sendMessage("¡Partida terminada! Puntuación final: 100");
        System.out.println("✓ Partida finalizada, jugador vuelve al lobby");

        // 8. Verificar mensajes enviados
        System.out.println("\n=== Mensajes enviados al cliente ===");
        for (int i = 0; i < mockSender.getSentMessages().size(); i++) {
            System.out.println((i + 1) + ". " + mockSender.getSentMessages().get(i));
        }

        // 9. Desconectar
        player.disconnect();
        System.out.println("\n✓ Jugador desconectado");
        System.out.println("  Estado: " + player.getState());
        System.out.println("  Conectado: " + player.isConnected());

        // 10. Intentar enviar mensaje después de desconectar (no se enviará)
        int messagesBefore = mockSender.getMessageCount();
        player.sendMessage("Este mensaje no se enviará");
        int messagesAfter = mockSender.getMessageCount();
        System.out.println("  Mensajes después de desconectar: " + messagesAfter +
                         " (igual que antes: " + messagesBefore + ")");

        System.out.println("\n=== Demo Completada ===");
        System.out.println("La clase Player actúa como el 'ancla' de la arquitectura:");
        System.out.println("1. Mantiene la identidad del usuario (ID, nombre)");
        System.out.println("2. Rastrea su estado lógico (LOBBY, PLAYING, etc.)");
        System.out.println("3. Abstrae la comunicación (MessageSender interface)");
        System.out.println("4. Vive durante toda la sesión del usuario");
    }
}
