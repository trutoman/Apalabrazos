# Análisis y Plan de Modernización - Apalabrazos 🎯

## 📊 ANÁLISIS ACTUAL DEL PROYECTO

### Problemas Críticos Identificados

#### 1. **EventBus Síncrono y Bloqueante**
```java
// Actual: EventBus.java (línea 54)
public void publish(GameEvent event) {
    for (EventListener listener : new ArrayList<>(listeners)) {
        try {
            listener.onEvent(event);  // ❌ BLOQUEANTE - ejecución síncrona
        } catch (Exception e) {
            log.error("Error processing event: {}", e.getMessage(), e);
        }
    }
}
```

**Problemas:**
- Todos los listeners se ejecutan secuencialmente en el mismo hilo
- Si un listener tarda, bloquea todos los demás
- No hay concurrencia ni paralelismo
- El publicador espera a que TODOS los listeners terminen antes de continuar

#### 2. **TimeService Ineficiente**
```java
// Actual: TimeService.java (línea 25-39)
worker = new Thread(() -> {
    while (running) {
        try {
            Thread.sleep(1000);  // ❌ INEFICIENTE - polling constante
        } catch (InterruptedException e) {
            break;
        }
        eventBus.publish(new TimerTickEvent(0));
    }
}, "TimeService_Thread");
```

**Problemas:**
- Usa Thread.sleep en bucle (polling) en lugar de scheduling
- Consume recursos innecesarios
- Poca precisión temporal
- Gestión manual de threads

#### 3. **Arquitectura Monolítica**
- **GameSessionManager**: Gestiona todas las sesiones en el mismo hilo
- **GameService**: Procesa lógica de juego síncronamente
- **TimeService**: Un solo thread para temporizadores globales

```
┌─────────────────────────────────────────┐
│        HILO PRINCIPAL (Main)            │
│  ┌───────────────────────────────────┐  │
│  │   GlobalEventBus (Singleton)      │  │
│  │   - publish() es SÍNCRONO         │  │
│  └───────────────────────────────────┘  │
│          ↓           ↓          ↓        │
│  GameSessionMgr  GameService TimeService│
│  (mismo hilo)   (mismo hilo) (1 thread) │
└─────────────────────────────────────────┘
```

---

## 🚀 PLAN DE MODERNIZACIÓN COMPLETO

### Fase 1: Migración a EventBus Asíncrono con Virtual Threads

#### Opción A: **EventBus Asíncrono con Virtual Threads** (RECOMENDADO ✅)

**Por qué Virtual Threads:**
- Java 21 incluye Virtual Threads (Project Loom)
- Extremadamente ligeros (millones de threads sin overhead)
- Ideal para operaciones I/O y eventos
- No requiere dependencias externas

```java
public class AsyncEventBus {
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor;

    public AsyncEventBus() {
        // Virtual Thread per Task Executor (Java 21)
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public CompletableFuture<Void> publish(GameEvent event) {
        List<CompletableFuture<Void>> futures = listeners.stream()
            .map(listener -> CompletableFuture.runAsync(
                () -> listener.onEvent(event),
                executor
            ))
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
```

#### Opción B: **Reactive Streams con Flow API** (Java 21 Built-in)

```java
public class ReactiveEventBus {
    private final SubmissionPublisher<GameEvent> publisher;

    public ReactiveEventBus() {
        this.publisher = new SubmissionPublisher<>(
            Executors.newVirtualThreadPerTaskExecutor(),
            Flow.defaultBufferSize()
        );
    }

    public void addListener(EventListener listener) {
        publisher.subscribe(new Flow.Subscriber<GameEvent>() {
            @Override
            public void onNext(GameEvent event) {
                listener.onEvent(event);
            }
            // ... onSubscribe, onError, onComplete
        });
    }

    public void publish(GameEvent event) {
        publisher.submit(event);  // Non-blocking
    }
}
```

#### Opción C: **Structured Concurrency** (Java 21 Preview)

```java
public class StructuredEventBus {
    public void publish(GameEvent event) throws InterruptedException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (EventListener listener : listeners) {
                scope.fork(() -> {
                    listener.onEvent(event);
                    return null;
                });
            }
            scope.join();           // Wait for all
            scope.throwIfFailed();  // Propagate exceptions
        }
    }
}
```

---

### Fase 2: Modernización del TimeService

#### Solución con ScheduledExecutorService + Virtual Threads

