# Estado de partida: almacenamiento, actualización y envío a jugadores

## Objetivo

Este documento describe, con trazabilidad técnica completa, dónde se guarda el estado actual de una partida, cómo se actualiza, qué estructuras están implicadas para un jugador y para todos los jugadores, y cómo se envía cada actualización por WebSocket.

---

## 1) Estructuras principales de estado (backend)

### 1.1 Estado global de todas las partidas activas

Archivo: `src/main/java/Apalabrazos/backend/service/MatchManager.java`

Estructuras:

- `activeMatches: Map<String, GameService>`
  - Clave: `matchId`
  - Valor: instancia `GameService` que contiene la lógica y el estado de esa partida
  - Es el registro maestro de partidas activas en memoria.

- `matchPlayerNames: Map<String, List<String>>`
  - Clave: `matchId`
  - Valor: snapshot de nombres de jugadores de la partida para lobby/UI
  - No es el estado jugable principal, es estado de presentación/sincronización de lobby.

Conclusión:

- El estado de "todas las partidas" vive en `MatchManager.activeMatches`.
- Desde cada `GameService` se llega al estado global de su partida concreta.

### 1.2 Estado global de una partida concreta

Archivo: `src/main/java/Apalabrazos/backend/model/GameGlobal.java`

Estructura central:

- `playerInstances: Map<String, GameInstance>`
  - Clave: `playerId`
  - Valor: estado de juego por jugador (`GameInstance`)
  - Es la pieza clave para responder "estado por jugador dentro de una partida".

Otros campos globales de la partida:

- `state: GameGlobalState` (`IDLE`, `CONTROLLER_READY`, `START_VALIDATED`, `INITIALIZED`, `PLAYING`, `PAUSED`, `POST`)
- `gameType`, `difficulty`, `maxPlayers`, `numberOfQuestions`
- `gameDuration`, `remainingSeconds`
- `controllerReadyPlayers: Set<String>` (confirmaciones de carga/controlador)
- `controllerReadyTimeoutSeconds`

Conclusión:

- El estado jugable global de una partida vive en `GameGlobal`.

### 1.3 Estado de un jugador dentro de una partida

Archivo: `src/main/java/Apalabrazos/backend/model/GameInstance.java`

Campos:

- `questionList: QuestionList`
  - Lista de preguntas y respuestas del jugador (incluye `Question.userResponseRecorded`).
- `gameResult: GameRecord`
  - Estadísticas/resultados acumulados del jugador.
- `currentQuestionIndex`
  - Índice de pregunta actual para ese jugador.
- `gameInstanceState: GameState` (`PENDING`, `PLAYING`, `PAUSED`, `FINISHED`)

Conclusión:

- El estado jugable individual (por jugador) vive en su `GameInstance`.

### 1.4 Estado fino por pregunta

Archivos:

- `src/main/java/Apalabrazos/backend/model/QuestionList.java`
- `src/main/java/Apalabrazos/backend/model/Question.java`

Campos clave en `Question`:

- `questionText`
- `questionResponsesList` (4 opciones)
- `correctQuestionIndex`
- `questionStatus`
- `questionLetter`
- `userResponseRecorded` (valores: `init`, `responsed_ok`, `responsed_fail`, `passed`)

Conclusión:

- La marca de si el jugador respondió bien/mal/pasó se persiste en `Question.userResponseRecorded`.

### 1.5 Estado de conexión y canal de envío

Archivos:

- `src/main/java/Apalabrazos/backend/service/ConnectionRegistry.java`
- `src/main/java/Apalabrazos/backend/model/Player.java`

Estructuras:

- `ConnectionRegistry.activeConnections: Map<UUID, Player>`
  - sesión WebSocket -> `Player`
- `Player.sender: MessageSender`
  - Canal de salida real al cliente.

Conclusión:

- El estado de juego se guarda en `GameGlobal/GameInstance`.
- La capacidad de "a quién puedo enviar" se resuelve mediante `ConnectionRegistry` y `Player.sender`.

---

## 2) Flujo de actualización del estado

## 2.1 Entrada desde cliente (WebSocket)

Archivo: `src/main/java/Apalabrazos/backend/network/server/JavalinConnectionHandler.java`

Mensajes entrantes relevantes:

- `GameControllerReady`
  - Llama a `matchManager.markMatchControllerReady(roomId, playerId)`.

