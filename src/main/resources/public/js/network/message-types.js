// js/network/message-types.js
//
// Centralized constants for WebSocket message types received from the server.
// Mirror of the Java class: Apalabrazos.backend.network.WsMessageType
//
// Import this wherever an inbound message type string is compared or stored,
// so that renaming a type only requires a single-place change.

export const WS_MESSAGE_TYPE = Object.freeze({

    // ── Lobby events ────────────────────────────────────────────────────────

    /** Sent once to a newly connected player with the current active-match list. */
    LOBBY_MATCHES_SNAPSHOT:          'LobbyMatchesSnapshot',

    /** Broadcast when a new match is created in the lobby. */
    LOBBY_MATCH_CREATED:             'LobbyMatchCreated',

    /** Broadcast when an existing match is updated. */
    LOBBY_MATCH_UPDATED:             'LobbyMatchUpdated',

    /** Broadcast when a match is removed from the lobby. */
    LOBBY_MATCH_REMOVED:             'LobbyMatchRemoved',

    /** Broadcast chat message to all lobby players. */
    CHAT_MESSAGE:                    'chat_message',

    // ── Match lifecycle events ───────────────────────────────────────────────

    /** Sent to the creator when a game creation request is accepted. */
    GAME_CREATION_REQUEST_VALID:     'GameCreationRequestValid',

    /** Sent to the creator when a game creation request is rejected. */
    GAME_CREATION_REQUEST_INVALID:   'GameCreationRequestInvalid',

    /** Sent to the joining player when a join-match request is accepted. */
    JOIN_MATCH_REQUEST_VALID:        'JoinMatchRequestValid',

    /** Sent to the joining player when a join-match request is rejected. */
    JOIN_MATCH_REQUEST_INVALID:      'JoinMatchRequestInvalid',

    /** Sent to the leaving player when a leave-match request is accepted. */
    LEAVE_MATCH_REQUEST_VALID:       'LeaveMatchRequestValid',

    /** Sent to the leaving player when a leave-match request is rejected. */
    LEAVE_MATCH_REQUEST_INVALID:     'LeaveMatchRequestInvalid',

    /** Sent to the creator when a start-match request is rejected. */
    START_MATCH_REQUEST_INVALID:     'StartMatchRequestInvalid',

    /** Sent to every player in the match when the match starts. */
    MATCH_STARTED:                   'MatchStarted',

    /** Sent to non-creator players when the creator leaves the match. */
    MATCH_CLOSED_BY_CREATOR:         'MatchClosedByCreator',

    // ── In-game events ───────────────────────────────────────────────────────

    /** Periodic timer countdown tick. */
    TIMER_TICK:                      'TimerTick',

    /** Extra-time score bonus awarded to a specific player. */
    EXTRA_TIME_SCORE:                'ExtraTimeScore',

    /** Answer validation result. */
    ANSWER_VALIDATED:                'AnswerValidated',

    /** Next question for the player(s). */
    QUESTION_CHANGED:                'QuestionChanged',

    /** Current standings / leaderboard. */
    STANDINGS:                       'Standings',

    /** Sent to all players when the game session ends. */
    GAME_FINISHED:                   'GameFinished',
});