```java
public class ModernTimeService {
    private final ScheduledExecutorService scheduler;
    private final AsyncEventBus eventBus;
    private ScheduledFuture<?> timerTask;
    private final AtomicInteger elapsedSeconds = new AtomicInteger(0);

    public ModernTimeService(AsyncEventBus eventBus) {
        this.eventBus = eventBus;
        // Virtual Thread Scheduler
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().factory()
        );
    }

    public void start() {
        timerTask = scheduler.scheduleAtFixedRate(
            this::tick,
            0,                          // Initial delay
            1,                          // Period
            TimeUnit.SECONDS
        );
    }

    private void tick() {
        int seconds = elapsedSeconds.incrementAndGet();
        eventBus.publish(new TimerTickEvent(seconds));
    }

    public void stop() {
        if (timerTask != null) {
            timerTask.cancel(false);
        }
        scheduler.shutdown();
    }
}
```

**Ventajas:**
✅ No usa Thread.sleep (más eficiente)
✅ Precisión temporal garantizada
✅ No consume CPU innecesariamente
✅ Gestión automática de threads

---

### Fase 3: Arquitectura de Servicios con Threading Específico

#### **GameSessionManager** - Virtual Threads Pool

```java
public class AsyncGameSessionManager implements EventListener {
    private final AsyncEventBus eventBus;
    private final ExecutorService virtualThreadPool;
    private final Map<String, GameService> activeSessions;

    public AsyncGameSessionManager() {
        this.eventBus = GlobalAsyncEventBus.getInstance();
        this.virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor();
        this.activeSessions = new ConcurrentHashMap<>();
        eventBus.addListener(this);
    }

    @Override
    public void onEvent(GameEvent event) {
        // Procesar eventos asíncronamente
        CompletableFuture.runAsync(() -> {
            if (event instanceof GameCreationRequestedEvent) {
                handleGameCreationRequested((GameCreationRequestedEvent) event);
            } else if (event instanceof PlayerJoinedEvent) {
                handlePlayerJoined((PlayerJoinedEvent) event);
            }
            // ... más handlers
        }, virtualThreadPool);
    }

    private void handleGameCreationRequested(GameCreationRequestedEvent event) {
        // Lógica asíncrona - no bloquea
        GameService gameService = new GameService(event.getConfig());
        String sessionId = addSession(gameService);

        if (sessionId != null) {
            eventBus.publish(new GameSessionCreatedEvent(
                event.getTempRoomCode(),
                sessionId,
                gameService
            ));
        }
    }
}
```

#### **GameService** - Structured Concurrency

```java
public class AsyncGameService implements EventListener {
    private final AsyncEventBus globalBus;
    private final AsyncEventBus externalBus;
    private final ExecutorService virtualThreads;
    private GameGlobal globalGameInstance;
    private ModernTimeService timeService;

    public AsyncGameService(GamePlayerConfig config) {
        this.globalGameInstance = new GameGlobal(config);
        this.globalBus = GlobalAsyncEventBus.getInstance();
        this.externalBus = new AsyncEventBus();
        this.virtualThreads = Executors.newVirtualThreadPerTaskExecutor();

        globalBus.addListener(this);
        externalBus.addListener(this);
    }

    @Override
    public void onEvent(GameEvent event) {
        CompletableFuture.runAsync(() -> {
            switch (event) {
                case TimerTickEvent tick -> handleTimerTick(tick);
                case AnswerSubmittedEvent answer -> handleAnswerSubmitted(answer);
                case GameControllerReady ready -> handleControllerReady(ready);
                default -> log.debug("Unhandled event: {}", event.getClass().getSimpleName());
            }
        }, virtualThreads);
    }

    public CompletableFuture<Void> initGameAsync() {
        return CompletableFuture.runAsync(() -> {
            timeService = new ModernTimeService(externalBus);
            timeService.start();

            globalGameInstance.setState(GameGlobal.GameGlobalState.PLAYING);

            loadQuestionsForAllPlayers();
            publishQuestionForAllPlayers(-1, QuestionStatus.INIT);

            log.info("Juego iniciado asíncronamente");
        }, virtualThreads);
    }
}
```

---

## 📐 ARQUITECTURA PROPUESTA

### Modelo de Threading por Servicio

```
┌─────────────────────────────────────────────────────────┐
│              AsyncEventBus (Virtual Threads)            │
│  ExecutorService(newVirtualThreadPerTaskExecutor())    │
│  - publish() → CompletableFuture<Void>                  │
│  - Listeners ejecutados en paralelo                     │
└─────────────────────────────────────────────────────────┘
           ↓                ↓                   ↓
┌──────────────────┐ ┌──────────────┐ ┌────────────────────┐
│ GameSessionMgr   │ │ GameService  │ │  TimeService       │
│ VirtualThreads   │ │ VirtualThreads│ │ ScheduledExecutor  │
│ Pool             │ │ Pool          │ │ (Virtual Thread)   │
└──────────────────┘ └──────────────┘ └────────────────────┘
```

