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
let currentToken = null;
let isCreateGamePending = false;
let createGamePendingTimeout = null;
let hasClearedMockGames = false;
let hasRegisteredSocketMessageHandler = false;
let currentJoinedRoomId = null;
let currentOwnedRoomId = null;
let currentJoinedRoomPlayers = 0;
let currentStartedRoomId = null;
let phaserGame = null;
const createdGameRoomIds = new Set();
const pendingJoinRoomIds = new Set();
const pendingLeaveRoomIds = new Set();

// Color palette used by lobby game cards.
const CARD_COLORS = ['--explode', '--third', '--green', '--orange', '--accent'];

function buildRandomCardColors(count) {
    return Array.from({ length: count }, () => {
        const pick = CARD_COLORS[Math.floor(Math.random() * CARD_COLORS.length)];
        return `var(${pick})`;
    });
}

function applyCardColors(card, cardColors = []) {
    if (!card) return;

    const badges = card.querySelectorAll('.game-card-field-value, .game-card-players-list-trigger');
    if (!badges.length) return;

    const resolvedColors = Array.isArray(cardColors) && cardColors.length
        ? cardColors
        : buildRandomCardColors(badges.length);

    badges.forEach((el, index) => {
        const fallback = buildRandomCardColors(1)[0];
        el.style.backgroundColor = resolvedColors[index] || fallback;
    });

    card.dataset.cardColors = JSON.stringify(resolvedColors);
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

function renderLobbyUsername(username) {
    const lobbyUsername = document.getElementById('lobby-username');
    if (!lobbyUsername) return;

    const safeUsername = String(username || '').trim() || 'User';

    lobbyUsername.textContent = '';

    const usernameHighlight = document.createElement('span');
    usernameHighlight.className = 'lobby-username-highlight';
    usernameHighlight.textContent = safeUsername;

    const lobbySuffix = document.createElement('span');
    lobbySuffix.className = 'lobby-username-suffix';
    lobbySuffix.textContent = 'on Apalabrazos lobby room';

    lobbyUsername.appendChild(usernameHighlight);
    lobbyUsername.appendChild(lobbySuffix);
}

function destroyPhaserGame() {
    if (phaserGame) {
        phaserGame.destroy(true);
        phaserGame = null;
    }
    const container = document.getElementById('phaser-game-container');
    if (container) container.innerHTML = '';
}

function showMatchStartView(payload = {}) {
    const roomId = String(payload?.roomId || currentJoinedRoomId || '').trim();

    UIManager.switchView('view-match-start');

    destroyPhaserGame();
    import('/js/phaser_src/main.js').then((mod) => {
        phaserGame = mod.startGame('phaser-game-container');
    });

    if (roomId && currentStartedRoomId !== roomId) {
        currentStartedRoomId = roomId;
        SocketClient.send('GameControllerReady', { roomId });
    }
}

function syncStartMatchButtonState() {
    const startBtn = document.getElementById('btn-start-match');
    if (!startBtn) return;

    const ownsCurrentRoom = Boolean(currentOwnedRoomId) && currentOwnedRoomId === currentJoinedRoomId;
    const enoughPlayers = currentJoinedRoomPlayers >= 2;
    const canStart = ownsCurrentRoom && enoughPlayers;

    startBtn.disabled = !canStart;

    if (!ownsCurrentRoom) {
        startBtn.textContent = 'Start Match';
    } else if (!enoughPlayers) {
        startBtn.textContent = 'Waiting Players';
    } else {
        startBtn.textContent = 'Start Match';
    }
}

function bindStartMatchButton() {
    const startBtn = document.getElementById('btn-start-match');
    if (!startBtn) return;
    if (startBtn.dataset.boundStartMatch === '1') return;

    startBtn.addEventListener('click', (event) => {
        event.preventDefault();

        if (!currentOwnedRoomId || currentOwnedRoomId !== currentJoinedRoomId) {
            showCreateGameErrors(['Solo el creador de la partida puede iniciarla.']);
            return;
        }

        if (currentJoinedRoomPlayers < 2) {
            showCreateGameErrors(['Necesitas al menos 2 jugadores para iniciar la partida.']);
            return;
        }

        SocketClient.send('StartMatchRequest', {
            roomId: currentOwnedRoomId,
            requestedAt: Math.floor(Date.now() / 1000),
        });
    });

    startBtn.dataset.boundStartMatch = '1';
}

function updateCurrentRoomState(roomId, players = null) {
    const normalizedRoomId = String(roomId || '').trim();

    if (!normalizedRoomId || currentJoinedRoomId !== normalizedRoomId) {
        syncStartMatchButtonState();
        return;
    }

    if (players != null && Number.isFinite(Number(players))) {
        currentJoinedRoomPlayers = Number(players);
    }

    syncStartMatchButtonState();
}

function handleLogout() {
    console.log('🚪 Logging out...');

    // Disconnect WebSocket
    SocketClient.disconnect();

    // Clear session variables
    currentUsername = null;
    currentToken = null;
    hasRegisteredSocketMessageHandler = false;
    currentJoinedRoomId = null;
    currentOwnedRoomId = null;
    currentJoinedRoomPlayers = 0;
    currentStartedRoomId = null;
    isCreateGamePending = false;
    createdGameRoomIds.clear();
    pendingJoinRoomIds.clear();
    pendingLeaveRoomIds.clear();

    // Destroy Phaser game if running
    destroyPhaserGame();

    // Clear any pending timeouts
    if (createGamePendingTimeout) {
        clearTimeout(createGamePendingTimeout);
        createGamePendingTimeout = null;
    }

    // Clear lobby UI
    const chatMessages = document.getElementById('chat-messages');
    if (chatMessages) {
        chatMessages.innerHTML = '';
    }

    const gamesList = document.getElementById('games-list');
    if (gamesList) {
        gamesList.innerHTML = '';
    }

    // Return to login screen
    UIManager.switchView('view-login');
    if (typeof LoginUI.focusUsernameInput === 'function') {
        LoginUI.focusUsernameInput();
    }

    console.log('✅ Logged out successfully');
}

function setCreatePendingState(pending) {
    isCreateGamePending = pending;
    syncCreateGameControlsState();
}

function syncCreateGameControlsState() {
    const btnConfirm = document.getElementById('btn-confirm-create');
    const btnCreate = document.getElementById('btn-create-game');
    const createCard = document.getElementById('create-game-config-card');

    const joinedAnyMatch = Boolean(currentJoinedRoomId);

    if (btnConfirm) {
        btnConfirm.disabled = isCreateGamePending || joinedAnyMatch;
        btnConfirm.textContent = isCreateGamePending ? 'Creating...' : 'Create Game';
    }

    if (btnCreate) {
        btnCreate.disabled = joinedAnyMatch;
    }

    if (joinedAnyMatch && createCard) {
        createCard.classList.add('hidden');
    }

    syncStartMatchButtonState();
}

function toTitleCase(value) {
    if (!value) return '';
    return String(value)
        .toLowerCase()
        .split(/[_\s-]+/)
        .map(chunk => chunk.charAt(0).toUpperCase() + chunk.slice(1))
        .join(' ');
}

function normalizeGameTypeLabel(type) {
    if (!type) return 'Classic';
    const raw = String(type).toUpperCase();
    if (raw === 'HIGHER_POINTS_WINS') return 'Classic';
    if (raw === 'NUMBER_WINS') return 'Dominio';
    return toTitleCase(type);
}

function normalizeTimeLabel(timeValue) {
    const n = Number(timeValue);
    if (!Number.isFinite(n) || n <= 0) return '5 min';
    if (n < 1) return `${Math.round(n * 60)} s`;
    return `${n} min`;
}

function attachPlayerListClickHandler(card) {
    const trigger = card.querySelector('.game-card-players-list-trigger');
    if (!trigger) return;

    trigger.addEventListener('click', (e) => {
        e.stopPropagation();
        const playerNames = JSON.parse(card.dataset.playerNames || '[]');
        const roomId = card.dataset.roomId;
        showPlayersModal(playerNames, roomId);
    });
}

function showPlayersModal(playerNames, roomId) {
    let modal = document.getElementById('players-modal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'players-modal';
        modal.className = 'players-modal';
        modal.innerHTML = `
            <div class="players-modal-overlay"></div>
            <div class="players-modal-content">
                <div class="players-modal-header">
                    <h3>Players in Game</h3>
                </div>
                <div class="players-modal-list"></div>
            </div>
        `;
        document.body.appendChild(modal);

        const overlay = modal.querySelector('.players-modal-overlay');
        overlay.addEventListener('click', closePlayersModal);

        modal.addEventListener('click', (e) => {
            if (e.target === modal) closePlayersModal();
        });
    }

    const listContainer = modal.querySelector('.players-modal-list');
    listContainer.innerHTML = '';

    if (!playerNames || playerNames.length === 0) {
        listContainer.innerHTML = '<p class="players-modal-empty">No players connected</p>';
    } else {
        playerNames.forEach(name => {
            const item = document.createElement('div');
            item.className = 'players-modal-item';
            item.textContent = name;
            listContainer.appendChild(item);
        });
    }

    modal.style.display = 'flex';
}

function closePlayersModal() {
    const modal = document.getElementById('players-modal');
    if (modal) {
        modal.style.display = 'none';
    }
}

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        closePlayersModal();
    }
});

