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
import { PhaserEventBus, emitSticky } from '../phaser_src/phaserEventBus.js';
import { WS_MESSAGE_TYPE } from './message-types.js';

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
// Helpers
// ---------------------------------------------------------------------------

/**
 * Returns true when `roomId` matches the player's currently active room.
 * Used by in-game event handlers to discard stale or foreign messages.
 */
function _isActiveRoom(roomId, state) {
    const activeRoomId = String(state.currentStartedRoomId || state.currentJoinedRoomId || '').trim();
    return Boolean(roomId) && (!activeRoomId || roomId === activeRoomId);
}

// ---------------------------------------------------------------------------
// Individual message handlers — one function per server message type
// ---------------------------------------------------------------------------

function _handleTimerTick(data, state) {
    const roomId = String(data?.payload?.roomId || '').trim();
    if (!_isActiveRoom(roomId, state)) return;

    const remaining = data?.payload?.remaining ?? 0;
    PhaserEventBus.emit('net:timerTick', { remaining });
}

function _handleExtraTimeScore(data, state) {
    const payload = data?.payload || {};
    const roomId = String(payload?.roomId || '').trim();
    if (!_isActiveRoom(roomId, state)) return;

    PhaserEventBus.emit('net:extraTimeScore', {
        roomId,
        playerId:         payload?.playerId,
        remainingSeconds: payload?.remainingSeconds,
        extraTimeScore:   payload?.extraTimeScore,
        totalScore:       payload?.totalScore,
    });
}

function _handleAnswerValidated(data, state) {
    const roomId = String(data?.payload?.roomId || '').trim();
    if (!_isActiveRoom(roomId, state)) return;

    const answerResult = data?.payload?.answerResult || null;
    if (answerResult) {
        emitSticky('net:answerValidated', answerResult);
    }
}

function _handleQuestionChanged(data, state) {
    const payload = data?.payload || {};
    const roomId = String(payload?.roomId || '').trim();
    if (!_isActiveRoom(roomId, state)) return;

    const nextQuestion = payload?.nextQuestion || null;
    const responsesCount = Array.isArray(nextQuestion?.questionResponsesList)
        ? nextQuestion.questionResponsesList.length
        : 0;
    console.log('[GAME][NET] QuestionChanged payload summary:', {
        questionIndex:   payload?.questionIndex,
        hasNextQuestion: Boolean(nextQuestion),
        questionText:    nextQuestion?.questionText || null,
        responsesCount,
    });
    emitSticky('net:questionChanged', payload);
    console.log('[GAME] QuestionChanged received:', payload);
}

function _handleStandings(data, state) {
    const payload = data?.payload || {};
    const roomId = String(payload?.roomId || '').trim();
    if (!_isActiveRoom(roomId, state)) return;

    const standings = Array.isArray(payload?.standings) ? payload.standings : [];
    emitSticky('net:standings', standings);
}

function _handleLobbyMatchesSnapshot(data, _state, actions) {
    const matches = Array.isArray(data?.payload?.matches) ? data.payload.matches : [];
    console.log(`[LOBBY] Snapshot received with ${matches.length} matches`);
    actions.renderLobbyMatchesSnapshot(matches);
}

function _handleLobbyMatchCreated(data, _state, actions) {
    const payload = data?.payload || {};
    console.log('[LOBBY] Real-time match broadcast received:', payload);
    actions.addOnlineGameCard(payload);
}

function _handleLobbyMatchUpdated(data, _state, actions) {
    const payload = data?.payload || {};
    console.log('[LOBBY] Match updated:', payload);
    actions.addOnlineGameCard(payload);
}

function _handleLobbyMatchRemoved(data, state, actions) {
    const roomId = String(data?.payload?.roomId || '').trim();
    console.log('[LOBBY] Match removed:', roomId);

    const wasActiveRoom = Boolean(roomId) && (
        state.currentJoinedRoomId === roomId ||
        state.currentStartedRoomId === roomId
    );
    actions.removeOnlineGameCard(roomId);

    if (wasActiveRoom) {
        state.currentJoinedRoomId = null;
        state.currentJoinedRoomPlayers = 0;
        state.currentStartedRoomId = null;
        state.currentOwnedRoomId = null;
        actions.destroyPhaserGame();
        actions.switchView('view-lobby');
        actions.syncAllJoinButtonsState();
    }
}

function _handleMatchClosedByCreator(data, state, actions) {
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
}

function _handleMatchStarted(data, state, actions) {
    const payload = data?.payload || {};
    const roomId = String(payload?.roomId || '').trim();
    const joinedRoomId = String(state.currentJoinedRoomId || '').trim();

    if (!roomId || !joinedRoomId || roomId !== joinedRoomId) return;

    state.currentJoinedRoomId = roomId;
    state.currentJoinedRoomPlayers = Number(payload?.players || state.currentJoinedRoomPlayers || 0);
    actions.showMatchStartView(payload);
}

function _handleStartMatchRequestInvalid(data, _state, actions) {
    const cause = data?.payload?.cause || 'No se ha podido iniciar la partida.';
    actions.showCreateGameErrors([cause]);
}

