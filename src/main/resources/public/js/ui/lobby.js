export const LobbyUI = {
    init(onSendMessage) {
        const chatInput = document.getElementById('lobby-chat-input');
        const sendBtn = document.getElementById('lobby-chat-send');

        if (!chatInput || !sendBtn) {
            console.error("Lobby elements not found");
            return;
        }

        // Send on click
        sendBtn.addEventListener('click', () => {
            const message = chatInput.value.trim();
            if (message) {
                onSendMessage(message);
                chatInput.value = ''; // Clear input
            }
        });

        // Send on Enter key
        chatInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                const message = chatInput.value.trim();
                if (message) {
                    onSendMessage(message);
                    chatInput.value = ''; // Clear input
                }
            }
        });
    },

    addMessage(sender, text, isSelf = false) {
        const chatMessages = document.getElementById('chat-messages');
        if (!chatMessages) return;

        const msgDiv = document.createElement('div');
        msgDiv.className = `chat-message ${isSelf ? 'self' : 'other'}`;

        const senderSpan = document.createElement('span');
        senderSpan.className = 'chat-sender';
        senderSpan.textContent = sender;

        const textSpan = document.createElement('span');
        textSpan.className = 'chat-text';
        textSpan.textContent = text;

        msgDiv.appendChild(senderSpan);
        msgDiv.appendChild(textSpan);
        chatMessages.appendChild(msgDiv);

        // Scroll to bottom
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }
};
