# WebSocket → GameSession Architecture

## Flujo Completo: Nivel 1 a Nivel 4

Cuando un cliente físico se conecta al servidor WebSocket, ocurre este flujo:

```
┌─────────────────────────────────────────────────────────────────┐
│ NIVEL 1: RED - WebSocket Connection                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Evento: Cliente conecta con username                           │
│  ↓                                                               │
│  @OnWebSocketOpen (ConnectionHandler)                           │
│    onClientConnect(session, username)                           │
│                                                                  │
└──────────────────┬──────────────────────────────────────────────┘
                   │ new WebSocketMessageSender()
                   │ new Player()
                   ↓
┌─────────────────────────────────────────────────────────────────┐
│ NIVEL 2: SESIÓN - Player Management                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Player player = new Player(sessionId, username, sender)        │
│  ├─ Identity: SessionID, Username, PlayerID                     │
│  ├─ State: LOBBY, MATCHMAKING, PLAYING, DISCONNECTED            │
│  └─ Channel: MessageSender (abstracción)                        │
│                                                                  │
│  GameSessionManager.registerConnection(player)                  │
│  └─ Map<UUID, Player> activeConnections                         │
│                                                                  │
└──────────────────┬──────────────────────────────────────────────┘
                   │ Cliente envía mensaje
                   │ @OnWebSocketMessage
                   ↓
┌─────────────────────────────────────────────────────────────────┐
│ NIVEL 3: MENSAJE & EVENTO - Event Bus                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ConnectionHandler.onMessage(sessionId, messageContent)         │
│    ↓                                                             │
│  eventBus.publish(new GameCreationRequestedEvent(...))          │
│    ↓                                                             │
│  AsyncEventBus procesa en VIRTUAL THREAD                        │
│    ├─ GameSessionManager.onEvent()  ← virtual thread 1          │
│    ├─ GameService.onEvent()          ← virtual thread 2         │
│    └─ Otros listeners...             ← virtual thread N         │
│                                                                  │
│  NO BLOQUEA el hilo WebSocket                                   │
│                                                                  │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ↓
┌─────────────────────────────────────────────────────────────────┐
│ NIVEL 4: LÓGICA - Game Logic & Responses                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  GameSessionManager.handleGameCreationRequested()               │
│  ├─ Crea GameService                                            │
│  ├─ Publica GameSessionCreatedEvent                             │
│  └─ Notifica al jugador: player.sendMessage(...)               │
│                                                                  │
│  GameService.onGameStartedRequest()                             │
│  ├─ Inicia TimeService (timer)                                  │
│  ├─ Publica GameStartedRequestEvent                             │
│  └─ Notifica a todos los jugadores                              │
│                                                                  │
│  Los mensajes se envían a través del Player:                    │
│    player.sendMessage(message)                                  │
│      ↓                                                           │
│    sender.send(message)   ← WebSocketMessageSender              │
│      ↓                                                           │
│    session.sendMessage(json)  ← WebSocket real (a implementar)  │
│                                                                  │
└──────────────────┬──────────────────────────────────────────────┘
                   │
                   ↓
┌─────────────────────────────────────────────────────────────────┐
│ CLIENTE RECIBE RESPUESTA                                        │
│                                                                  │
│  {"type": "gameCreated", "roomId": "..."}                       │
│  {"type": "gameStarted", "question": "..."}                     │
│  {"type": "answerResult", "correct": true, "points": 10}        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Código: ConnectionHandler → Player

```java
// En WebSocket Server
public abstract class ConnectionHandler {

    // Entrada: Cliente conecta
    protected void onClientConnect(Object session, String username) {
        // 1. Crea MessageSender para WebSocket
        UUID sessionId = UUID.randomUUID();
        WebSocketMessageSender messageSender =
            new WebSocketMessageSender(session, sessionId.toString());

        // 2. Crea Player (el ANCLA)
        Player player = new Player(sessionId, username, messageSender);

        // 3. Registra en GameSessionManager
        GameSessionManager.getInstance().registerConnection(player);
    }

    // Entrada: Cliente envía mensaje
    protected void onClientMessage(UUID sessionId, String messageContent) {
        Player player = GameSessionManager.getInstance()
                           .getPlayerBySessionId(sessionId);
        if (player != null) {
            // Aquí se procesa: JSON → GameEvent → EventBus
        }
    }