function bindLogoutButton() {
    const logoutBtn = document.getElementById('btn-logout');
    if (!logoutBtn) return;
    if (logoutBtn.dataset.boundLogout === '1') return;

    logoutBtn.addEventListener('click', (event) => {
        event.preventDefault();
        handleLogout();
    });
    logoutBtn.dataset.boundLogout = '1';
}

function applyJoinButtonState(button) {
    if (!button) return;

    const roomId = String(button.dataset.roomId || '').trim();
    const pending = pendingJoinRoomIds.has(roomId);
    const leaving = pendingLeaveRoomIds.has(roomId);
    const joined = Boolean(currentJoinedRoomId) && currentJoinedRoomId === roomId;
    const blocked = Boolean(currentJoinedRoomId) && currentJoinedRoomId !== roomId;

    button.disabled = pending || blocked || leaving;
    button.classList.toggle('is-joined', joined);
    button.classList.toggle('is-pending', pending || leaving);
    button.classList.toggle('is-blocked', blocked);
    button.textContent = leaving ? 'LEAVING...' : pending ? 'JOINING...' : joined ? 'LEAVE' : blocked ? 'IN GAME' : 'Join';
}

function syncJoinButtonState(roomId) {
    if (!roomId) return;
    const joinButton = document.querySelector(`.btn-join-game[data-room-id="${roomId}"]`);
    applyJoinButtonState(joinButton);
}