### Flujo de Eventos Asíncrono

```
Usuario → GameController
    ↓
    publish(AnswerSubmittedEvent) [Non-blocking]
    ↓
AsyncEventBus
    ├─→ Virtual Thread 1: GameService.onEvent() [Parallel]
    ├─→ Virtual Thread 2: GameSessionMgr.onEvent() [Parallel]
    └─→ Virtual Thread 3: Logger.onEvent() [Parallel]
        ↓
    Todos ejecutan en paralelo sin bloquearse
```

---

## 🔧 IMPLEMENTACIÓN POR FASES

### **FASE 1.1: Crear AsyncEventBus** (1-2 días)
1. Crear `AsyncEventBus.java` con Virtual Threads
2. Actualizar `GlobalEventBus` a singleton de `AsyncEventBus`
3. Cambiar firma de `publish()` para devolver `CompletableFuture<Void>`

### **FASE 1.2: Actualizar Listeners** (1 día)
1. Modificar `EventListener.onEvent()` para ser no-bloqueante
2. Usar `CompletableFuture.runAsync()` en todos los handlers
3. Gestionar errores con `.exceptionally()`

### **FASE 2.1: Modernizar TimeService** (1 día)
1. Reemplazar `Thread` manual por `ScheduledExecutorService`
2. Usar Virtual Thread factory
3. Eliminar `Thread.sleep()`

### **FASE 2.2: Testing de Concurrencia** (1 día)
1. Crear tests con múltiples eventos simultáneos
2. Validar no hay race conditions en `GameGlobal`
3. Usar `ConcurrentHashMap` donde sea necesario

### **FASE 3.1: Migrar GameService** (2 días)
1. Convertir todos los handlers a asíncronos
2. Usar Structured Concurrency para operaciones relacionadas
3. Implementar backpressure si es necesario

### **FASE 3.2: Migrar GameSessionManager** (1 día)
1. Hacer thread-safe las `activeSessions`
2. Procesamiento asíncrono de creación/eliminación de sesiones

### **FASE 4: Optimizaciones Java 21** (1 día)
1. Pattern Matching for switch (ya usamos)
2. Record Patterns donde aplique
3. Scoped Values para contexto de sesión

---

## 🎯 RECOMENDACIONES FINALES

### ✅ **Usar:**
1. **AsyncEventBus con Virtual Threads** - Ideal para este proyecto
2. **ScheduledExecutorService** - Para TimeService
3. **CompletableFuture** - Para operaciones asíncronas
4. **ConcurrentHashMap** - Para colecciones compartidas
5. **Structured Concurrency** - Para grupos de tareas relacionadas

### ❌ **Evitar:**
1. Thread.sleep en bucles
2. Synchronized excesivo (usar estructuras concurrentes)
3. EventBus síncrono
4. Singletons mutables sin sincronización
5. Thread manual (usar ExecutorService)

### 📚 **Dependencias Adicionales Opcionales:**

```xml
<!-- Solo si quieres Reactive completo -->
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
    <version>3.6.2</version>
</dependency>

<!-- Testing de concurrencia -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.0</version>
    <scope>test</scope>
</dependency>
```

---

## 📊 COMPARATIVA DE OPCIONES

| Opción | Complejidad | Performance | Escalabilidad | Requiere Deps |
|--------|-------------|-------------|---------------|---------------|
| **Virtual Threads + CompletableFuture** | ⭐⭐ Baja | ⭐⭐⭐⭐⭐ Excelente | ⭐⭐⭐⭐⭐ Excelente | ❌ No |
| Flow API (Reactive) | ⭐⭐⭐ Media | ⭐⭐⭐⭐ Muy Buena | ⭐⭐⭐⭐⭐ Excelente | ❌ No |
| Project Reactor | ⭐⭐⭐⭐ Alta | ⭐⭐⭐⭐⭐ Excelente | ⭐⭐⭐⭐⭐ Excelente | ✅ Sí |
| Structured Concurrency | ⭐⭐⭐ Media | ⭐⭐⭐⭐ Muy Buena | ⭐⭐⭐⭐ Muy Buena | ❌ No (Preview) |

---

## 🎓 CONCLUSIÓN

**Mi recomendación: Opción A - Virtual Threads + CompletableFuture**

**Razones:**
1. ✅ Java 21 built-in (no deps externas)
2. ✅ Fácil de implementar y entender
3. ✅ Performance excelente para tu caso de uso
4. ✅ Escalable a millones de eventos concurrentes
5. ✅ Mantenible y debuggable

**Timeline estimado:** 7-10 días de desarrollo + 2-3 días de testing

¿Quieres que comience implementando alguna fase específica?
