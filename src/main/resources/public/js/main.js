// js/main.js
import { LoginUI } from './ui/login.js';
import { SocketClient } from './network/socket-client.js';
import { UIManager } from './ui/ui-manager.js';
import { API_ENDPOINTS, WS_ENDPOINTS, buildApiUrl, buildWsUrl } from './config.js';

// 1. Inicializar la interfaz de Login
LoginUI.init(async (credentials) => {
    try {
        // 2. Enviar login por POST al backend HTTP
        const loginUrl = buildApiUrl(API_ENDPOINTS.login);
        const loginResponse = await fetch(loginUrl, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                user: credentials.user,
                pass: credentials.pass
            })
        });

        if (!loginResponse.ok) {
            let errorMessage = "Credenciales incorrectas.";
            try {
                const errorBody = await loginResponse.json();
                if (errorBody && errorBody.reason) {
                    errorMessage = errorBody.reason;
                }
            } catch (e) {
                // Respuesta no JSON, usar mensaje por defecto
            }
            LoginUI.showError(errorMessage);
            return;
        }

        // 3. Intentar conectar al servidor Java (Nivel 1 del backend)
        const serverUrl = buildWsUrl(WS_ENDPOINTS.game);
        await SocketClient.connect(serverUrl);

        // 4. Una vez conectados, enviamos el comando de login
        SocketClient.send("LOGIN_ATTEMPT", {
            user: credentials.user,
            pass: credentials.pass
        });

        // 5. Escuchamos la respuesta del servidor (esto es un ejemplo)
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