function syncAllJoinButtonsState() {
    document.querySelectorAll('.btn-join-game').forEach(button => applyJoinButtonState(button));
    syncCreateGameControlsState();
}

function removeOnlineGameCard(roomId) {
    const normalizedRoomId = String(roomId || '').trim();
    if (!normalizedRoomId) return;

    const card = document.querySelector(`.game-card-neo[data-room-id="${normalizedRoomId}"]`);
    if (card) {
        card.remove();
    }

    createdGameRoomIds.delete(normalizedRoomId);
    pendingJoinRoomIds.delete(normalizedRoomId);
    pendingLeaveRoomIds.delete(normalizedRoomId);

    if (currentJoinedRoomId === normalizedRoomId) {
        currentJoinedRoomId = null;
        currentJoinedRoomPlayers = 0;
        currentStartedRoomId = null;
    }

    if (currentOwnedRoomId === normalizedRoomId) {
        currentOwnedRoomId = null;
    }

    syncAllJoinButtonsState();
}

function updateOnlineGameCard(gameData, cardColors = null) {
    const roomId = String(gameData?.roomId || '').trim();
    if (!roomId) return;

    const currentPlayers = Number.isFinite(Number(gameData?.players)) ? Number(gameData.players) : 1;
    if (currentPlayers <= 0) {
        removeOnlineGameCard(roomId);
        return;
    }

    const card = document.querySelector(`.game-card-neo[data-room-id="${roomId}"]`);
    if (!card) return;

    const maxPlayers = Number.isFinite(Number(gameData?.maxPlayers)) ? Number(gameData.maxPlayers) : 2;
    const name = String(gameData?.name || `Game ${roomId.slice(0, 6)}`).trim();
    const typeLabel = normalizeGameTypeLabel(gameData?.gameType);
    const timeLabel = normalizeTimeLabel(gameData?.time);
    const difficultyLabel = toTitleCase(gameData?.difficulty || 'Medium');

    const values = card.querySelectorAll('.game-card-field-value');
    const nameNode = card.querySelector('.game-card-name-text');
    if (nameNode) {
        nameNode.textContent = name;
    }
    if (values.length >= 4) {
        values[0].textContent = `${currentPlayers}/${maxPlayers}`;
        values[1].textContent = typeLabel;
        values[2].textContent = timeLabel;
        values[3].textContent = difficultyLabel;
    }

    // Update playerNames in data attribute
    const playerNames = Array.isArray(gameData?.playerNames) ? gameData.playerNames : [];
    card.dataset.playerNames = JSON.stringify(playerNames);

    const colorsFromCard = card.dataset.cardColors ? JSON.parse(card.dataset.cardColors) : null;
    const colorsFromPayload = Array.isArray(gameData?.cardColors) ? gameData.cardColors : null;

    updateCurrentRoomState(roomId, currentPlayers);
    syncJoinButtonState(roomId);
    attachPlayerListClickHandler(card);
    applyCardColors(card, cardColors || colorsFromPayload || colorsFromCard || []);
}