    // Entrada: Cliente desconecta
    protected void onClientDisconnect(UUID sessionId) {
        GameSessionManager.getInstance()
            .unregisterConnection(sessionId);
    }
}
```

## Implementaciones Concretas

### Java WebSocket API (javax.websocket)

```java
@ServerEndpoint("/ws/game/{username}")
public class GameWebSocketEndpoint extends JavaWebSocketHandler {

    @OnOpen
    public void onOpen(Session session,
                      @PathParam("username") String username) {
        onOpen(session, username);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        UUID sessionId = extractSessionId(session);
        onMessage(message, sessionId);
    }

    @OnClose
    public void onClose(Session session) {
        UUID sessionId = extractSessionId(session);
        onClose(sessionId);
    }
}
```

### Spring WebSocket

```java
@Component
public class GameWebSocketHandler
    extends SpringWebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String username = extractUsername(session);
        afterConnectionEstablished(session, username);
    }

    @Override
    public void handleMessage(WebSocketSession session,
                             WebSocketMessage<?> message) {
        UUID sessionId = extractSessionId(session);
        handleMessage(sessionId, (String) message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                     CloseStatus closeStatus) {
        UUID sessionId = extractSessionId(session);
        afterConnectionClosed(sessionId);
    }
}
```

## Ventajas de esta Arquitectura

### 1. **Separación Clara de Niveles**
- Nivel 1: Red (WebSocket)
- Nivel 2: Sesión (Player)
- Nivel 3: Eventos (AsyncEventBus)
- Nivel 4: Lógica (GameService)

### 2. **Desacoplamiento Total**
- GameSessionManager NO sabe nada de WebSocket
- GameService NO sabe nada de Player
- Todo está conectado por eventos asíncrónos

### 3. **Testeable**
- MockMessageSender para testing sin WebSocket real
- Puedes crear Scenarios completos sin servidor

### 4. **Escalable**
- Virtual threads en AsyncEventBus
- Millones de conexiones simultáneas
- Sin bloqueos entre niveles

### 5. **Player como Ancla**
- Vive durante toda la sesión
- ÚNICA forma de comunicarse con el cliente
- Su estado refleja la realidad del jugador

## Flujo de Mensajes Específico

### Ejemplo: Cliente crea partida

```
Cliente                    WebSocket           GameSessionManager    GameService
  │                            │                       │                  │
  ├──createGame──────────────→ │                       │                  │
  │                            │                       │                  │
  │                            ├─ onMessage() ────────→ │                  │
  │                            │                       │                  │
  │                            │                  publish(GameCreationRequestedEvent)
  │                            │                       │                  │
  │                            │                       │←─ virtual thread 1
  │                            │                       │
  │                            │←────── gameSessionCreated ─ eventBus ────→ │
  │                            │                       │    virtual thread 2 │
  │                            │                       │                  │
  │←──gameCreated────────────── ←────── player.sendMessage() ────────────→ │
```

1. Cliente envía `createGame`
2. WebSocket recibe en `onMessage`
3. Publica evento a AsyncEventBus
4. GameSessionManager y GameService escuchan (virtual threads)
5. Ellos se comunican entre sí por eventos
6. Finalmente, envían mensaje al cliente por `player.sendMessage()`

## implementación: WebSocketMessageSender

Actualmente `WebSocketMessageSender` está scaffolding (skeleton).

Para completarlo, descomenta la lógica real de envío:

```java
@Override
public void send(Object message) {
    if (!connected) {
        messageQueue.offer(message);
        return;
    }

    try {
        String json = serializeToJson(message);  // Serializar objeto
        session.getBasicRemote().sendText(json); // Enviar por WebSocket
    } catch (Exception e) {
        connected = false;
        messageQueue.offer(message);
    }
}
```

Necesitarás:
- Jackson para `serializeToJson()`
- La sesión real de javax.websocket.Session

## Estado Actual

✅ Arquitectura completamente diseñada
✅ Player implementado como ancla
✅ GameSessionManager maneja conexiones
✅ AsyncEventBus procesa en virtual threads
✅ ConnectionHandler como interfaz
✅ WebSocketMessageSender skeleton
✅ Ejemplos funcionales

🔄 **Por implementar:**
1. Serialización JSON (Jackson)
2. WebSocket real (Java WebSocket o Spring)
3. Integración con frontend
4. Tests end-to-end
