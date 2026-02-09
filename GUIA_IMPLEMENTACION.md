# Guía de Implementación Paso a Paso - Modernización Apalabrazos

## 🎯 OBJETIVO

Migrar de una arquitectura síncrona bloqueante a una arquitectura asíncrona con Virtual Threads de Java 21.

---

## 📋 CHECKLIST DE PRE-REQUISITOS

- [x] Java 21 instalado
- [ ] Entender conceptos de CompletableFuture
- [ ] Entender Virtual Threads
- [ ] Conocer ConcurrentHashMap
- [ ] Tests unitarios existentes (para regresión)

---

## 🚀 FASE 1: CREAR ASYNCEVENTBUS (Días 1-2)

### Paso 1.1: Crear AsyncEventBus.java

```bash
# Crear archivo
touch src/main/java/UE_Proyecto_Ingenieria/Apalabrazos/backend/events/AsyncEventBus.java
```

Copiar contenido de `ejemplos-modernizacion/AsyncEventBus.java`

### Paso 1.2: Crear GlobalAsyncEventBus.java

```bash
touch src/main/java/UE_Proyecto_Ingenieria/Apalabrazos/backend/events/GlobalAsyncEventBus.java
```

### Paso 1.3: Tests Unitarios

```java
package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

public class AsyncEventBusTest {

    @Test
    public void testPublishAndForget() {
        AsyncEventBus bus = new AsyncEventBus();
        AtomicInteger counter = new AtomicInteger(0);

        bus.addListener(event -> counter.incrementAndGet());

        bus.publishAndForget(new TestEvent());

        // Dar tiempo a procesar
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            fail();
        }

        assertEquals(1, counter.get());
    }

    @Test
    public void testPublishAndWait() {
        AsyncEventBus bus = new AsyncEventBus();
        AtomicInteger counter = new AtomicInteger(0);

        bus.addListener(event -> {
            try {
                Thread.sleep(100); // Simular trabajo
                counter.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        bus.publishAndWait(new TestEvent());

        // Aquí garantizamos que el listener terminó
        assertEquals(1, counter.get());
    }

    @Test
    public void testConcurrentPublish() {
        AsyncEventBus bus = new AsyncEventBus();
        AtomicInteger counter = new AtomicInteger(0);

        bus.addListener(event -> counter.incrementAndGet());

        // Publicar 100 eventos en paralelo
        CompletableFuture<?>[] futures = new CompletableFuture[100];
        for (int i = 0; i < 100; i++) {
            futures[i] = bus.publish(new TestEvent());
        }

        CompletableFuture.allOf(futures).join();

        assertEquals(100, counter.get());
    }

    @Test
    public void testExceptionHandling() {
        AsyncEventBus bus = new AsyncEventBus();

        // Listener que lanza excepción
        bus.addListener(event -> {
            throw new RuntimeException("Test exception");
        });

        // No debería lanzar excepción al publicar
        assertDoesNotThrow(() -> {
            bus.publishAndWait(new TestEvent());
        });
    }

    // Evento de prueba
    private static class TestEvent extends GameEvent {
    }
}
```

---

## 🚀 FASE 2: MODERNIZAR TIMESERVICE (Días 3-4)

### Paso 2.1: Crear ModernTimeService.java

```bash
touch src/main/java/UE_Proyecto_Ingenieria/Apalabrazos/backend/service/ModernTimeService.java
```

Copiar contenido de `ejemplos-modernizacion/ModernTimeService.java`

### Paso 2.2: Tests de ModernTimeService

