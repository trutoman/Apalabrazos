// js/network/socket-client.js

export const SocketClient = {
    socket: null,
    listeners: new Set(), // Allow multiple modules to listen to messages

    connect(url, token = null) {
        return new Promise((resolve, reject) => {
            try {
                // If there's a token, add it as query parameter
                const wsUrl = token ? `${url}?token=${encodeURIComponent(token)}` : url;
                console.log("🔗 WebSocket URL:", wsUrl);
                this.socket = new WebSocket(wsUrl);

                // Add timeout to detect connection failures
                const connectionTimeout = setTimeout(() => {
                    if (this.socket.readyState !== WebSocket.OPEN) {
                        console.error("❌ WebSocket connection timeout");
                        this.socket.close();
                        reject(new Error("Connection timeout - server may be unreachable"));
                    }
                }, 5000);

                this.socket.onopen = () => {
                    clearTimeout(connectionTimeout);
                    console.log("✅ Connected to Apalabrazos server");
                    resolve();
                };

                this.socket.onerror = (err) => {
                    clearTimeout(connectionTimeout);
                    console.error("❌ WebSocket error:", err);
                    reject(err || new Error("WebSocket error"));
                };

                this.socket.onmessage = (event) => {
                    try {
                        console.log("📨 Raw message from server:", event.data);
                        const message = JSON.parse(event.data);
                        console.log("📦 Parsed message:", message);
                        this._dispatch(message);
                    } catch (e) {
                        console.error("Error parsing message:", e);
                    }
                };

                this.socket.onclose = (event) => {
                    console.warn("⚠️ Connection closed", {
                        code: event.code,
                        reason: event.reason,
                        wasClean: event.wasClean
                    });
                    // Here you could implement reconnection logic
                };

            } catch (e) {
                console.error("❌ Error creating WebSocket:", e);
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