- `AnswerSubmitted`
  - Parsea `questionIndex` y `selectedOption`.
  - Llama a `matchManager.submitAnswerForPlayer(playerId, questionIndex, selectedOption)`.

## 2.2 Enrutado a la partida correcta

Archivo: `src/main/java/Apalabrazos/backend/service/MatchManager.java`

- `submitAnswerForPlayer(...)`
  - Busca la partida actual del jugador con `findJoinedMatchIdForPlayer(playerId)`.
  - Obtiene `GameService` por `matchId`.
  - Publica `AnswerSubmittedEvent` en el bus externo de esa partida.

- `markMatchControllerReady(...)`
  - Obtiene `GameService` por `matchId`.
  - Publica `GameControllerReady` en el bus externo de esa partida.

## 2.3 Mutación de estado real en lógica de juego

Archivo: `src/main/java/Apalabrazos/backend/service/GameService.java`

Puntos principales:

- `handleAnswerSubmitted(AnswerSubmittedEvent event)`
  - Obtiene `GameInstance` del jugador desde `GlobalGameInstance.getPlayerInstance(playerId)`.
  - Obtiene `Question` por índice.
  - Calcula si es correcta/incorrecta/pasada.
  - Actualiza `question.setUserResponseRecorded(...)`.
  - Calcula totales (`correct/incorrect`) con `getCorrectIncorrectTotals()`.
  - Publica `AnswerValidatedEvent`.
  - Calcula siguiente pregunta y publica `QuestionChangedEvent` para ese jugador.

- `handleTimerTick(TimerTickEvent event)`
  - Decrementa `GameGlobal.remainingSeconds`.
  - Publica `TimerTickEvent(remaining)` por bus externo.
  - Si tiempo agotado: `finishGame()`.

- `finishGame()`
  - Cambia `GameGlobal.state` a `POST`.
  - Detiene `TimeService`.
  - Construye `GameRecord` final por jugador.
  - Publica `GameFinishedEvent` en bus externo.

- `GameStartedValid()` + `transitionStartValidated()` + `transitionControllerReady()`
  - Controlan transición de estados de la partida (`IDLE`, `START_VALIDATED`, `CONTROLLER_READY`, `INITIALIZED`, `PLAYING`).

---

## 3) Cómo se envía el estado a los jugadores

## 3.1 Puente de red de una partida

Archivo: `src/main/java/Apalabrazos/backend/service/MatchManager.java`

Método clave: `registerMatchNetworkBridge(matchId, service)`.

Este bridge escucha eventos del `GameService` y decide el envío por socket a `Player.sendMessage(...)`.

Eventos puenteados actualmente:

1. `TimerTickEvent`
   - Mensaje enviado: `type = TimerTick`, payload `{ remaining }`
   - Estrategia: recorrido de `gi.getAllPlayerIds()`
   - Resultado: broadcast a todos los jugadores de la partida.

2. `AnswerValidatedEvent`
   - Mensaje enviado: `type = AnswerValidated`, payload con `answerResult` completo
   - Estrategia: usa `answerValidated.getPlayerId()` como destinatario único
   - Resultado: unicast solo al jugador afectado.

3. `QuestionChangedEvent`
   - Mensaje enviado: `type = QuestionChanged`, payload con índice, estado, `nextQuestion`, totales
   - Estrategia:
     - Si `playerId` viene informado: unicast al jugador objetivo.
     - Si `playerId` viene vacío: broadcast a todos.
   - Resultado actual real del flujo de respuestas: unicast, porque `GameService.publishQuestionForPlayer(...)` siempre incluye `playerId`.

## 3.2 Inicio de partida

Archivo: `src/main/java/Apalabrazos/backend/service/MatchManager.java`

- `broadcastMatchStarted(...)`
  - Mensaje `MatchStarted` para cada jugador conectado de la partida.
  - Estrategia: broadcast por iteración de `allPlayerIds`.

- En `handlePlayerJoined(...)`
  - Si la partida ya estaba iniciada, el jugador que entra tarde recibe `MatchStarted` en unicast.

## 3.3 Fin de partida

`GameService.finishGame()` publica `GameFinishedEvent`, pero el bridge de `MatchManager.registerMatchNetworkBridge(...)` no tiene rama para `GameFinishedEvent`.

Implicación técnica actual:

- El evento de fin existe internamente en backend.
- No hay envío socket explícito `type = GameFinished` desde ese bridge.
- En cliente (`src/main/resources/public/js/network/message-handler.js`) tampoco hay rama para `GameFinished`.