```java
package UE_Proyecto_Ingenieria.Apalabrazos.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class ModernTimeServiceTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testTimerTicks() throws InterruptedException {
        AsyncEventBus bus = new AsyncEventBus();
        ModernTimeService timeService = new ModernTimeService(bus);

        AtomicInteger tickCount = new AtomicInteger(0);

        bus.addListener(event -> {
            if (event instanceof TimerTickEvent) {
                tickCount.incrementAndGet();
            }
        });

        timeService.start();

        // Esperar 3 segundos
        Thread.sleep(3200);

        timeService.stop();

        // Deberían ser al menos 3 ticks (puede ser 4 por timing)
        assertTrue(tickCount.get() >= 3 && tickCount.get() <= 4);
    }

    @Test
    public void testStartStop() {
        AsyncEventBus bus = new AsyncEventBus();
        ModernTimeService timeService = new ModernTimeService(bus);

        assertFalse(timeService.isRunning());

        timeService.start();
        assertTrue(timeService.isRunning());

        timeService.stop();
        assertFalse(timeService.isRunning());
    }

    @Test
    public void testReset() throws InterruptedException {
        AsyncEventBus bus = new AsyncEventBus();
        ModernTimeService timeService = new ModernTimeService(bus);

        timeService.start();
        Thread.sleep(2200);

        assertTrue(timeService.getElapsedSeconds() >= 2);

        timeService.reset();
        assertEquals(0, timeService.getElapsedSeconds());

        timeService.shutdown();
    }
}
```

### Paso 2.3: Migración Incremental - Mantener ambos TimeService

**Estrategia:** Crear `ModernTimeService` sin borrar `TimeService` original.

```java
// En GameService.java - agregar soporte dual temporal
public class GameService implements EventListener {

    // Mantener ambos temporalmente
    private TimeService oldTimeService;           // Original
    private ModernTimeService modernTimeService;  // Nuevo

    // Flag para cambiar entre implementaciones
    private static final boolean USE_MODERN = true;

    public void initGame() {
        if (USE_MODERN) {
            if (this.modernTimeService == null) {
                this.modernTimeService = new ModernTimeService(
                    new AsyncEventBus() // Temporal: bus local
                );
            }
            this.modernTimeService.start();
        } else {
            if (this.oldTimeService == null) {
                this.oldTimeService = new TimeService();
            }
            this.oldTimeService.start();
        }

        // ... resto del código
    }
}
```

---

## 🚀 FASE 3: MIGRAR GAMESERVICE (Días 5-7)

### Paso 3.1: Crear AsyncGameService (sin borrar GameService)

```bash
touch src/main/java/UE_Proyecto_Ingenieria/Apalabrazos/backend/service/AsyncGameService.java
```

### Paso 3.2: Patrón de Migración - Estrategia Incremental

```java
// 1. Nuevo archivo: AsyncGameService.java
// 2. Copiar TODO el código de GameService.java
// 3. Renombrar clase: GameService → AsyncGameService
// 4. Cambiar EventBus → AsyncEventBus
// 5. Modificar handlers uno por uno

// Ejemplo de migración de un handler:

// ANTES en GameService
private void handleAnswerSubmitted(AnswerSubmittedEvent event) {
    String playerId = event.getPlayerId();
    // ... lógica síncrona
    publishQuestionForPlayer(playerId, questionIndex, newStatus, nextQuestion);
}

// DESPUÉS en AsyncGameService
private void handleAnswerSubmittedAsync(AnswerSubmittedEvent event) {
    CompletableFuture.runAsync(() -> {
        String playerId = event.getPlayerId();
        // ... misma lógica
        publishQuestionForPlayer(playerId, questionIndex, newStatus, nextQuestion);
    }, virtualThreads)
    .exceptionally(ex -> {
        log.error("Error procesando respuesta: {}", ex.getMessage(), ex);
        return null;
    });
}
```

### Paso 3.3: Actualizar GameGlobal para Thread-Safety

```java
// En GameGlobal.java

// CAMBIO 1: ConcurrentHashMap
private final ConcurrentHashMap<String, GameInstance> playerInstances =
    new ConcurrentHashMap<>();

// CAMBIO 2: AtomicInteger para remainingSeconds
private final AtomicInteger remainingSeconds;

public GameGlobal() {
    // ...
    this.remainingSeconds = new AtomicInteger(300);
}

public void decrementTime() {
    remainingSeconds.decrementAndGet();
}

public int getRemainingSeconds() {
    return remainingSeconds.get();
}

public boolean isTimeUp() {
    return remainingSeconds.get() <= 0;
}
```

---

## 🚀 FASE 4: MIGRAR GAMESESSIONMANAGER (Días 8-9)

### Paso 4.1: Actualizar GameSessionManager

