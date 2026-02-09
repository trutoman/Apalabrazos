package UE_Proyecto_Ingenieria.Apalabrazos.backend.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mock implementation of MessageSender for testing purposes.
 * Stores all sent messages in a list for verification in unit tests.
 *
 * Usage in tests:
 * <pre>
 * MockMessageSender sender = new MockMessageSender();
 * Player player = new Player(UUID.randomUUID(), "TestPlayer", sender);
 * player.sendMessage("Hello");
 * assertEquals("Hello", sender.getSentMessages().get(0));
 * </pre>
 */
public class MockMessageSender implements MessageSender {

    private final List<Object> sentMessages = new ArrayList<>();
    private boolean connected = true;

    @Override
    public void send(Object message) {
        if (connected) {
            sentMessages.add(message);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        this.connected = false;
    }

    /**
     * Get all messages sent through this sender (for test verification)
     * @return Unmodifiable list of sent messages
     */
    public List<Object> getSentMessages() {
        return Collections.unmodifiableList(sentMessages);
    }

    /**
     * Get the last message sent
     * @return The last message, or null if none
     */
    public Object getLastMessage() {
        return sentMessages.isEmpty() ? null : sentMessages.get(sentMessages.size() - 1);
    }

    /**
     * Get the number of messages sent
     * @return Message count
     */
    public int getMessageCount() {
        return sentMessages.size();
    }

    /**
     * Clear all stored messages (useful between test cases)
     */
    public void clearMessages() {
        sentMessages.clear();
    }

    /**
     * Simulate reconnection
     */
    public void reconnect() {
        this.connected = true;
    }
}
