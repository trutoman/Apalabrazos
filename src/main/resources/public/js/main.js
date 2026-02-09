// js/main.js
import { LoginUI } from './ui/login.js';
import { SocketClient } from './network/socket-client.js';
import { UIManager } from './ui/ui-manager.js';

// 1. Inicializar la interfaz de Login
LoginUI.init(async (credentials) => {
    try {
        // 2. Intentar conectar al servidor Java (Nivel 1 del backend)
        const serverUrl = "ws://localhost:8080/game";
        await SocketClient.connect(serverUrl);

        // 3. Una vez conectados, enviamos el comando de login
        SocketClient.send("LOGIN_ATTEMPT", {
            user: credentials.user,
            pass: credentials.pass
        });

        // 4. Escuchamos la respuesta del servidor (esto es un ejemplo)
        SocketClient.onMessage((msg) => {
            if (msg.type === "LOGIN_SUCCESS") {
                console.log("Login correcto, entrando al lobby...");
                UIManager.switchView('view-lobby');
            } else if (msg.type === "LOGIN_ERROR") {
                LoginUI.showError(msg.data.reason);
            }
        });

    } catch (error) {
        LoginUI.showError("No se pudo conectar con el servidor.");
    }
});