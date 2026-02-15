// js/main.js
import { LoginUI } from './ui/login.js';
import { SocketClient } from './network/socket-client.js';
import { UIManager } from './ui/ui-manager.js';
import { API_ENDPOINTS, WS_ENDPOINTS, buildApiUrl, buildWsUrl } from './config.js';

function decodeJwtPayload(token) {
    try {
        const payload = token.split('.')[1];
        if (!payload) {
            return null;
        }
        let base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
        const padLength = base64.length % 4;
        if (padLength) {
            base64 += '='.repeat(4 - padLength);
        }
        const json = atob(base64);
        return JSON.parse(json);
    } catch (e) {
        return null;
    }
}

// 1. Initialize Login interface
LoginUI.init(
    // onLoginAttempt callback
    async (credentials) => {
        try {
            // 2. Send login via POST to HTTP backend
            const loginUrl = buildApiUrl(API_ENDPOINTS.login);
            const loginResponse = await fetch(loginUrl, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    email: credentials.email,
                    pass: credentials.pass
                })
            });

            if (!loginResponse.ok) {
                let errorMessage = "Credenciales incorrectas.";
                try {
                    const errorBody = await loginResponse.json();
                    if (errorBody && errorBody.message) {
                        errorMessage = errorBody.message;
                    }
                } catch (e) {
                    // Respuesta no JSON, usar mensaje por defecto
                }
                LoginUI.showError(errorMessage);
                return;
            }

            // 3. Extract token from response
            const loginData = await loginResponse.json();
            const token = loginData.token;

            if (!token) {
                LoginUI.showError("Error: No authentication token received");
                return;
            }

            const jwtPayload = decodeJwtPayload(token);
            const username = jwtPayload && jwtPayload.username;
            if (!username) {
                LoginUI.showError("Error: Token missing username");
                return;
            }

            // 4. Connect to WebSocket with token
            const serverUrl = `${buildWsUrl(WS_ENDPOINTS.game)}/${encodeURIComponent(username)}`;
            console.log("Connecting to WebSocket:", serverUrl);

            try {
                await SocketClient.connect(serverUrl, token);
            } catch (wsError) {
                console.error("WebSocket connection error:", wsError);
                LoginUI.showError("Error al conectar con el servidor WebSocket. Verifica que el servidor esté en línea.");
                return;
            }

            // 5. WebSocket authenticated, switch to lobby
            console.log("✅ Authentication successful, entering lobby...");
            UIManager.switchView('view-lobby');

            // 6. Listen for server messages
            SocketClient.onMessage((msg) => {
                console.log("Message received:", msg);
                // Here you can handle other game events
            });

        } catch (error) {
            console.error("Login error:", error);
            LoginUI.showError("No se pudo conectar con el servidor.");
        }
    },
    // onRegisterAttempt callback
    async (registerData) => {
        try {
            const registerUrl = buildApiUrl(API_ENDPOINTS.register);

            const response = await fetch(registerUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    username: registerData.username,
                    email: registerData.email,
                    password: registerData.password
                })
            });

            if (response.ok) {
                const data = await response.json();
                alert("Registro exitoso! Por favor, inicia sesión.");
                UIManager.switchView('view-login');
            } else {
                try {
                    const errorData = await response.json();
                    throw new Error(errorData.message || "Error en el registro");
                } catch (e) {
                    // Fallback if response is not JSON or other error
                    throw new Error(e.message || "Error desconocido en el registro");
                }
            }
        } catch (error) {
            console.error("Registration error:", error);
            LoginUI.showError(error.message || "No se pudo conectar con el servidor.");
        }
    }
);