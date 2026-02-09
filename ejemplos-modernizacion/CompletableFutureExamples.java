package UE_Proyecto_Ingenieria.Apalabrazos.backend.examples;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.events.*;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.service.AsyncGameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Ejemplos de cómo usar CompletableFuture con el sistema asíncrono.
 *
 * CompletableFuture es la API de Java para programación asíncrona y reactiva.
 * Permite encadenar operaciones, manejar errores, combinar futures, etc.
 */
public class CompletableFutureExamples {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureExamples.class);

    // ============================================================
    // EJEMPLO 1: Fire-and-Forget (Publicar y olvidar)
    // ============================================================

    public static void ejemplo1_FireAndForget() {
        AsyncEventBus bus = new AsyncEventBus();

        // Publicar evento sin esperar resultado
        bus.publishAndForget(new TimerTickEvent(10));

        // El código continúa inmediatamente, no espera a que los listeners terminen
        log.info("Evento publicado, continuando...");
    }

    // ============================================================
    // EJEMPLO 2: Esperar a que evento se procese
    // ============================================================

    public static void ejemplo2_PublishAndWait() {
        AsyncEventBus bus = new AsyncEventBus();

        // Publicar y esperar bloqueando hasta que TODOS los listeners terminen
        bus.publishAndWait(new TimerTickEvent(10));

        // Aquí garantizamos que todos los listeners procesaron el evento
        log.info("Todos los listeners terminaron");
    }

    // ============================================================
    // EJEMPLO 3: Publicar y hacer algo cuando termine
    // ============================================================

    public static void ejemplo3_PublishThenCallback() {
        AsyncEventBus bus = new AsyncEventBus();

        CompletableFuture<Void> future = bus.publish(new TimerTickEvent(10));

        // thenRun: ejecutar código cuando el Future complete
        future.thenRun(() -> {
            log.info("Todos los listeners procesaron el evento");
        });

        // El código continúa sin bloquear
        log.info("Continuando mientras los listeners procesan...");
    }

    // ============================================================
    // EJEMPLO 4: Manejar errores
    // ============================================================

    public static void ejemplo4_ErrorHandling() {
        AsyncEventBus bus = new AsyncEventBus();

        bus.publish(new TimerTickEvent(10))
            .exceptionally(ex -> {
                log.error("Error procesando evento: {}", ex.getMessage());
                return null; // Manejar el error y retornar valor por defecto
            })
            .thenRun(() -> {
                log.info("Procesamiento completado (con o sin errores)");
            });
    }

    // ============================================================
    // EJEMPLO 5: Encadenar operaciones asíncronas
    // ============================================================

    public static void ejemplo5_Chaining(AsyncGameService gameService) {
        // Iniciar juego → esperar 5 segundos → finalizar juego
        gameService.initGameAsync()
            .thenRun(() -> {
                log.info("Juego iniciado, esperando 5 segundos...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            })
            .thenRun(() -> {
                log.info("Finalizando juego...");
                // gameService.finishGameAsync();
            })
            .exceptionally(ex -> {
                log.error("Error en secuencia: {}", ex.getMessage());
                return null;
            });
    }

    // ============================================================
    // EJEMPLO 6: Combinar múltiples operaciones
    // ============================================================

    public static void ejemplo6_CombiningFutures() {
        AsyncEventBus bus = new AsyncEventBus();

        // Publicar 3 eventos y esperar a que TODOS terminen
        CompletableFuture<Void> future1 = bus.publish(new TimerTickEvent(1));
        CompletableFuture<Void> future2 = bus.publish(new TimerTickEvent(2));
        CompletableFuture<Void> future3 = bus.publish(new TimerTickEvent(3));

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(future1, future2, future3);

        allFutures.thenRun(() -> {
            log.info("Los 3 eventos fueron procesados por todos los listeners");
        });
    }

    // ============================================================
    // EJEMPLO 7: Race condition - el primero que termine
    // ============================================================

    public static void ejemplo7_RaceCondition() {
        AsyncEventBus bus = new AsyncEventBus();

        CompletableFuture<Void> future1 = bus.publish(new TimerTickEvent(1));
        CompletableFuture<Void> future2 = bus.publish(new TimerTickEvent(2));

        // anyOf: completa cuando EL PRIMERO de los futures completa
        CompletableFuture<Object> firstCompleted = CompletableFuture.anyOf(future1, future2);

        firstCompleted.thenRun(() -> {
            log.info("Al menos uno de los eventos fue procesado");
        });
    }

    // ============================================================
    // EJEMPLO 8: Timeout - cancelar si tarda mucho
    // ============================================================

    public static void ejemplo8_Timeout(AsyncGameService gameService) {
        gameService.initGameAsync()
            .orTimeout(10, TimeUnit.SECONDS)  // Cancelar si tarda más de 10 segundos
            .exceptionally(ex -> {
                if (ex instanceof java.util.concurrent.TimeoutException) {
                    log.error("Inicialización de juego tardó más de 10 segundos");
                }
                return null;
            });
    }

    // ============================================================
    // EJEMPLO 9: Ejecutar en paralelo y combinar resultados
    // ============================================================

    public static void ejemplo9_ParallelExecution() {
        AsyncEventBus bus = new AsyncEventBus();

        // Crear CompletableFutures que retornan valores
        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> {
            // Simular procesamiento pesado
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 10;
        });

        CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 20;
        });

        // Combinar resultados cuando ambos completen
        future1.thenCombine(future2, (result1, result2) -> result1 + result2)
            .thenAccept(sum -> {
                log.info("Suma de resultados: {}", sum); // Output: 30
            });
    }

    // ============================================================
    // EJEMPLO 10: Pattern real - Iniciar juego multijugador
    // ============================================================

    public static CompletableFuture<Void> ejemplo10_RealWorldPattern(
            AsyncGameService gameService,
            String player1,
            String player2) {

        return CompletableFuture.runAsync(() -> {
            log.info("Agregando jugadores...");
            gameService.addPlayerToGame(player1);
            gameService.addPlayerToGame(player2);
        })
        .thenCompose(v -> {
            log.info("Jugadores agregados, validando inicio...");
            gameService.GameStartedValid();
            return CompletableFuture.completedFuture(null);
        })
        .thenCompose(v -> {
            log.info("Inicializando juego...");
            return gameService.initGameAsync();
        })
        .thenRun(() -> {
            log.info("Juego multijugador iniciado exitosamente");
        })
        .exceptionally(ex -> {
            log.error("Error iniciando juego multijugador: {}", ex.getMessage());
            return null;
        });
    }
}

