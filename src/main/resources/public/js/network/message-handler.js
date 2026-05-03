// js/network/message-handler.js
//
// Inbound WebSocket message router.
// Registers a listener on SocketClient and dispatches each server message
// to the appropriate UI/app action, keeping network concerns out of main.js.
//
// Usage (from main.js):
//   bindSocketMessageHandlers({ state, actions });
//
// `state`   — proxy object with getters/setters wired to main.js module-level variables
// `actions` — plain object with callback functions provided by main.js

import { SocketClient } from './socket-client.js';
import { PhaserEventBus } from '../phaser_src/phaserEventBus.js';

/**
 * Registers the central inbound message handler on SocketClient.
 *
 * @param {{ state: Object, actions: Object }} ctx
 *   state   — proxy exposing mutable app state from main.js (getters + setters)
 *   actions — callback functions for every UI/app reaction needed
 */
export function bindSocketMessageHandlers({ state, actions }) {
    SocketClient.onMessage((msg) => {
        console.log('Message received:', msg);
        try {
            const data = typeof msg === 'string' ? JSON.parse(msg) : msg;
            _route(data, state, actions);
        } catch (e) {
            // Ignore parsing errors for non-JSON messages
        }
    });
}

// ---------------------------------------------------------------------------
// Internal router — one branch per server message type
// ---------------------------------------------------------------------------