function addOnlineGameCard(gameData, cardColors = null) {
    const gamesList = document.getElementById('games-list');
    if (!gamesList) return;

    const roomId = String(gameData?.roomId || '').trim();
    if (!roomId) {
        return;
    }

    const existingCard = gamesList.querySelector(`[data-room-id="${roomId}"]`);
    if (existingCard) {
        updateOnlineGameCard(gameData, cardColors);
        return;
    }

    if (createdGameRoomIds.has(roomId)) {
        return;
    }

    if (!hasClearedMockGames) {
        gamesList.innerHTML = '';
        hasClearedMockGames = true;
    }

    const currentPlayers = Number.isFinite(Number(gameData?.players)) ? Number(gameData.players) : 1;
    if (currentPlayers <= 0) {
        removeOnlineGameCard(roomId);
        return;
    }

    const maxPlayers = Number.isFinite(Number(gameData?.maxPlayers)) ? Number(gameData.maxPlayers) : 2;
    const name = String(gameData?.name || `Game ${roomId.slice(0, 6)}`).trim();
    const typeLabel = normalizeGameTypeLabel(gameData?.gameType);
    const timeLabel = normalizeTimeLabel(gameData?.time);
    const difficultyLabel = toTitleCase(gameData?.difficulty || 'Medium');

    const card = document.createElement('div');
    card.className = 'game-card-neo';
    card.dataset.roomId = roomId;

    card.innerHTML = `
        <div class="game-card-fields-grid">
            <div class="game-card-name-field">
                <span class="game-card-name-text"></span>
            </div>
            <div class="game-card-field">
                <span class="game-card-field-label">Players</span>
                <div class="game-card-players-container">
                    <span class="game-card-field-value"></span>
                    <span class="game-card-players-list-trigger" data-room-id="${roomId}" title="Click to view players">Players list ▼</span>
                </div>
            </div>
            <div class="game-card-field">
                <span class="game-card-field-label">Type</span>
                <span class="game-card-field-value"></span>
            </div>
            <div class="game-card-field">
                <span class="game-card-field-label">Time</span>
                <span class="game-card-field-value"></span>
            </div>
            <div class="game-card-field">
                <span class="game-card-field-label">Difficulty</span>
                <span class="game-card-field-value"></span>
            </div>
        </div>
        <button class="btn-join-game" data-room-id="${roomId}">Join</button>
    `;

    const values = card.querySelectorAll('.game-card-field-value');
    card.querySelector('.game-card-name-text').textContent = name;
    values[0].textContent = `${currentPlayers}/${maxPlayers}`;
    values[1].textContent = typeLabel;
    values[2].textContent = timeLabel;
    values[3].textContent = difficultyLabel;

    // Store playerNames in data attribute for modal access
    const playerNames = Array.isArray(gameData?.playerNames) ? gameData.playerNames : [];
    card.dataset.playerNames = JSON.stringify(playerNames);

    gamesList.prepend(card);
    createdGameRoomIds.add(roomId);
    const colorsFromPayload = Array.isArray(gameData?.cardColors) ? gameData.cardColors : null;

    updateCurrentRoomState(roomId, currentPlayers);
    syncJoinButtonState(roomId);
    attachPlayerListClickHandler(card);
    applyCardColors(card, cardColors || colorsFromPayload || []);
}