```java
// En GameSessionManager.java - cambios mínimos

public class GameSessionManager implements EventListener {

    private final AsyncEventBus eventBus;  // Cambio: EventBus → AsyncEventBus
    private final ExecutorService virtualThreads;  // Nuevo

    // IMPORTANTE: ConcurrentHashMap para thread-safety
    private final Map<String, GameService> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionCreators = new ConcurrentHashMap<>();

    public GameSessionManager() {
        this.eventBus = GlobalAsyncEventBus.getInstance();  // Cambio
        this.virtualThreads = Executors.newVirtualThreadPerTaskExecutor();  // Nuevo
        eventBus.addListener(this);
    }

    @Override
    public void onEvent(GameEvent event) {
        // Procesar asíncronamente
        CompletableFuture.runAsync(() -> {
            handleEventSync(event);  // Delegar a método síncrono interno
        }, virtualThreads)
        .exceptionally(ex -> {
            log.error("Error procesando evento: {}", ex.getMessage(), ex);
            return null;
        });
    }

    // Lógica original se mueve aquí (sin cambios)
    private void handleEventSync(GameEvent event) {
        if (event instanceof GameCreationRequestedEvent) {
            handleGameCreationRequested((GameCreationRequestedEvent) event);
        } else if (event instanceof GetGameSessionInfoEvent) {
            handleGetGameSessionInfo((GetGameSessionInfoEvent) event);
        } else if (event instanceof PlayerJoinedEvent) {
            handlePlayerJoined((PlayerJoinedEvent) event);
        } else if (event instanceof GameStartedRequestEvent) {
            handleGameStartedRequest((GameStartedRequestEvent) event);
        }
    }

    // Los métodos handleXXX no necesitan cambios si solo modifican estructuras thread-safe
    // ...
}
```

---

## 🚀 FASE 5: INTEGRACIÓN Y TESTING (Días 10-12)

### Paso 5.1: Test de Integración End-to-End

```java
package UE_Proyecto_Ingenieria.Apalabrazos.backend;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AsyncIntegrationTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testCompleteGameFlow() {
        // 1. Inicializar servicios
        GameSessionManager sessionManager = new GameSessionManager();

        // 2. Crear configuración de juego
        Player player = new Player("TestPlayer", "test123");
        GamePlayerConfig config = new GamePlayerConfig();
        config.setPlayer(player);
        config.setTimerSeconds(60);
        config.setQuestionNumber(5);
        config.setMaxPlayers(2);

        // 3. Crear sesión de juego
        AsyncEventBus globalBus = GlobalAsyncEventBus.getInstance();

        AtomicReference<String> sessionId = new AtomicReference<>();

        globalBus.addListener(event -> {
            if (event instanceof GameSessionCreatedEvent created) {
                sessionId.set(created.getSessionId());
            }
        });

        // Publicar evento de creación
        globalBus.publishAndWait(new GameCreationRequestedEvent(config, "TEMP123"));

        assertNotNull(sessionId.get(), "Sesión creada");

        // 4. Unir jugador
        globalBus.publishAndWait(new PlayerJoinedEvent(player.getPlayerID(), sessionId.get()));

        // 5. Validar inicio de juego
        GameService service = sessionManager.getSessionById(sessionId.get());
        assertNotNull(service);

        service.GameStartedValid();

        // 6. Esperar a que el juego inicie
        Thread.sleep(2000);

        assertEquals(GameGlobal.GameGlobalState.PLAYING,
                     service.getGameInstance().getState());

        // 7. Cleanup
        service.shutdown();
        sessionManager.clearAllSessions();
    }
}
```

### Paso 5.2: Load Testing

```java
@Test
@Timeout(value = 30, unit = TimeUnit.SECONDS)
public void testHighConcurrency() {
    AsyncEventBus bus = GlobalAsyncEventBus.getInstance();
    AtomicInteger processedEvents = new AtomicInteger(0);

    bus.addListener(event -> {
        processedEvents.incrementAndGet();
    });

    // Publicar 10,000 eventos en paralelo
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < 10000; i++) {
        futures.add(bus.publish(new TimerTickEvent(i)));
    }

    // Esperar a que todos completen
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    assertEquals(10000, processedEvents.get());
}
```

---

## 📊 CHECKLIST DE MIGRACIÓN