function _handleChatMessage(data, state, actions) {
    const { text, username_originator } = data.payload || {};
    if (text && username_originator) {
        const isOwn = username_originator === state.currentUsername;
        actions.addLobbyMessage(username_originator, text, isOwn);
    }
}

function _handleGameCreationRequestValid(data, state, actions) {
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
}

function _handleGameCreationRequestInvalid(data, state, actions) {
    if (state.createGamePendingTimeout) {
        clearTimeout(state.createGamePendingTimeout);
        state.createGamePendingTimeout = null;
    }
    actions.setCreatePendingState(false);
    const cause = data?.payload?.cause || 'Error desconocido al crear la partida.';
    console.warn('[CREATE] Validation failed:', [cause]);
    actions.showCreateGameErrors([cause]);
}

function _handleJoinMatchRequestValid(data, state, actions) {
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
}

function _handleJoinMatchRequestInvalid(data, state, actions) {
    const roomId = String(data?.payload?.roomId || '').trim();
    if (roomId) {
        state.pendingJoinRoomIds.delete(roomId);
        actions.syncAllJoinButtonsState();
    }
    const cause = data?.payload?.cause || 'No se ha podido unir a la partida.';
    console.warn('[JOIN] Join failed:', cause);
    actions.showCreateGameErrors([cause]);
}

function _handleLeaveMatchRequestValid(data, state, actions) {
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
}

function _handleLeaveMatchRequestInvalid(data, state, actions) {
    state.pendingLeaveRoomIds.clear();
    const cause = data?.payload?.cause || 'No se ha podido salir de la partida.';
    console.warn('[LEAVE] Leave failed:', cause);
    actions.showCreateGameErrors([cause]);
    actions.syncAllJoinButtonsState();
}

function _handleGameFinished(data, state) {
    const payload = data?.payload || {};
    const roomId = String(payload?.roomId || '').trim();
    if (!_isActiveRoom(roomId, state)) return;

    console.log('[GAME] GameFinished received:', payload);
    emitSticky('net:gameFinished', payload);
}

// ---------------------------------------------------------------------------
// Dispatch table — maps each message type to its handler
// ---------------------------------------------------------------------------

const _handlers = {
    [WS_MESSAGE_TYPE.TIMER_TICK]:                   _handleTimerTick,
    [WS_MESSAGE_TYPE.EXTRA_TIME_SCORE]:             _handleExtraTimeScore,
    [WS_MESSAGE_TYPE.ANSWER_VALIDATED]:             _handleAnswerValidated,
    [WS_MESSAGE_TYPE.QUESTION_CHANGED]:             _handleQuestionChanged,
    [WS_MESSAGE_TYPE.STANDINGS]:                    _handleStandings,
    [WS_MESSAGE_TYPE.LOBBY_MATCHES_SNAPSHOT]:       _handleLobbyMatchesSnapshot,
    [WS_MESSAGE_TYPE.LOBBY_MATCH_CREATED]:          _handleLobbyMatchCreated,
    [WS_MESSAGE_TYPE.LOBBY_MATCH_UPDATED]:          _handleLobbyMatchUpdated,
    [WS_MESSAGE_TYPE.LOBBY_MATCH_REMOVED]:          _handleLobbyMatchRemoved,
    [WS_MESSAGE_TYPE.MATCH_CLOSED_BY_CREATOR]:      _handleMatchClosedByCreator,
    [WS_MESSAGE_TYPE.MATCH_STARTED]:                _handleMatchStarted,
    [WS_MESSAGE_TYPE.START_MATCH_REQUEST_INVALID]:  _handleStartMatchRequestInvalid,
    [WS_MESSAGE_TYPE.CHAT_MESSAGE]:                 _handleChatMessage,
    [WS_MESSAGE_TYPE.GAME_CREATION_REQUEST_VALID]:  _handleGameCreationRequestValid,
    [WS_MESSAGE_TYPE.GAME_CREATION_REQUEST_INVALID]:_handleGameCreationRequestInvalid,
    [WS_MESSAGE_TYPE.JOIN_MATCH_REQUEST_VALID]:     _handleJoinMatchRequestValid,
    [WS_MESSAGE_TYPE.JOIN_MATCH_REQUEST_INVALID]:   _handleJoinMatchRequestInvalid,
    [WS_MESSAGE_TYPE.LEAVE_MATCH_REQUEST_VALID]:    _handleLeaveMatchRequestValid,
    [WS_MESSAGE_TYPE.LEAVE_MATCH_REQUEST_INVALID]:  _handleLeaveMatchRequestInvalid,
    [WS_MESSAGE_TYPE.GAME_FINISHED]:                _handleGameFinished,
};

// ---------------------------------------------------------------------------
// Internal router — dispatches to the appropriate handler by message type
// ---------------------------------------------------------------------------

function _route(data, state, actions) {
    const handler = _handlers[data?.type];
    if (handler) {
        handler(data, state, actions);
    }
}
