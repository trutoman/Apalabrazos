// js/network/socket-client.js

export const SocketClient = {
    socket: null,
    listeners: new Set(), // Para que varios módulos escuchen mensajes

    connect(url) {
        return new Promise((resolve, reject) => {
            try {
                this.socket = new WebSocket(url);

                this.socket.onopen = () => {
                    console.log("✅ Conectado al servidor de Apalabrazos");
                    resolve();
                };

                this.socket.onerror = (err) => {
                    console.error("❌ Error en la conexión");
                    reject(err);
                };

                this.socket.onmessage = (event) => {
                    const message = JSON.parse(event.data);
                    this._dispatch(message);
                };

                this.socket.onclose = () => {
                    console.warn("⚠️ Conexión cerrada");
                    // Aquí podrías implementar lógica de reconexión
                };

            } catch (e) {
                reject(e);
            }
        });
    },

    // Envía un objeto al servidor transformándolo a JSON
    send(type, payload) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            const message = JSON.stringify({ type, data: payload });
            this.socket.send(message);
        } else {
            console.error("No se puede enviar: Socket no conectado");
        }
    },

    // Permite que otros módulos (Lobby, Match) se suscriban a mensajes
    onMessage(callback) {
        this.listeners.add(callback);
    },

    _dispatch(message) {
        this.listeners.forEach(callback => callback(message));
    }
};