### EventBus
- [ ] `AsyncEventBus.java` creado
- [ ] `GlobalAsyncEventBus.java` creado
- [ ] Tests unitarios pasando
- [ ] Tests de concurrencia pasando

### TimeService
- [ ] `ModernTimeService.java` creado
- [ ] Tests de precisión temporal pasando
- [ ] Integrado con `AsyncEventBus`
- [ ] `TimeService` original marcado como `@Deprecated`

### GameService
- [ ] `AsyncGameService.java` creado
- [ ] Todos los handlers migrados a async
- [ ] `CompletableFuture` usado correctamente
- [ ] Error handling implementado
- [ ] Tests de regresión pasando

### GameSessionManager
- [ ] Migrado a `AsyncEventBus`
- [ ] `ConcurrentHashMap` implementado
- [ ] Procesamiento asíncrono de eventos
- [ ] Tests de concurrencia pasando

### GameGlobal
- [ ] `ConcurrentHashMap` para `playerInstances`
- [ ] `AtomicInteger` para contadores
- [ ] Thread-safety verificado
- [ ] Tests de race conditions

### Integración
- [ ] Tests end-to-end pasando
- [ ] Load tests pasando
- [ ] Sin deadlocks
- [ ] Sin race conditions
- [ ] Performance mejorado (benchmarks)

---

## 🎯 COMANDOS ÚTILES

### Compilar proyecto
```bash
cd /home/alosadad/Apalabrazos
mvn clean compile
```

### Ejecutar tests
```bash
mvn test
```

### Ejecutar tests específicos
```bash
mvn test -Dtest=AsyncEventBusTest
```

### Ejecutar con Java 21
```bash
java --version  # Verificar Java 21
mvn clean package
java -jar target/Apalabrazos-0.0.1-SNAPSHOT.jar
```

### Habilitar preview features (si usas Structured Concurrency)
```xml
<!-- En pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <release>21</release>
        <compilerArgs>
            <arg>--enable-preview</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

---

## 🐛 TROUBLESHOOTING

### Problema: "CompletableFuture nunca completa"

```java
// MAL: Olvidar retornar en exceptionally
future.exceptionally(ex -> {
    log.error("Error: {}", ex.getMessage());
    // ❌ Falta return null;
});

// BIEN:
future.exceptionally(ex -> {
    log.error("Error: {}", ex.getMessage());
    return null;  // ✅ Siempre retornar
});
```

### Problema: "ConcurrentModificationException"

```java
// MAL: HashMap no thread-safe
private Map<String, GameService> sessions = new HashMap<>();

// BIEN: ConcurrentHashMap
private final Map<String, GameService> sessions = new ConcurrentHashMap<>();
```

### Problema: "Race condition en contadores"

```java
// MAL: int no es thread-safe
private int counter = 0;
public void increment() {
    counter++;  // ❌ Race condition
}

// BIEN: AtomicInteger
private final AtomicInteger counter = new AtomicInteger(0);
public void increment() {
    counter.incrementAndGet();  // ✅ Thread-safe
}
```

---

## 📈 MÉTRICAS DE ÉXITO

Al finalizar la migración, deberías ver:

✅ **Performance:**
- Latencia P50 < 5ms (antes: ~200ms)
- Throughput > 1000 eventos/s (antes: ~10 eventos/s)

✅ **Recursos:**
- CPU usage distribuido en todos los cores
- Memory estable bajo carga

✅ **Calidad:**
- 0 errores de concurrencia en tests
- 0 deadlocks en load tests
- Cobertura de tests > 80%

---

## 🎓 PRÓXIMOS PASOS OPCIONALES

Una vez completada la migración básica, puedes explorar:

1. **Reactive Streams (Flow API)**
   - Backpressure para controlar flujo de eventos
   - Buffering avanzado

2. **Structured Concurrency** (Preview en Java 21)
   - Mejor gestión de tareas relacionadas
   - Error propagation automático

3. **Scoped Values** (Preview en Java 21)
   - Contexto thread-local mejorado
   - Alternativa moderna a ThreadLocal

4. **Observabilidad**
   - Micrometer para métricas
   - OpenTelemetry para tracing
   - Visualizar flujo asíncrono

---

¿Quieres que comience implementando alguna fase específica del código?
