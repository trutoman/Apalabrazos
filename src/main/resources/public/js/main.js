// js/main.js
import { LoginUI } from './ui/login.js';
import { LobbyUI } from './ui/lobby.js';
import { SocketClient } from './network/socket-client.js';
import { UIManager } from './ui/ui-manager.js';
import { API_ENDPOINTS, WS_ENDPOINTS, buildApiUrl, buildWsUrl } from './config.js';
import { GAME_OPTIONS } from './config/game-options.js';
import { validate_game_creation } from './validation/game-validation.js';

// Module-level session info — populated after a successful login
let currentUsername = null;

// Assign a random accent colour to every card field-value badge
const CARD_COLORS = ['--explode', '--third', '--green', '--orange'];
function randomCardColors() {
    document.querySelectorAll('.game-card-field-value').forEach(el => {
        const pick = CARD_COLORS[Math.floor(Math.random() * CARD_COLORS.length)];
        el.style.backgroundColor = `var(${pick})`;
    });
}

// Populate create-game selects from the central config
function populateSelect(id, options) {
    const sel = document.getElementById(id);
    if (!sel) return;
    sel.innerHTML = '';
    options.forEach(({ value, label, default: isDefault }) => {
        const opt = document.createElement('option');
        opt.value = value;
        opt.textContent = label;
        if (isDefault) opt.selected = true;
        sel.appendChild(opt);
    });
}

function populateGameSelects() {
    populateSelect('cfg-players', GAME_OPTIONS.players);
    populateSelect('cfg-game-type', GAME_OPTIONS.gameTypes);
    populateSelect('cfg-time', GAME_OPTIONS.times);
    populateSelect('cfg-difficulty', GAME_OPTIONS.difficulties);
}

document.addEventListener('DOMContentLoaded', () => {
    randomCardColors();
    populateGameSelects();

    // --- Create Game button validation ---
    const btnConfirm = document.getElementById('btn-confirm-create');
    if (btnConfirm) {
        btnConfirm.addEventListener('click', () => {
            console.log('[CREATE] btn-confirm-create clicked ✅');
            const result = validate_game_creation();
            if (!result.valid) {
                console.warn('[CREATE] Validation failed:', result.errors);
                showCreateGameErrors(result.errors);
                return;
            }
            clearCreateGameErrors();

            // Build and send GameCreationRequest
            const nameInput = document.getElementById('cfg-game-name');
            const payload = {
                name: nameInput?.value?.trim() ?? '',
                players: parseInt(document.getElementById('cfg-players')?.value, 10),
                gameType: document.getElementById('cfg-game-type')?.value,
                time: parseFloat(document.getElementById('cfg-time')?.value),
                difficulty: document.getElementById('cfg-difficulty')?.value,
                requestedAt: Math.floor(Date.now() / 1000),
            };

            console.log('[CREATE] Sending GameCreationRequest:', payload);
            SocketClient.send('GameCreationRequest', payload);
        });
    } else {
        console.error('[CREATE] ❌ btn-confirm-create NOT FOUND in DOM');
    }
});

/** Renders validation errors as a fixed toast above everything. */
function showCreateGameErrors(errors) {
    let box = document.getElementById('create-game-errors');
    if (!box) {
        box = document.createElement('div');
        box.id = 'create-game-errors';
        box.className = 'create-game-errors';
        // Append to body so it's never clipped by parent overflow
        document.body.appendChild(box);
    }
    box.innerHTML = errors.map(e => `<span>⚠️ ${e}</span>`).join('');
    box.style.display = 'flex';
    // Auto-hide after 4 seconds
    clearTimeout(box._hideTimer);
    box._hideTimer = setTimeout(() => { box.style.display = 'none'; }, 4000);
}

function clearCreateGameErrors() {
    const box = document.getElementById('create-game-errors');
    if (box) box.style.display = 'none';
}

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
//UIManager.switchView('view-lobby');

// Create Game config card toggle — wired at page load so it always works
const _btnCreate = document.getElementById('btn-create-game');
const _createCard = document.getElementById('create-game-config-card');
const _btnCloseCreate = document.getElementById('btn-close-create-game');
if (_btnCreate && _createCard) {
    _btnCreate.addEventListener('click', () => { _createCard.classList.remove('hidden'); });
}
if (_btnCloseCreate && _createCard) {
    _btnCloseCreate.addEventListener('click', () => { _createCard.classList.add('hidden'); });
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
            const userId = jwtPayload && jwtPayload.userId;

            if (!username || !userId) {
                LoginUI.showError("Error: Token missing required user information");
                return;
            }
            currentUsername = username; // store for later use (e.g. game creation)

            // 4. Connect to WebSocket with token using userId in the path
            const serverUrl = `${buildWsUrl(WS_ENDPOINTS.game)}/${encodeURIComponent(userId)}`;
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

            // Initialize Lobby UI with message sending logic
            LobbyUI.init((message) => {
                console.log("Sending message:", message);
                // SocketClient.sendMessage({ type: 'CHAT', content: message });
                // Using a simpler approach for now as sendMessage implementation might vary
                try {
                    // Send message via WebSocket - the UI will update when the server echoes it back
                    if (SocketClient.sendMessage) {
                        SocketClient.sendMessage({ type: 'chat', payload: message });
                    } else if (SocketClient.send) {
                        SocketClient.send(JSON.stringify({ type: 'chat', payload: message }));
                    } else {
                        console.warn("SocketClient.sendMessage not available");
                    }
                } catch (e) {
                    console.error("Error sending message:", e);
                }
            });



            // 6. Listen for server messages
            SocketClient.onMessage((msg) => {
                console.log("Message received:", msg);
                try {
                    const data = typeof msg === 'string' ? JSON.parse(msg) : msg;

                    if (data.type === 'chat_message') {
                        const { text, username_originator } = data.payload || {};
                        if (text && username_originator) {
                            // Align right if the message belongs to this user, left otherwise
                            const isOwn = username_originator === username;
                            LobbyUI.addMessage(username_originator, text, isOwn);
                        }
                    }
                } catch (e) {
                    // Ignore parsing errors for non-JSON messages
                }
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