package Apalabrazos.backend.network;

/**
 * Centralized constants for WebSocket message types sent from the server to connected clients.
 * <p>
 * Use these constants wherever a message {@code "type"} field is constructed so that
 * renaming or auditing a message type only requires a change in a single place.
 * <p>
 * The equivalent JS constants live in {@code js/network/message-types.js}.
 */
public final class WsMessageType {

    private WsMessageType() {}

    // ── Lobby events ──────────────────────────────────────────────────────────

    /** Sent once to a newly connected player with the current list of active matches. */
    public static final String LOBBY_MATCHES_SNAPSHOT     = "LobbyMatchesSnapshot";

    /** Broadcast to all lobby players when a new match is created. */
    public static final String LOBBY_MATCH_CREATED        = "LobbyMatchCreated";

    /** Broadcast to all lobby players when an existing match is updated. */
    public static final String LOBBY_MATCH_UPDATED        = "LobbyMatchUpdated";

    /** Broadcast to all lobby players when a match is removed. */
    public static final String LOBBY_MATCH_REMOVED        = "LobbyMatchRemoved";

    /** Broadcast chat message to all lobby players. */
    public static final String CHAT_MESSAGE               = "chat_message";

    // ── Match lifecycle events ─────────────────────────────────────────────────

    /** Sent to the creator when a game creation request is accepted. */
    public static final String GAME_CREATION_REQUEST_VALID   = "GameCreationRequestValid";

    /** Sent to the creator when a game creation request is rejected. */
    public static final String GAME_CREATION_REQUEST_INVALID = "GameCreationRequestInvalid";

    /** Sent to the joining player when a join-match request is accepted. */
    public static final String JOIN_MATCH_REQUEST_VALID      = "JoinMatchRequestValid";

    /** Sent to the joining player when a join-match request is rejected. */
    public static final String JOIN_MATCH_REQUEST_INVALID    = "JoinMatchRequestInvalid";

    /** Sent to the leaving player when a leave-match request is accepted. */
    public static final String LEAVE_MATCH_REQUEST_VALID     = "LeaveMatchRequestValid";

    /** Sent to the leaving player when a leave-match request is rejected. */
    public static final String LEAVE_MATCH_REQUEST_INVALID   = "LeaveMatchRequestInvalid";

    /** Sent to the creator when a start-match request is rejected. */
    public static final String START_MATCH_REQUEST_INVALID   = "StartMatchRequestInvalid";

    /** Sent to every player in the match when the match starts. */
    public static final String MATCH_STARTED              = "MatchStarted";

    /** Sent to non-creator players when the creator leaves the match. */
    public static final String MATCH_CLOSED_BY_CREATOR    = "MatchClosedByCreator";

    // ── In-game events ─────────────────────────────────────────────────────────

    /** Periodic timer countdown tick. */
    public static final String TIMER_TICK                 = "TimerTick";

    /** Extra-time score bonus awarded to a specific player. */
    public static final String EXTRA_TIME_SCORE           = "ExtraTimeScore";

    /** Answer validation result sent to the player who submitted the answer. */
    public static final String ANSWER_VALIDATED           = "AnswerValidated";

    /** Next question delivered to the player(s). */
    public static final String QUESTION_CHANGED           = "QuestionChanged";

    /** Question preload/loading failure reported before gameplay can continue. */
    public static final String QUESTION_LOAD_ERROR        = "QuestionLoadError";

    /** Current standings / leaderboard broadcast to all players in the match. */
    public static final String STANDINGS                  = "Standings";

    /** Sent to all players in the match when the game session ends. */
    public static final String GAME_FINISHED              = "GameFinished";
}
