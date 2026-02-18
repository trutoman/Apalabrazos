package UE_Proyecto_Ingenieria.Apalabrazos.backend.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para AsyncEventBus.
 *
 * Verifica:
 * 1. Publicación no-bloqueante (fire-and-forget)
 * 2. Publicación blocking (publishAndWait)
 * 3. Listeners ejecutan en paralelo (no secuencial)
 * 4. Manejo de excepciones en listeners
 * 5. Listeners duplicados no se registran
 */
public class AsyncEventBusTest {

    private static final Logger log = LoggerFactory.getLogger(AsyncEventBusTest.class);

    private AsyncEventBus eventBus;
    private List<GameEvent> eventsReceived;
    private AtomicInteger listenerCallCount;

    @BeforeEach
    void setup() {
        eventBus = new AsyncEventBus();
        eventsReceived = new ArrayList<>();
        listenerCallCount = new AtomicInteger(0);
    }

    /**
     * TEST 1: Publicación no-bloqueante
     *
     * Verifica que publish() retorna inmediatamente sin esperar a los listeners
     */
    @Test
    void testPublishNonBlocking() {
        // Listener que duerme 1 segundo
        eventBus.addListener(event -> {
            try {
                Thread.sleep(1000);
                listenerCallCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Medir tiempo de publicación
        long startTime = System.currentTimeMillis();

        CompletableFuture<Void> future = eventBus.publish(new TestGameEvent());

        long publishTime = System.currentTimeMillis() - startTime;

        // publish() debe retornar en < 100ms (no esperó el listener)
        assertTrue(publishTime < 100,
                  "publish() tardó " + publishTime + "ms, debería ser < 100ms");

        // Pero el listener debe haberse ejecutado eventualmente
        future.join(); // Esperar a que termine
        assertEquals(1, listenerCallCount.get());
    }

    /**
     * TEST 2: Publicación blocking (publishAndWait)
     *
     * Verifica que publishAndWait() espera a que todos los listeners terminen
     */
    @Test
    void testPublishAndWait() {
        // Listener que duerme 500ms
        eventBus.addListener(event -> {
            try {
                Thread.sleep(500);
                eventsReceived.add(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long startTime = System.currentTimeMillis();

        eventBus.publishAndWait(new TestGameEvent());

        long totalTime = System.currentTimeMillis() - startTime;

        // publishAndWait() debe tardar >= 500ms (esperó al listener)
        assertTrue(totalTime >= 500,
                  "publishAndWait() tardó " + totalTime + "ms, debería ser >= 500ms");

        // Y el evento debe haber sido procesado
        assertEquals(1, eventsReceived.size());
    }

    /**
     * TEST 3: Listeners en paralelo
     *
     * Verifica que múltiples listeners se ejecutan en paralelo, no secuencial.
     * Si fuera secuencial: 3 listeners * 100ms = 300ms total
     * Si es paralelo: 100ms total (todos al mismo tiempo)
     */
    @Test
    void testParallelListenerExecution() {
        int numListeners = 3;

        // Crear 3 listeners que duermen 100ms cada uno
        for (int i = 0; i < numListeners; i++) {
            eventBus.addListener(event -> {
                try {
                    Thread.sleep(100);
                    listenerCallCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        long startTime = System.currentTimeMillis();

        eventBus.publishAndWait(new TestGameEvent());

        long totalTime = System.currentTimeMillis() - startTime;

        // Si ejecutara secuencial: ~300ms (3 * 100ms)
        // Si ejecuta paralelo: ~100ms (todos al mismo tiempo)
        // Tolerancia: < 200ms indica paralelismo
        assertTrue(totalTime < 200,
                  "Listeners ejecutaron secuencial (" + totalTime + "ms), " +
                  "deberían ser paralelo (< 200ms)");

        assertEquals(numListeners, listenerCallCount.get());
    }

    /**
     * TEST 4: Manejo de excepciones
     *
     * Verifica que una excepción en un listener no afecta a otros listeners
     */
    @Test
    void testExceptionHandling() {
        // Listener 1: lanza excepción
        eventBus.addListener(event -> {
            throw new RuntimeException("Error intencional");
        });

        // Listener 2: procesa normalmente
        eventBus.addListener(event -> {
            eventsReceived.add(event);
        });

        // No debe lanzar excepción, solo loguear
        assertDoesNotThrow(() -> {
            eventBus.publishAndWait(new TestGameEvent());
        });

        // El segundo listener debe seguir habiendo procesado el evento
        assertEquals(1, eventsReceived.size());
    }

    /**
     * TEST 5: Listeners duplicados
     *
     * Verifica que agregar el mismo listener dos veces solo lo registra una vez
     */
    @Test
    void testDuplicateListeners() {
        EventListener listener = event -> {
            listenerCallCount.incrementAndGet();
        };

        // Intentar agregar dos veces
        eventBus.addListener(listener);
        eventBus.addListener(listener);

        eventBus.publishAndWait(new TestGameEvent());

        // Debe ejecutarse una sola vez, no dos
        assertEquals(1, listenerCallCount.get(),
                    "Listener se ejecutó " + listenerCallCount.get() + " veces, " +
                    "debería ser 1 (sin duplicados)");
    }

    /**
     * Evento de prueba simple
     */
    private static class TestGameEvent extends GameEvent {
        public TestGameEvent() {
            super();
        }
    }
}