---

## 4) ¿Se envía cada actualización a todos o solo al afectado?

Resumen directo:

- `TimerTick`: a todos los jugadores de la partida.
- `MatchStarted`: a todos los jugadores de la partida (y unicast al que entra tarde si ya empezó).
- `AnswerValidated`: solo al jugador afectado.
- `QuestionChanged`:
  - por diseño soporta ambas opciones,
  - en el flujo actual de respuesta se envía solo al jugador afectado.
- `GameFinished`: actualmente no se envía por socket desde el bridge (aunque sí se publica como evento interno).

---

## 5) Detalle absoluto: estado para uno vs estado para todos

## 5.1 Estado para un jugador (nivel más detallado)

Ruta de acceso:

`MatchManager.activeMatches[matchId]`
-> `GameService.GlobalGameInstance`
-> `GameGlobal.playerInstances[playerId]`
-> `GameInstance`

Contenido del `GameInstance`:

- `currentQuestionIndex`
- `gameInstanceState`
- `questionList`:
  - `Question[0..N]` con:
    - texto
    - opciones
    - índice correcto
    - `questionStatus`
    - `questionLetter`
    - `userResponseRecorded` (estado real de respuesta del jugador)
- `gameResult`:
  - correctas
  - incorrectas
  - pasadas
  - tiempo total
  - score

## 5.2 Estado para todos los jugadores de una partida

Ruta de acceso:

`MatchManager.activeMatches[matchId]`
-> `GameService.GlobalGameInstance`
-> `GameGlobal.playerInstances` (mapa completo de jugadores)

Contenido agregado:

- Conjunto de `playerId` en partida.
- `GameInstance` de cada jugador.
- Estado global de la partida (`GameGlobal.state`).
- Config global (`difficulty`, `gameType`, `maxPlayers`, `numberOfQuestions`, `gameDuration`).
- Tiempo restante global (`remainingSeconds`).
- Confirmaciones de ready (`controllerReadyPlayers`).

## 5.3 Estado para todas las partidas del servidor

Ruta de acceso:

`MatchManager.activeMatches` completo.

Contenido:

- Todas las partidas activas (`matchId -> GameService`).
- De cada una se puede entrar a su `GameGlobal` y de ahí a todos sus jugadores.

---

## 6) Mapa rápido de responsabilidades

- `JavalinConnectionHandler`
  - Entrada de mensajes de cliente.
- `MatchManager`
  - Registro de partidas, routing por `matchId`, bridge de salida por socket.
- `GameService`
  - Lógica de negocio y mutación del estado real.
- `GameGlobal`
  - Estado global de la partida.
- `GameInstance`
  - Estado individual por jugador.
- `ConnectionRegistry` + `Player`
  - Resolución de conexiones y canal de envío a cliente.

---

## 7) Observaciones técnicas importantes del comportamiento actual

1. `GameFinishedEvent` se publica internamente, pero no se transforma en mensaje WebSocket en el bridge actual de `MatchManager`.

2. En `loadQuestionsForAllPlayers()` se carga una `QuestionList` y se asigna esa misma referencia a todas las `GameInstance` de los jugadores. Dado que `handleAnswerSubmitted()` muta `Question.userResponseRecorded`, esta decisión de referencia compartida impacta directamente cómo se comparte/mezcla el estado de preguntas entre jugadores.

3. El cliente web (`message-handler.js`) maneja `TimerTick`, `AnswerValidated`, `QuestionChanged`, pero no maneja `GameFinished`.

---

## 8) Archivos fuente clave consultados

- `src/main/java/Apalabrazos/backend/service/MatchManager.java`
- `src/main/java/Apalabrazos/backend/service/GameService.java`
- `src/main/java/Apalabrazos/backend/model/GameGlobal.java`
- `src/main/java/Apalabrazos/backend/model/GameInstance.java`
- `src/main/java/Apalabrazos/backend/model/QuestionList.java`
- `src/main/java/Apalabrazos/backend/model/Question.java`
- `src/main/java/Apalabrazos/backend/model/GameRecord.java`
- `src/main/java/Apalabrazos/backend/service/ConnectionRegistry.java`
- `src/main/java/Apalabrazos/backend/model/Player.java`
- `src/main/java/Apalabrazos/backend/network/server/JavalinConnectionHandler.java`
- `src/main/resources/public/js/network/message-handler.js`
- `src/main/resources/public/js/phaser_src/phaserEventBus.js`