/* =====================================================================
 * RESUMEN DE OPERACIONES DE CompletableFuture:
 * =====================================================================
 *
 * CREACIÓN:
 * - CompletableFuture.runAsync(Runnable)          → ejecuta sin retornar valor
 * - CompletableFuture.supplyAsync(Supplier<T>)    → ejecuta y retorna valor
 *
 * TRANSFORMACIÓN:
 * - thenApply(Function<T,U>)      → transforma resultado
 * - thenAccept(Consumer<T>)       → consume resultado
 * - thenRun(Runnable)             → ejecuta sin usar resultado
 *
 * COMPOSICIÓN:
 * - thenCompose(Function)         → encadenar futures
 * - thenCombine(CompletableFuture, BiFunction) → combinar 2 futures
 *
 * COMBINACIÓN:
 * - allOf(CompletableFuture...)   → esperar TODOS
 * - anyOf(CompletableFuture...)   → esperar CUALQUIERA
 *
 * MANEJO DE ERRORES:
 * - exceptionally(Function)       → manejar excepción
 * - handle(BiFunction)            → manejar resultado o error
 * - whenComplete(BiConsumer)      → callback final
 *
 * CONTROL:
 * - orTimeout(long, TimeUnit)     → timeout
 * - completeOnTimeout(T, long, TimeUnit) → valor por defecto si timeout
 * - join()                        → esperar bloqueando (sin throws)
 * - get()                         → esperar bloqueando (throws Exception)
 *
 * =====================================================================
 */
