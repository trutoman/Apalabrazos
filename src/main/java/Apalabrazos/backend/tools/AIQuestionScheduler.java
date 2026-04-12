package Apalabrazos.backend.tools;

import Apalabrazos.backend.model.QuestionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler que genera baterías de preguntas con IA de forma periódica.
 *
 * Por defecto, ejecuta la generación todos los días a las 08:00 (hora de Madrid/España).
 *
 * Variables de entorno:
 * - AI_GENERATOR_ENABLED    → "true" para activar (default: "false")
 * - AI_GENERATOR_HOUR       → Hora del día para ejecutar 0-23 (default: 8)
 * - AI_GENERATOR_MINUTE     → Minuto de la hora 0-59 (default: 0)
 * - AI_GENERATOR_TIMEZONE   → Zona horaria (default: Europe/Madrid)
 * - AI_GENERATOR_OUTPUT_DIR → Directorio donde guardar el JSON generado (default: src/main/resources/Apalabrazos/data)
 * - AI_GENERATOR_FILENAME   → Nombre del archivo de salida (default: questions2.json)
 * - AI_GENERATOR_RUN_ON_START → "true" para ejecutar una vez al arrancar (default: "false")
 */
public class AIQuestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionScheduler.class);

    private final AIQuestionGenerator generator;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private volatile boolean running = false;

    // Config
    private final boolean enabled;
    private final int hour;
    private final int minute;
    private final ZoneId zone;
    private final String outputDir;
    private final String filename;
    private final boolean runOnStart;

    public AIQuestionScheduler() {
        this.generator = new AIQuestionGenerator();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ai-question-scheduler");
            t.setDaemon(true);
            return t;
        });

        this.enabled = readEnvBool("AI_GENERATOR_ENABLED", false);
        this.hour = readEnvInt("AI_GENERATOR_HOUR", 8);
        this.minute = readEnvInt("AI_GENERATOR_MINUTE", 0);
        this.zone = ZoneId.of(readEnv("AI_GENERATOR_TIMEZONE", "Europe/Madrid"));
        this.outputDir = readEnv("AI_GENERATOR_OUTPUT_DIR", "src/main/resources/Apalabrazos/data");
        this.filename = readEnv("AI_GENERATOR_FILENAME", "questions2.json");
        this.runOnStart = readEnvBool("AI_GENERATOR_RUN_ON_START", false);
    }

    /**
     * Inicia el scheduler. Si no está habilitado, no hace nada.
     */
    public void start() {
        if (!enabled) {
            log.info("AI Question Scheduler DESHABILITADO (AI_GENERATOR_ENABLED != true)");
            return;
        }

        log.info("AI Question Scheduler INICIADO - Generación diaria a las {}:{} ({})", hour, minute, zone);

        // Ejecutar inmediatamente si está configurado
        if (runOnStart) {
            log.info("Ejecución inicial al arrancar habilitada (AI_GENERATOR_RUN_ON_START=true)");
            scheduler.submit(this::generateAndSave);
        }

        // Calcular el delay hasta la próxima ejecución
        scheduleNextRun();
    }

    /**
     * Programa la siguiente ejecución.
     */
    private void scheduleNextRun() {
        long delayMinutes = calculateDelayMinutes();
        log.info("Próxima generación programada en {} minutos (a las {}:{} {})",
                delayMinutes, hour, minute, zone);

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::generateAndSave,
                delayMinutes,
                TimeUnit.DAYS.toMinutes(1),
                TimeUnit.MINUTES
        );
    }

    /**
     * Calcula los minutos de delay hasta la próxima hora programada.
     */
    private long calculateDelayMinutes() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            // Ya pasó la hora hoy, programar para mañana
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun).toMinutes();
    }

    /**
     * Genera las preguntas y las guarda en el archivo.
     */
    private void generateAndSave() {
        if (running) {
            log.warn("Generación ya en curso, saltando esta ejecución");
            return;
        }

        running = true;
        String dateTag = LocalDate.now(zone).format(DateTimeFormatter.ISO_LOCAL_DATE);

        try {
            log.info("=== Iniciando generación de preguntas con IA [{}] ===", dateTag);

            QuestionList questions = generator.generateFullBattery();
            int count = questions.getCurrentLength();

            if (count == 0) {
                log.error("La IA no generó ninguna pregunta válida. No se sobrescribe el archivo actual.");
                return;
            }

            // Guardar en archivo principal
            String outputPath = outputDir + "/" + filename;
            generator.saveToFile(questions, outputPath);
            log.info("✓ {} preguntas generadas y guardadas en {}", count, outputPath);

            // Guardar backup con fecha
            String backupPath = outputDir + "/questions_backup_" + dateTag + ".json";
            generator.saveToFile(questions, backupPath);
            log.info("✓ Backup guardado en {}", backupPath);

        } catch (Exception e) {
            log.error("Error generando preguntas con IA [{}]: {}", dateTag, e.getMessage(), e);
        } finally {
            running = false;
        }
    }

    /**
     * Detiene el scheduler de forma limpia.
     */
    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("AI Question Scheduler detenido");
    }

    /**
     * Fuerza una generación manual (útil para testing o endpoint admin).
     */
    public void forceGenerate() {
        log.info("Generación forzada solicitada manualmente");
        scheduler.submit(this::generateAndSave);
    }

    private static String readEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static int readEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean readEnvBool(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }
}