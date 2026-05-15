package Apalabrazos.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralised configuration for the AI question generation subsystem.
 * All values are read from environment variables at startup; sensible defaults
 * are provided so the app can run out-of-the-box in development.
 *
 * Environment variables:
 *   AI_API_KEY                              — API key for the LLM endpoint (optional)
 *   AI_API_URL                              — Endpoint URL (Ollama / Anthropic-compatible)
 *   AI_MODEL                                — Primary model name
 *   AI_FALLBACK_MODEL                       — Fallback model used on 503 errors (optional)
 *   AI_WORD_DICTIONARY_PATH                 — Path to the word dictionary file
 *   AI_QUESTIONS_PER_LETTER                 — Questions kept per letter after generation
 *   AI_QUESTIONS_TO_GENERATE_PER_LETTER_IN_BATCH — Questions generated per letter per batch attempt
 *   AI_LETTERS_PER_BATCH                    — Letters grouped in a single LLM call
 *   AI_MAX_ATTEMPTS_PER_BATCH               — Maximum retry attempts per letter batch
 *   AI_MAX_TOKENS                           — Max tokens requested from the model
 *   AI_APP_NAME                             — Application name injected into prompts
 *   AI_APP_URL                              — Application URL injected into prompts
 */
public final class AIQuestionConfig {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionConfig.class);

    // ── Defaults ──────────────────────────────────────────────────────────────

    private static final String DEFAULT_API_URL    = "http://100.93.139.92:11434/api/chat";
    private static final String DEFAULT_MODEL      = "gemma4:e2b";
    private static final String DEFAULT_FALLBACK_MODEL = "";
    private static final String DEFAULT_WORD_DICTIONARY_PATH = "Apalabrazos/data/dictionary.txt";

    private static final int  DEFAULT_QUESTIONS_PER_LETTER                     = 1;
    private static final int  DEFAULT_QUESTIONS_TO_GENERATE_PER_LETTER_IN_BATCH = 2;
    private static final int  DEFAULT_LETTERS_PER_BATCH                        = 25;
    private static final int  DEFAULT_MAX_ATTEMPTS_PER_BATCH                   = 1;
    private static final int  DEFAULT_MAX_TOKENS                               = 6000;

    private static final String DEFAULT_APP_NAME = "Apalabrazos";
    private static final String DEFAULT_APP_URL  = "https://github.com/Apalabrazos";

    public static final int    DEFAULT_LOG_PREVIEW        = 4000;
    public static final int    MAX_RETRIES_ON_503          = 2;
    public static final long   INITIAL_RETRY_DELAY_MS      = 1500L;
    public static final long   DEFAULT_429_WAIT_MS         = 45000L;

    // ── Runtime values ────────────────────────────────────────────────────────

    private static final String apiKey;
    private static final String apiUrl;
    private static final String model;
    private static final String fallbackModel;
    private static final String wordDictionaryPath;

    private static final int questionsPerLetter;
    private static final int questionsToGeneratePerLetterInBatch;
    private static final int lettersPerBatch;
    private static final int maxAttemptsPerBatch;
    private static final int maxTokens;

    private static final String appName;
    private static final String appUrl;

    static {
        apiKey       = readEnv("AI_API_KEY", "");
        apiUrl       = readEnv("AI_API_URL", DEFAULT_API_URL);
        model        = readEnv("AI_MODEL",   DEFAULT_MODEL);
        fallbackModel = readEnv("AI_FALLBACK_MODEL", DEFAULT_FALLBACK_MODEL);

        String rawDictPath = readEnv("AI_WORD_DICTIONARY_PATH", DEFAULT_WORD_DICTIONARY_PATH);
        wordDictionaryPath = (rawDictPath == null || rawDictPath.isBlank())
                ? DEFAULT_WORD_DICTIONARY_PATH
                : rawDictPath.trim();

        questionsPerLetter                     = readEnvInt("AI_QUESTIONS_PER_LETTER", DEFAULT_QUESTIONS_PER_LETTER);
        questionsToGeneratePerLetterInBatch    = readEnvInt("AI_QUESTIONS_TO_GENERATE_PER_LETTER_IN_BATCH", DEFAULT_QUESTIONS_TO_GENERATE_PER_LETTER_IN_BATCH);
        lettersPerBatch                        = readEnvInt("AI_LETTERS_PER_BATCH",    DEFAULT_LETTERS_PER_BATCH);
        maxAttemptsPerBatch                    = readEnvInt("AI_MAX_ATTEMPTS_PER_BATCH", DEFAULT_MAX_ATTEMPTS_PER_BATCH);
        maxTokens                              = readEnvInt("AI_MAX_TOKENS",           DEFAULT_MAX_TOKENS);

        appName = readEnv("AI_APP_NAME", DEFAULT_APP_NAME);
        appUrl  = readEnv("AI_APP_URL",  DEFAULT_APP_URL);

        log.info(
                "AIQuestionConfig cargado — apiUrl={}, model={}, fallbackModel={}, questionsPerLetter={}, " +
                "questionsToGeneratePerLetterInBatch={}, lettersPerBatch={}, maxAttemptsPerBatch={}, " +
                "maxTokens={}, appName={}, appUrl={}, wordDictionaryPath={}",
                apiUrl, model, fallbackModel, questionsPerLetter,
                questionsToGeneratePerLetterInBatch, lettersPerBatch, maxAttemptsPerBatch,
                maxTokens, appName, appUrl, wordDictionaryPath);
    }

    private AIQuestionConfig() {
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static String getApiKey()       { return apiKey; }
    public static String getApiUrl()       { return apiUrl; }
    public static String getModel()        { return model; }
    public static String getFallbackModel(){ return fallbackModel; }
    public static String getWordDictionaryPath() { return wordDictionaryPath; }

    public static int getQuestionsPerLetter()                   { return questionsPerLetter; }
    public static int getQuestionsToGeneratePerLetterInBatch()  { return questionsToGeneratePerLetterInBatch; }
    public static int getLettersPerBatch()                      { return lettersPerBatch; }
    public static int getMaxAttemptsPerBatch()                  { return maxAttemptsPerBatch; }
    public static int getMaxTokens()                            { return maxTokens; }

    public static String getAppName() { return appName; }
    public static String getAppUrl()  { return appUrl; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String readEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.trim().isEmpty()) ? defaultValue : value.trim();
    }

    private static int readEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Valor no numérico para {}: '{}'. Usando default={}", key, value, defaultValue);
            return defaultValue;
        }
    }
}
