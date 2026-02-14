// js/network/socket-client.js

export const SocketClient = {
    socket: null,
    listeners: new Set(), // Allow multiple modules to listen to messages

    connect(url, token = null) {
        return new Promise((resolve, reject) => {
            try {
                // If there's a token, add it as query parameter
                const wsUrl = token ? `${url}?token=${encodeURIComponent(token)}` : url;
                this.socket = new WebSocket(wsUrl);

                this.socket.onopen = () => {
                    console.log("✅ Connected to Apalabrazos server");
                    resolve();
                };

                this.socket.onerror = (err) => {
                    console.error("❌ Connection error");
                    reject(err);
                };

                this.socket.onmessage = (event) => {
                    const message = JSON.parse(event.data);
                    this._dispatch(message);
                };

                this.socket.onclose = () => {
                    console.warn("⚠️ Connection closed");
                    // Here you could implement reconnection logic
                };

            } catch (e) {
                reject(e);
            }
        });
    },

    // Sends an object to the server transforming it to JSON
    send(type, payload) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            const message = JSON.stringify({ type, data: payload });
            this.socket.send(message);
        } else {
            console.error("Cannot send: Socket not connected");
        }
    },

    // Allow other modules (Lobby, Match) to subscribe to messages
    onMessage(callback) {
        this.listeners.add(callback);
    },

    _dispatch(message) {
        this.listeners.forEach(callback => callback(message));
    }
};