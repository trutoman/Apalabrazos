// js/network/socket-client.js

export const SocketClient = {
    socket: null,
    listeners: new Set(), // Allow multiple modules to listen to messages

    connect(url, token = null) {
        return new Promise((resolve, reject) => {
            try {
                // Clean up any stale listeners from previous connections
                this.listeners.clear();

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

                    // Start heartbeat (ping) every 20 seconds to prevent idle timeout
                    this.pingInterval = setInterval(() => {
                        if (this.socket.readyState === WebSocket.OPEN) {
                            this.send("PING", {});
                        }
                    }, 20000); // 20 seconds

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
                    if (this.pingInterval) {
                        clearInterval(this.pingInterval);
                    }
                    console.warn("⚠️ Connection closed",
                        "Code:", event.code,
                        "Reason:", event.reason,
                        "WasClean:", event.wasClean
                    );
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

    // Sends a raw object to the server
    sendMessage(messageObj) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            this.socket.send(JSON.stringify(messageObj));
        } else {
            console.error("Cannot send: Socket not connected");
        }
    },

    // Allow other modules (Lobby, Match) to subscribe to messages
    onMessage(callback) {
        this.listeners.add(callback);
    },

    // Disconnect from the server
    disconnect() {
        if (this.pingInterval) {
            clearInterval(this.pingInterval);
        }
        if (this.socket) {
            this.socket.close();
            this.socket = null;
        }
        this.listeners.clear();
        console.log('✅ Disconnected from server');
    },

    _dispatch(message) {
        this.listeners.forEach(callback => callback(message));
    }
};