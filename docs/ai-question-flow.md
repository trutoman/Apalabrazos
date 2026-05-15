# Flujo de Preguntas IA (nuevo)

## Objetivo

Documentar el flujo actual para precarga de preguntas al crear/iniciar partida, usando el bus de eventos global y el bridge a WebSocket.

## Diagrama secuencial (corto)

```mermaid
sequenceDiagram
    participant FE as Frontend (WS)
    participant MM as MatchManager
    participant GS as GameService
    participant BUS as GlobalAsyncEventBus
    participant AI as AIQuestionService

    FE->>MM: GameCreationRequest / StartMatch
    MM->>GS: crear partida y startQuestionPreload()
    GS->>BUS: AIQuestionPreloadRequestedEvent
    BUS->>AI: onEvent(requested)
    AI->>AI: generar preguntas (endpoint IA, fallback opcional)

    alt Precarga OK
        AI->>BUS: AIQuestionPreloadCompletedEvent
        BUS->>GS: onGlobalEvent(completed)
        GS->>GS: loadQuestionsForAllPlayersWithTimeout()
        GS->>MM: QuestionChangedEvent (primera pregunta)
        MM->>FE: WS QUESTION_CHANGED
    else Precarga FAIL/TIMEOUT
        AI->>BUS: AIQuestionPreloadFailedEvent
        BUS->>GS: onGlobalEvent(failed)
        GS->>MM: QuestionLoadErrorEvent
        MM->>FE: WS QUESTION_LOAD_ERROR
    end
```

## Tabla de eventos

| Evento | Emisor | Receptor | Payload principal |
|---|---|---|---|
| `AIQuestionPreloadRequestedEvent` | `GameService.startQuestionPreload()` | `AIQuestionService` (vía `GlobalAsyncEventBus`) | `matchId`, `numberOfQuestions`, `allowFallback` |
| `AIQuestionPreloadCompletedEvent` | `AIQuestionService` | `GameService` (vía `GlobalAsyncEventBus`) | `matchId`, `questions` (`QuestionList`), `source` |
| `AIQuestionPreloadFailedEvent` | `AIQuestionService` | `GameService` (vía `GlobalAsyncEventBus`) | `matchId`, `errorMessage`, `errorReason` |
| `QuestionChangedEvent` | `GameService` (bus externo de partida) | `MatchManager` (network bridge) | `questionIndex`, `status`, `playerId` (opcional), `nextQuestion`, `totalCorrect`, `totalIncorrect` |
| `WS QUESTION_CHANGED` | `MatchManager.sendQuestionChangedToPlayers()` | Frontend (`message-handler.js`) | `payload.roomId`, `payload.questionIndex`, `payload.status`, `payload.nextQuestion`, `payload.totalCorrect`, `payload.totalIncorrect` |
| `QuestionLoadErrorEvent` | `GameService` (cuando hay timeout/error al cargar) | `MatchManager` (network bridge) | `matchId`, `errorMessage`, `errorReason` |
| `WS QUESTION_LOAD_ERROR` | `MatchManager.sendQuestionLoadErrorToPlayers()` | Frontend (`message-handler.js`) | `payload.roomId`, `payload.errorMessage`, `payload.errorReason` |

## Notas rápidas

- `errorReason` esperado: `TIMEOUT`, `LOAD_FAILED` (y compatibles futuros).
- El frontend filtra por sala activa antes de actuar sobre el evento.
- En error de carga, el frontend muestra modal explícito de cancelación y vuelve al lobby.