function renderLobbyMatchesSnapshot(matches) {
    const gamesList = document.getElementById('games-list');
    if (!gamesList) return;

    gamesList.innerHTML = '';
    createdGameRoomIds.clear();
    pendingJoinRoomIds.clear();
    pendingLeaveRoomIds.clear();
    currentJoinedRoomId = null;
    currentOwnedRoomId = null;
    currentJoinedRoomPlayers = 0;
    currentStartedRoomId = null;
    hasClearedMockGames = true;

    if (!Array.isArray(matches) || matches.length === 0) {
        return;
    }

    matches.forEach(match => addOnlineGameCard(match));
    syncAllJoinButtonsState();
}

function registerSocketMessageHandlers() {
    if (hasRegisteredSocketMessageHandler) {
        return;
    }

    hasRegisteredSocketMessageHandler = true;
    SocketClient.onMessage((msg) => {
        console.log('Message received:', msg);
        try {
            const data = typeof msg === 'string' ? JSON.parse(msg) : msg;

            if (data.type === 'LobbyMatchesSnapshot') {
                const matches = Array.isArray(data?.payload?.matches) ? data.payload.matches : [];
                console.log(`[LOBBY] Snapshot received with ${matches.length} matches`);
                renderLobbyMatchesSnapshot(matches);
            } else if (data.type === 'LobbyMatchCreated') {
                const payload = data?.payload || {};
                console.log('[LOBBY] Real-time match broadcast received:', payload);
                addOnlineGameCard(payload);
            } else if (data.type === 'LobbyMatchUpdated') {
                const payload = data?.payload || {};
                console.log('[LOBBY] Match updated:', payload);
                addOnlineGameCard(payload);
            } else if (data.type === 'LobbyMatchRemoved') {
                const roomId = String(data?.payload?.roomId || '').trim();
                console.log('[LOBBY] Match removed:', roomId);
                removeOnlineGameCard(roomId);
                if (currentJoinedRoomId === roomId) {
                    currentJoinedRoomId = null;
                    currentJoinedRoomPlayers = 0;
                    currentStartedRoomId = null;
                    UIManager.switchView('view-lobby');
                    showCreateGameErrors(['La partida a la que estabas unido ya no existe.']);
                    syncAllJoinButtonsState();
                }
            } else if (data.type === 'MatchClosedByCreator') {
                const roomId = String(data?.payload?.roomId || '').trim();
                const cause = data?.payload?.cause || 'El creador abandonó la partida.';

                pendingJoinRoomIds.delete(roomId);
                pendingLeaveRoomIds.delete(roomId);
                removeOnlineGameCard(roomId);

                if (!roomId || currentJoinedRoomId === roomId) {
                    currentJoinedRoomId = null;
                    currentJoinedRoomPlayers = 0;
                    currentStartedRoomId = null;
                }

                if (!roomId || currentOwnedRoomId === roomId) {
                    currentOwnedRoomId = null;
                }

                destroyPhaserGame();
                UIManager.switchView('view-lobby');
                showCreateGameErrors([cause]);
                syncAllJoinButtonsState();
            } else if (data.type === 'MatchStarted') {
                const payload = data?.payload || {};
                const roomId = String(payload?.roomId || '').trim();

                if (roomId) {
                    currentJoinedRoomId = roomId;
                    currentJoinedRoomPlayers = Number(payload?.players || currentJoinedRoomPlayers || 0);
                }

                showMatchStartView(payload);
            } else if (data.type === 'StartMatchRequestInvalid') {
                const cause = data?.payload?.cause || 'No se ha podido iniciar la partida.';
                showCreateGameErrors([cause]);
            } else if (data.type === 'chat_message') {
                const { text, username_originator } = data.payload || {};
                if (text && username_originator) {
                    const isOwn = username_originator === currentUsername;
                    LobbyUI.addMessage(username_originator, text, isOwn);
                }
            } else if (data.type === 'GameCreationRequestValid') {
                if (createGamePendingTimeout) {
                    clearTimeout(createGamePendingTimeout);
                    createGamePendingTimeout = null;
                }
                setCreatePendingState(false);
                clearCreateGameErrors();
                resetCreateGameForm();

                if (_createCard) {
                    _createCard.classList.add('hidden');
                }

                const payload = data?.payload || {};
                const roomId = String(payload?.roomId || '').trim();
                if (roomId) {
                    currentJoinedRoomId = roomId;
                    currentOwnedRoomId = roomId;
                    currentJoinedRoomPlayers = Number(payload?.players || 1);
                    currentStartedRoomId = null;
                    pendingJoinRoomIds.clear();
                }
                console.log('[CREATE] GameCreationRequestValid received:', payload);
                addOnlineGameCard(payload, buildRandomCardColors(5));
                syncAllJoinButtonsState();
            } else if (data.type === 'JoinMatchRequestValid') {
                const payload = data?.payload || {};
                const roomId = String(payload?.roomId || '').trim();
                if (roomId) {
                    pendingJoinRoomIds.clear();
                    currentJoinedRoomId = roomId;
                    currentJoinedRoomPlayers = Number(payload?.players || currentJoinedRoomPlayers || 0);
                    currentStartedRoomId = null;
                }
                console.log('[JOIN] JoinMatchRequestValid received:', payload);
                addOnlineGameCard(payload);
                syncAllJoinButtonsState();
            } else if (data.type === 'JoinMatchRequestInvalid') {
                const roomId = String(data?.payload?.roomId || '').trim();
                if (roomId) {
                    pendingJoinRoomIds.delete(roomId);
                    syncAllJoinButtonsState();
                }
                const cause = data?.payload?.cause || 'No se ha podido unir a la partida.';
                console.warn('[JOIN] Join failed:', cause);
                showCreateGameErrors([cause]);
            } else if (data.type === 'LeaveMatchRequestValid') {
                const roomId = String(data?.payload?.roomId || '').trim();
                pendingLeaveRoomIds.clear();
                pendingJoinRoomIds.clear();
                if (!roomId || currentJoinedRoomId === roomId) {
                    currentJoinedRoomId = null;
                    currentJoinedRoomPlayers = 0;
                    currentStartedRoomId = null;
                }
                if (!roomId || currentOwnedRoomId === roomId) {
                    currentOwnedRoomId = null;
                }
                console.log('[LEAVE] LeaveMatchRequestValid received:', data?.payload || {});
                syncAllJoinButtonsState();
            } else if (data.type === 'LeaveMatchRequestInvalid') {
                pendingLeaveRoomIds.clear();
                const cause = data?.payload?.cause || 'No se ha podido salir de la partida.';
                console.warn('[LEAVE] Leave failed:', cause);
                showCreateGameErrors([cause]);
                syncAllJoinButtonsState();
            } else if (data.type === 'GameCreationRequestInvalid') {
                if (createGamePendingTimeout) {
                    clearTimeout(createGamePendingTimeout);
                    createGamePendingTimeout = null;
                }
                setCreatePendingState(false);

                const cause = data?.payload?.cause || 'Error desconocido al crear la partida.';
                const errors = [cause];
                console.warn('[CREATE] Validation failed:', errors);
                showCreateGameErrors(errors);
            }
        } catch (e) {
            // Ignore parsing errors for non-JSON messages
        }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    populateGameSelects();
    syncCreateGameControlsState();
    bindLogoutButton();
    bindStartMatchButton();

    // --- Create Game button validation ---
    const btnConfirm = document.getElementById('btn-confirm-create');
    if (btnConfirm) {
        btnConfirm.addEventListener('click', () => {
            console.log('[CREATE] btn-confirm-create clicked ✅');
            if (isCreateGamePending) {
                console.warn('[CREATE] A create request is already pending. Ignoring duplicate click.');
                return;
            }

            if (currentJoinedRoomId) {
                showCreateGameErrors(['Ya estás dentro de una partida. Debes salir antes de crear otra.']);
                syncCreateGameControlsState();
                return;
            }

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

            setCreatePendingState(true);
            if (createGamePendingTimeout) {
                clearTimeout(createGamePendingTimeout);
            }
            createGamePendingTimeout = setTimeout(() => {
                setCreatePendingState(false);
                console.warn('[CREATE] Timeout waiting for GameCreationRequestValid/GameCreationRequestInvalid');
            }, 10000);
        });
    } else {
        console.error('[CREATE] ❌ btn-confirm-create NOT FOUND in DOM');
    }

    document.addEventListener('click', (event) => {
        const joinButton = event.target.closest('.btn-join-game');
        if (!joinButton) {
            return;
        }

        const roomId = String(joinButton.dataset.roomId || '').trim();
        if (!roomId) {
            return;
        }

        if (currentJoinedRoomId === roomId) {
            if (pendingLeaveRoomIds.has(roomId)) {
                return;
            }

            pendingLeaveRoomIds.add(roomId);
            pendingJoinRoomIds.delete(roomId);
            syncJoinButtonState(roomId);

            console.log('[LEAVE] Sending LeaveMatchRequest for room:', roomId);
            SocketClient.send('LeaveMatchRequest', {
                roomId,
                requestedAt: Math.floor(Date.now() / 1000),
            });
            return;
        }

        if (currentJoinedRoomId && currentJoinedRoomId !== roomId) {
            showCreateGameErrors(['Ya estás unido a una partida. Debes salir de ella antes de unirte a otra.']);
            syncAllJoinButtonsState();
            return;
        }

        if (pendingJoinRoomIds.has(roomId)) {
            return;
        }

        pendingJoinRoomIds.add(roomId);
        syncJoinButtonState(roomId);

        console.log('[JOIN] Sending JoinMatchRequest for room:', roomId);
        SocketClient.send('JoinMatchRequest', {
            roomId,
            requestedAt: Math.floor(Date.now() / 1000),
        });
    });
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

function resetCreateGameForm() {
    const nameInput = document.getElementById('cfg-game-name');
    if (nameInput) {
        nameInput.value = '';
    }

    const players = document.getElementById('cfg-players');
    const gameType = document.getElementById('cfg-game-type');
    const time = document.getElementById('cfg-time');
    const difficulty = document.getElementById('cfg-difficulty');

    if (players && GAME_OPTIONS.players.length > 0) {
        const defaultOption = GAME_OPTIONS.players.find(option => option.default) || GAME_OPTIONS.players[0];
        players.value = defaultOption.value;
    }

    if (gameType && GAME_OPTIONS.gameTypes.length > 0) {
        const defaultOption = GAME_OPTIONS.gameTypes.find(option => option.default) || GAME_OPTIONS.gameTypes[0];
        gameType.value = defaultOption.value;
    }

    if (time && GAME_OPTIONS.times.length > 0) {
        const defaultOption = GAME_OPTIONS.times.find(option => option.default) || GAME_OPTIONS.times[0];
        time.value = defaultOption.value;
    }

    if (difficulty && GAME_OPTIONS.difficulties.length > 0) {
        const defaultOption = GAME_OPTIONS.difficulties.find(option => option.default) || GAME_OPTIONS.difficulties[0];
        difficulty.value = defaultOption.value;
    }
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
    _btnCreate.addEventListener('click', () => {
        if (currentJoinedRoomId) {
            showCreateGameErrors(['No puedes crear otra partida mientras estés unido a una.']);
            syncCreateGameControlsState();
            return;
        }
        _createCard.classList.remove('hidden');
    });
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
            currentToken = token;  // Store token for logout

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
            currentToken = token;  // store token for logout

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

            // Register socket message handlers AFTER connecting (before handlers were cleared by connect())
            hasRegisteredSocketMessageHandler = false;  // Reset for new connection
            registerSocketMessageHandlers();

            // 5. WebSocket authenticated, switch to lobby
            console.log("✅ Authentication successful, entering lobby...");
            UIManager.switchView('view-lobby');
            renderLobbyUsername(currentUsername);

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



            // 6. Server messages are handled by the global socket listener registered before connect.

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