function _route(data, state, actions) {
    if (data.type === 'TimerTick') {
        const remaining = data?.payload?.remaining ?? 0;
        PhaserEventBus.emit('net:timerTick', { remaining });

    } else if (data.type === 'LobbyMatchesSnapshot') {
        const matches = Array.isArray(data?.payload?.matches) ? data.payload.matches : [];
        console.log(`[LOBBY] Snapshot received with ${matches.length} matches`);
        actions.renderLobbyMatchesSnapshot(matches);

    } else if (data.type === 'LobbyMatchCreated') {
        const payload = data?.payload || {};
        console.log('[LOBBY] Real-time match broadcast received:', payload);
        actions.addOnlineGameCard(payload);

    } else if (data.type === 'LobbyMatchUpdated') {
        const payload = data?.payload || {};
        console.log('[LOBBY] Match updated:', payload);
        actions.addOnlineGameCard(payload);

    } else if (data.type === 'LobbyMatchRemoved') {
        const roomId = String(data?.payload?.roomId || '').trim();
        console.log('[LOBBY] Match removed:', roomId);
        actions.removeOnlineGameCard(roomId);
        if (state.currentJoinedRoomId === roomId) {
            state.currentJoinedRoomId = null;
            state.currentJoinedRoomPlayers = 0;
            state.currentStartedRoomId = null;
            actions.switchView('view-lobby');
            actions.showCreateGameErrors(['La partida a la que estabas unido ya no existe.']);
            actions.syncAllJoinButtonsState();
        }

    } else if (data.type === 'MatchClosedByCreator') {
        const roomId = String(data?.payload?.roomId || '').trim();
        const cause = data?.payload?.cause || 'El creador abandonó la partida.';

        state.pendingJoinRoomIds.delete(roomId);
        state.pendingLeaveRoomIds.delete(roomId);
        actions.removeOnlineGameCard(roomId);

        if (!roomId || state.currentJoinedRoomId === roomId) {
            state.currentJoinedRoomId = null;
            state.currentJoinedRoomPlayers = 0;
            state.currentStartedRoomId = null;
        }
        if (!roomId || state.currentOwnedRoomId === roomId) {
            state.currentOwnedRoomId = null;
        }

        actions.destroyPhaserGame();
        actions.switchView('view-lobby');
        actions.showCreateGameErrors([cause]);
        actions.syncAllJoinButtonsState();

    } else if (data.type === 'MatchStarted') {
        const payload = data?.payload || {};
        const roomId = String(payload?.roomId || '').trim();

        if (roomId) {
            state.currentJoinedRoomId = roomId;
            state.currentJoinedRoomPlayers = Number(payload?.players || state.currentJoinedRoomPlayers || 0);
        }
        actions.showMatchStartView(payload);

    } else if (data.type === 'StartMatchRequestInvalid') {
        const cause = data?.payload?.cause || 'No se ha podido iniciar la partida.';
        actions.showCreateGameErrors([cause]);

    } else if (data.type === 'chat_message') {
        const { text, username_originator } = data.payload || {};
        if (text && username_originator) {
            const isOwn = username_originator === state.currentUsername;
            actions.addLobbyMessage(username_originator, text, isOwn);
        }

    } else if (data.type === 'GameCreationRequestValid') {
        if (state.createGamePendingTimeout) {
            clearTimeout(state.createGamePendingTimeout);
            state.createGamePendingTimeout = null;
        }
        actions.setCreatePendingState(false);
        actions.clearCreateGameErrors();
        actions.resetCreateGameForm();

        const createCard = state.createCard;
        if (createCard) {
            createCard.classList.add('hidden');
        }

        const payload = data?.payload || {};
        const roomId = String(payload?.roomId || '').trim();
        if (roomId) {
            state.currentJoinedRoomId = roomId;
            state.currentOwnedRoomId = roomId;
            state.currentJoinedRoomPlayers = Number(payload?.players || 1);
            state.currentStartedRoomId = null;
            state.pendingJoinRoomIds.clear();
        }
        console.log('[CREATE] GameCreationRequestValid received:', payload);
        actions.addOnlineGameCard(payload, actions.buildRandomCardColors(5));
        actions.syncAllJoinButtonsState();

    } else if (data.type === 'JoinMatchRequestValid') {
        const payload = data?.payload || {};
        const roomId = String(payload?.roomId || '').trim();
        if (roomId) {
            state.pendingJoinRoomIds.clear();
            state.currentJoinedRoomId = roomId;
            state.currentJoinedRoomPlayers = Number(payload?.players || state.currentJoinedRoomPlayers || 0);
            state.currentStartedRoomId = null;
        }
        console.log('[JOIN] JoinMatchRequestValid received:', payload);
        actions.addOnlineGameCard(payload);
        actions.syncAllJoinButtonsState();

    } else if (data.type === 'JoinMatchRequestInvalid') {
        const roomId = String(data?.payload?.roomId || '').trim();
        if (roomId) {
            state.pendingJoinRoomIds.delete(roomId);
            actions.syncAllJoinButtonsState();
        }
        const cause = data?.payload?.cause || 'No se ha podido unir a la partida.';
        console.warn('[JOIN] Join failed:', cause);
        actions.showCreateGameErrors([cause]);

    } else if (data.type === 'LeaveMatchRequestValid') {
        const roomId = String(data?.payload?.roomId || '').trim();
        state.pendingLeaveRoomIds.clear();
        state.pendingJoinRoomIds.clear();
        if (!roomId || state.currentJoinedRoomId === roomId) {
            state.currentJoinedRoomId = null;
            state.currentJoinedRoomPlayers = 0;
            state.currentStartedRoomId = null;
        }
        if (!roomId || state.currentOwnedRoomId === roomId) {
            state.currentOwnedRoomId = null;
        }
        console.log('[LEAVE] LeaveMatchRequestValid received:', data?.payload || {});
        actions.syncAllJoinButtonsState();

    } else if (data.type === 'LeaveMatchRequestInvalid') {
        state.pendingLeaveRoomIds.clear();
        const cause = data?.payload?.cause || 'No se ha podido salir de la partida.';
        console.warn('[LEAVE] Leave failed:', cause);
        actions.showCreateGameErrors([cause]);
        actions.syncAllJoinButtonsState();

    } else if (data.type === 'GameCreationRequestInvalid') {
        if (state.createGamePendingTimeout) {
            clearTimeout(state.createGamePendingTimeout);
            state.createGamePendingTimeout = null;
        }
        actions.setCreatePendingState(false);
        const cause = data?.payload?.cause || 'Error desconocido al crear la partida.';
        console.warn('[CREATE] Validation failed:', [cause]);
        actions.showCreateGameErrors([cause]);
    }
}
