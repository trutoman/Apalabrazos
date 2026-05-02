package Apalabrazos.backend.tools;

import Apalabrazos.backend.model.AlphabetMap;
import Apalabrazos.backend.model.Question;
import Apalabrazos.backend.model.QuestionLevel;
import Apalabrazos.backend.model.QuestionList;
import Apalabrazos.backend.model.QuestionStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AIQuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionGenerator.class);

    private static final String ENYE = "ñ";
    private static final String ENYE_UPPER = "Ñ";

    private static final String DEFAULT_API_URL = "http://172.18.0.20:11434/v1/messages";
    private static final String DEFAULT_MODEL = "gemma4:e2b";
    private static final String DEFAULT_FALLBACK_MODEL = "";

    private static final String DEFAULT_WORD_DICTIONARY_PATH = "src/main/resources/Apalabrazos/data/0_palabras_todas.txt";

    private static final int DEFAULT_QUESTIONS_PER_LETTER = 1;
    private static final int DEFAULT_QUESTIONS_TO_GENERATE_PER_LETTER_IN_BATCH = 2;
    private static final int DEFAULT_LETTERS_PER_BATCH = 27;
    private static final int DEFAULT_MAX_ATTEMPTS_PER_BATCH = 1;
    private static final int DEFAULT_MAX_TOKENS = 4000;

    private static final String DEFAULT_APP_NAME = "Apalabrazos";
    private static final String DEFAULT_APP_URL = "https://github.com/Apalabrazos";

    private static final int DEFAULT_LOG_PREVIEW = 4000;
    private static final int MAX_RETRIES_ON_503 = 2;
    private static final long INITIAL_RETRY_DELAY_MS = 1500L;
    private static final long DEFAULT_429_WAIT_MS = 45000L;

    private static final List<Charset> MOJIBAKE_SOURCE_CHARSETS = List.of(
            Charset.forName("CP437"),
            Charset.forName("CP850"),
            Charset.forName("windows-1252"),
            StandardCharsets.ISO_8859_1
    );

    /**
     * Palabras de apoyo SOLO para letras difíciles.
     * El resto de letras sigue usando el diccionario completo para mantener aleatoriedad.
     */
    private static final Map<String, List<String>> SAFE_WORDS_BY_LETTER = Map.of(
            "k", List.of("kilo", "kiwi", "karate", "kayak", "kebab", "koala"),
            "w", List.of("wifi", "wok", "waterpolo", "whisky", "windsurf", "web"),
            "x", List.of("xilofono", "xenofobia", "xerografia", "xilografia", "xenon", "xilema"),
            "ñ", List.of("niño", "señal", "montaña", "pañuelo", "caña", "bañera", "sueño", "araña"),
            "y", List.of("yate", "yema", "yogur", "yerno", "yunque", "yegua")
    );

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String fallbackModel;
    private final int questionsPerLetter;
    private final int questionsToGeneratePerLetterInBatch;
    private final int lettersPerBatch;
    private final int maxAttemptsPerBatch;
    private final int maxTokens;
    private final String appName;
    private final String appUrl;
    private final String wordDictionaryPath;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private Map<String, List<String>> wordsByLetterCache;

    public AIQuestionGenerator() {
        this.apiKey = readEnv("AI_API_KEY", "");
        this.apiUrl = readEnv("AI_API_URL", DEFAULT_API_URL).trim();
        this.model = readEnv("AI_MODEL", DEFAULT_MODEL);
        this.fallbackModel = readEnv("AI_FALLBACK_MODEL", DEFAULT_FALLBACK_MODEL);
        this.questionsPerLetter = readEnvInt("AI_QUESTIONS_PER_LETTER", DEFAULT_QUESTIONS_PER_LETTER);
        this.questionsToGeneratePerLetterInBatch = readEnvInt(
                "AI_QUESTIONS_TO_GENERATE_PER_LETTER_IN_BATCH",
                DEFAULT_QUESTIONS_TO_GENERATE_PER_LETTER_IN_BATCH
        );
        this.lettersPerBatch = readEnvInt("AI_LETTERS_PER_BATCH", DEFAULT_LETTERS_PER_BATCH);
        this.maxAttemptsPerBatch = readEnvInt("AI_MAX_ATTEMPTS_PER_BATCH", DEFAULT_MAX_ATTEMPTS_PER_BATCH);
        this.maxTokens = readEnvInt("AI_MAX_TOKENS", DEFAULT_MAX_TOKENS);
        this.appName = readEnv("AI_APP_NAME", DEFAULT_APP_NAME);
        this.appUrl = readEnv("AI_APP_URL", DEFAULT_APP_URL);
        this.wordDictionaryPath = readEnv("AI_WORD_DICTIONARY_PATH", DEFAULT_WORD_DICTIONARY_PATH);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);

        log.info(
                "AIQuestionGenerator configurado para Anthropic-compatible. apiUrl={}, modelo={}, fallbackModel={}, questionsPerLetter={}, questionsToGeneratePerLetterInBatch={}, lettersPerBatch={}, maxAttemptsPerBatch={}, maxTokens={}, appName={}, appUrl={}",
                apiUrl, model, fallbackModel, questionsPerLetter, questionsToGeneratePerLetterInBatch,
                lettersPerBatch, maxAttemptsPerBatch, maxTokens, appName, appUrl
        );

        log.info("Diccionario de palabras configurado en: {}", wordDictionaryPath);
    }

    public int getQuestionsPerLetter() {
        return questionsPerLetter;
    }

    public void validateConfiguration() {
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("AI_API_URL no configurada.");
        }

        if (model == null || model.isBlank()) {
            throw new IllegalStateException("AI_MODEL no configurado.");
        }

        if (questionsPerLetter <= 0) {
            throw new IllegalStateException("AI_QUESTIONS_PER_LETTER debe ser > 0.");
        }

        if (questionsToGeneratePerLetterInBatch < questionsPerLetter) {
            throw new IllegalStateException("AI_QUESTIONS_TO_GENERATE_PER_LETTER_IN_BATCH debe ser >= AI_QUESTIONS_PER_LETTER.");
        }

        if (lettersPerBatch <= 0) {
            throw new IllegalStateException("AI_LETTERS_PER_BATCH debe ser > 0.");
        }

        if (maxTokens <= 0) {
            throw new IllegalStateException("AI_MAX_TOKENS debe ser > 0.");
        }
    }

    public QuestionList generateFullBattery() throws Exception {
        validateConfiguration();

        List<String> allLetters = new ArrayList<>(AlphabetMap.MAP.values()).stream()
                .map(this::normalizeLetter)
                .distinct()
                .collect(Collectors.toList());

        return generateBatteryForMissingLetters(allLetters);
    }

    public QuestionList generateBatteryForMissingLetters(List<String> targetLetters) throws Exception {
        validateConfiguration();

        if (targetLetters == null || targetLetters.isEmpty()) {
            log.info("No hay letras pendientes de generar.");
            return new QuestionList(new ArrayList<>(), 0);
        }

        List<String> normalizedTargetLetters = targetLetters.stream()
                .map(this::normalizeLetter)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());

        Map<String, List<Question>> acceptedByLetter = new LinkedHashMap<>();
        for (String letter : normalizedTargetLetters) {
            acceptedByLetter.put(letter, new ArrayList<>());
        }
        // Ahora TODAS las letras (incluida la ñ) van a la IA
        List<String> lettersForAI = new ArrayList<>(normalizedTargetLetters);

        if (lettersForAI.isEmpty()) {
            List<Question> allQuestions = flattenQuestions(acceptedByLetter.values());
            QuestionList result = new QuestionList(allQuestions, allQuestions.size());
            log.info("Generadas {} preguntas para letras pendientes", allQuestions.size());
            return result;
        }

        List<List<String>> batches = partitionLetters(lettersForAI, lettersPerBatch);
        boolean quotaExceeded = false;

        for (List<String> batchLetters : batches) {
            if (quotaExceeded) {
                break;
            }

            log.info("Procesando lote de letras pendientes: {}", batchLetters);

            int attempts = 0;

            while (attempts < maxAttemptsPerBatch && !areBatchLettersComplete(batchLetters, acceptedByLetter)) {
                attempts++;

                try {
                    Map<String, CandidateQuestionData> candidatesByLetter = buildCandidatesForBatch(batchLetters);

                    if (candidatesByLetter.isEmpty()) {
                        log.warn("No se pudieron preparar candidatas para el lote {}. Se salta el intento.", batchLetters);
                        break;
                    }

                    log.info("Enviando lote {} a la IA con {} candidatas", batchLetters, candidatesByLetter.size());
                    String responseBody = callAIForBatch(batchLetters, candidatesByLetter);
                    List<Question> parsed = parseAIResponse(responseBody, batchLetters, candidatesByLetter);

                    int acceptedThisAttempt = 0;

                    for (Question q : parsed) {
                        if (!isValidQuestion(q)) {
                            log.warn(
                                    "Pregunta descartada | Letra: {} | Pista: {} | Respuestas: {}",
                                    q != null ? q.getQuestionLetter() : "null",
                                    q != null ? q.getQuestionText() : "null",
                                    q != null ? q.getQuestionResponsesList() : "null"
                            );
                            continue;
                        }

                        String letter = normalizeLetter(q.getQuestionLetter());
                        List<Question> existingForLetter = acceptedByLetter.getOrDefault(letter, Collections.emptyList());

                        if (existingForLetter.size() >= questionsPerLetter) {
                            continue;
                        }

                        if (isDuplicate(flattenQuestions(acceptedByLetter.values()), q)) {
                            log.warn(
                                    "Pregunta descartada por duplicada | Letra: {} | Pista: {} | Correcta: {}",
                                    letter,
                                    q.getQuestionText(),
                                    q.getQuestionResponsesList().get(q.getCorrectQuestionIndex())
                            );
                            continue;
                        }

                        acceptedByLetter.get(letter).add(q);
                        acceptedThisAttempt++;

                        log.info(
                                "Pregunta aceptada | Letra: {} | Pista: {} | Correcta: {} | Total letra: {}/{}",
                                letter,
                                q.getQuestionText(),
                                q.getQuestionResponsesList().get(q.getCorrectQuestionIndex()),
                                acceptedByLetter.get(letter).size(),
                                questionsPerLetter
                        );
                    }

                    log.info(
                            "Lote pendiente {} intento {}/{} -> aceptadas en intento: {}",
                            batchLetters,
                            attempts,
                            maxAttemptsPerBatch,
                            acceptedThisAttempt
                    );

                } catch (QuotaExceededException e) {
                    log.warn("Cuota agotada mientras se procesaban letras pendientes. Se detiene la generación: {}", e.getMessage());
                    quotaExceeded = true;
                    break;
                } catch (Exception e) {
                    log.error(
                            "Lote pendiente {} intento {}/{} fallido: {}",
                            batchLetters,
                            attempts,
                            maxAttemptsPerBatch,
                            e.getMessage(),
                            e
                    );

                    if (isQuotaException(e)) {
                        long waitMs = extractRetryDelayMillis(e.getMessage());
                        if (waitMs <= 0) {
                            waitMs = DEFAULT_429_WAIT_MS;
                        }

                        log.warn("Esperando {} ms antes de reintentar lote pendiente {}", waitMs, batchLetters);
                        Thread.sleep(waitMs);
                    }
                }
            }

            logBatchStatus(batchLetters, acceptedByLetter);
        }

        List<Question> allQuestions = flattenQuestions(acceptedByLetter.values());
        QuestionList result = new QuestionList(allQuestions, allQuestions.size());
        log.info("Generadas {} preguntas para letras pendientes", allQuestions.size());
        return result;
    }
    private String callAIForBatch(
            List<String> batchLetters,
            Map<String, CandidateQuestionData> candidatesByLetter
    ) throws Exception {
        return callAIWithModel(batchLetters, candidatesByLetter, model, true);
    }

    private String callAIWithModel(
            List<String> batchLetters,
            Map<String, CandidateQuestionData> candidatesByLetter,
            String modelToUse,
            boolean allowFallback
    ) throws Exception {
        String prompt = buildBatchPrompt(batchLetters, candidatesByLetter);
        String requestBody = buildRequestBody(prompt, modelToUse);

        long delayMs = INITIAL_RETRY_DELAY_MS;
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES_ON_503 + 1; attempt++) {
            try {
                return executeMessagesRequest(batchLetters, modelToUse, requestBody);
            } catch (Exception e) {
                lastException = e;

                if (!is503Exception(e) || attempt > MAX_RETRIES_ON_503) {
                    break;
                }

                log.warn(
                        "Lote {} -> intento {} con modelo '{}' fallido por 503. Reintentando en {} ms",
                        batchLetters,
                        attempt,
                        modelToUse,
                        delayMs
                );

                Thread.sleep(delayMs);
                delayMs *= 2;
            }
        }

        if (allowFallback
                && fallbackModel != null
                && !fallbackModel.isBlank()
                && !fallbackModel.equals(modelToUse)
                && is503Exception(lastException)) {

            log.warn(
                    "Lote {} -> fallback de modelo por 503: '{}' -> '{}'",
                    batchLetters,
                    modelToUse,
                    fallbackModel
            );

            return callAIWithModel(batchLetters, candidatesByLetter, fallbackModel, false);
        }

        if (lastException != null) {
            throw lastException;
        }

        throw new RuntimeException("No se pudo obtener respuesta de la IA.");
    }

    private String buildRequestBody(String prompt, String modelToUse) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelToUse);
        body.put("max_tokens", maxTokens);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);

        userMessage.put("content", content);
        messages.add(userMessage);

        body.put("messages", messages);

        return mapper.writeValueAsString(body);
    }

    private String executeMessagesRequest(List<String> batchLetters, String modelToUse, String requestBody) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120));

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("x-api-key", apiKey);
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        int status = response.statusCode();
        String body = response.body();

        log.info("HTTP status IA Anthropic-compatible para lote {} con modelo '{}': {}", batchLetters, modelToUse, status);

        if (status == 200) {
            if (body == null || body.isBlank()) {
                throw new RuntimeException("La API Anthropic-compatible devolvió HTTP 200 pero el cuerpo está vacío.");
            }
            return body;
        }

        log.error(
                "Error de API Anthropic-compatible (status {}) con modelo '{}' para lote {}: {}",
                status,
                modelToUse,
                batchLetters,
                preview(body, DEFAULT_LOG_PREVIEW)
        );

        String detailedMessage = extractApiErrorMessage(body);

        if (status == 400) {
            throw new RuntimeException("HTTP 400 INVALID_REQUEST. Detalle: " + detailedMessage);
        }

        if (status == 401 || status == 403) {
            throw new RuntimeException("HTTP " + status + " auth/permiso. Detalle: " + detailedMessage);
        }

        if (status == 404) {
            throw new RuntimeException("HTTP 404 endpoint/modelo no encontrado. Detalle: " + detailedMessage);
        }

        if (status == 429) {
            throw new QuotaExceededException("HTTP 429 cuota/rate limit. Detalle: " + detailedMessage);
        }

        if (status == 503) {
            throw new RuntimeException("HTTP 503 UNAVAILABLE. Detalle: " + detailedMessage);
        }

        if (status >= 500) {
            throw new RuntimeException("HTTP " + status + " error interno del proveedor. Detalle: " + detailedMessage);
        }

        throw new RuntimeException("Error llamando a la API Anthropic-compatible: HTTP " + status + ". Detalle: " + detailedMessage);
    }

    private String buildBatchPrompt(
            List<String> batchLetters,
            Map<String, CandidateQuestionData> candidatesByLetter
    ) {
        String wordsData = batchLetters.stream()
                .map(this::normalizeLetter)
                .filter(candidatesByLetter::containsKey)
                .map(letter -> {
                    CandidateQuestionData c = candidatesByLetter.get(letter);
                    boolean isEnye = ENYE.equals(letter);
                    String upper = isEnye ? ENYE_UPPER : letter.toUpperCase(Locale.ROOT);
                    String prefix = isEnye ? "Contiene la Ñ:" : "Con la " + upper + ":";

                    return """
- Letra: "%s"
  Palabra correcta: "%s"
  El enunciado debe empezar exactamente por: "%s"
""".formatted(letter, c.correctWord(), prefix);
                })
                .collect(Collectors.joining("\n"));

        return """
Eres un generador experto de preguntas tipo rosco de Pasapalabra en español.
Debes responder SOLO con JSON válido.
No escribas markdown.
No escribas explicaciones.
No generes respuestas posibles.
No generes correctQuestionIndex.

Tu única tarea es generar una pista breve para cada palabra correcta.
La pista NO puede mencionar literalmente la palabra correcta ni derivados evidentes.
La pista debe definir EXACTAMENTE la palabra correcta, no otra palabra parecida.
La pista debe ser clara, natural, en español y de dificultad media.

DATOS:
%s

FORMATO JSON OBLIGATORIO:
{
  "questionList": [
    {
      "questionLetter": "a",
      "questionText": "Con la A: Pista breve sin decir la respuesta."
    }
  ]
}
""".formatted(wordsData);
    }

    private List<Question> parseAIResponse(
            String responseBody,
            List<String> expectedLetters,
            Map<String, CandidateQuestionData> candidatesByLetter
    ) throws Exception {
        String content = extractTextContentFromResponse(responseBody);

        if (content.isBlank()) {
            throw new RuntimeException("La IA devolvió contenido vacío.");
        }

        log.info("Respuesta cruda IA para lote {}:\n{}", expectedLetters, preview(content, DEFAULT_LOG_PREVIEW));

        JsonNode questionsNode;
        try {
            questionsNode = mapper.readTree(content);
        } catch (Exception e) {
            throw new RuntimeException("La IA no devolvió JSON válido. Contenido recibido: " + preview(content, 1000), e);
        }

        JsonNode listNode = null;

        if (questionsNode.isObject() && questionsNode.has("questionList") && questionsNode.get("questionList").isArray()) {
            listNode = questionsNode.get("questionList");
        } else if (questionsNode.isArray()) {
            listNode = questionsNode;
        }

        if (listNode == null || !listNode.isArray()) {
            throw new RuntimeException("La respuesta de la IA no contiene un array de preguntas válido.");
        }

        Set<String> expected = expectedLetters.stream()
                .map(this::normalizeLetter)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Question> questions = new ArrayList<>();

        for (JsonNode qNode : listNode) {
            try {
                String rawLetter = repairAndTrim(qNode.path("questionLetter").asText(""));
                String rawText = repairAndTrim(qNode.path("questionText").asText(""));

                String letter = normalizeLetter(rawLetter);
                String text = rawText;

                if (!expected.contains(letter)) {
                    continue;
                }

                CandidateQuestionData candidate = candidatesByLetter.get(letter);

                if (candidate == null || text.isBlank()) {
                    continue;
                }

                String finalLetter = normalizeLetter(candidate.letter());

                Question q = new Question(
                        text,
                        new ArrayList<>(candidate.responses()),
                        candidate.correctIndex(),
                        QuestionStatus.INIT,
                        QuestionLevel.MEDIUM,
                        finalLetter,
                        "init"
                );

                q.setQuestionLetter(finalLetter);
                questions.add(q);

            } catch (Exception e) {
                log.warn("Error parseando pregunta IA para lote {}: {}", expectedLetters, e.getMessage());
            }
        }

        log.info("Parseadas {} preguntas candidatas para lote {}", questions.size(), expectedLetters);
        return questions;
    }

    private String extractTextContentFromResponse(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        JsonNode root = mapper.readTree(responseBody);
        JsonNode contentNode = root.path("content");

        if (!contentNode.isArray() || contentNode.isEmpty()) {
            throw new RuntimeException("La respuesta Anthropic-compatible no contiene 'content'.");
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode item : contentNode) {
            String type = item.path("type").asText("");
            if ("text".equals(type)) {
                String text = item.path("text").asText("");
                if (!text.isBlank()) {
                    sb.append(text);
                }
            }
        }

        return sanitizeContent(repairMojibakeIfNeeded(sb.toString()));
    }

    private Map<String, CandidateQuestionData> buildCandidatesForBatch(List<String> batchLetters) throws Exception {
        Map<String, List<String>> wordsByLetter = getWordsByLetter();
        Map<String, CandidateQuestionData> result = new LinkedHashMap<>();
        Random random = new Random();

        for (String rawLetter : batchLetters) {
            String letter = normalizeLetter(rawLetter);

            List<String> availableWords;

            // Para letras difíciles usamos una bolsa de apoyo estable, porque el diccionario bruto
            // suele tener muy pocas palabras jugables. Para el resto mantenemos aleatoriedad total.
            List<String> safeWords = SAFE_WORDS_BY_LETTER.get(letter);
            if (isDifficultLetter(letter) && safeWords != null && safeWords.size() >= 4) {
                availableWords = new ArrayList<>(safeWords);
                log.info("Usando bolsa de apoyo para letra '{}': {}", letter, safeWords);
            } else {
                availableWords = new ArrayList<>(wordsByLetter.getOrDefault(letter, Collections.emptyList()));
            }

            if (availableWords.size() < 4) {
                log.warn("No hay suficientes palabras para la letra '{}'. Disponibles: {}", letter, availableWords.size());
                continue;
            }

            Collections.shuffle(availableWords);

            List<String> responses = availableWords.stream()
                    .limit(4)
                    .collect(Collectors.toCollection(ArrayList::new));

            int correctIndex = random.nextInt(4);
            String correctWord = responses.get(correctIndex);

            result.put(letter, new CandidateQuestionData(letter, responses, correctIndex, correctWord));

            log.info(
                    "Candidatas preparadas | Letra: {} | Respuestas: {} | Correcta: {}",
                    letter,
                    responses,
                    correctWord
            );
        }

        return result;
    }

    private Map<String, List<String>> getWordsByLetter() throws Exception {
        if (wordsByLetterCache != null) {
            return wordsByLetterCache;
        }

        Path path = Path.of(wordDictionaryPath).toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            throw new IllegalStateException("No existe el diccionario de palabras: " + path);
        }

        log.info("Cargando diccionario desde {}", path);

        Map<String, List<String>> result = new LinkedHashMap<>();
        Map<String, Set<String>> seenByLetter = new LinkedHashMap<>();

        long totalLines = 0;
        long acceptedWords = 0;

        try (java.util.stream.Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                totalLines++;

                String word = repairAndTrim(line);

                if (!looksReasonableDictionaryWord(word)) {
                    continue;
                }

                String firstLetter = getDictionaryKeyForWord(word);

                if (firstLetter.isBlank()) {
                    continue;
                }

                String normalizedWord = normalizeFreeText(word);

                result.computeIfAbsent(firstLetter, k -> new ArrayList<>());
                seenByLetter.computeIfAbsent(firstLetter, k -> new LinkedHashSet<>());

                if (seenByLetter.get(firstLetter).add(normalizedWord)) {
                    result.get(firstLetter).add(word);
                    acceptedWords++;
                }
            }
        }

        wordsByLetterCache = result;

        log.info("Diccionario cargado desde {}. Líneas leídas: {}. Palabras aceptadas: {}", path, totalLines, acceptedWords);
        for (Map.Entry<String, List<String>> entry : wordsByLetterCache.entrySet()) {
            log.info("Letra '{}' -> {} palabras disponibles", entry.getKey(), entry.getValue().size());
        }

        return wordsByLetterCache;
    }

    private boolean looksReasonableDictionaryWord(String word) {
        if (word == null) return false;

        String repaired = repairAndTrim(word);
        String normalized = normalizeFreeText(repaired);
        String firstLetter = getDictionaryKeyForWord(repaired);

        if (repaired.isBlank()) return false;
        if (repaired.length() < 4 || repaired.length() > 11) return false;
        if (repaired.contains(" ")) return false;
        if (repaired.contains("-")) return false;
        if (looksLikeMojibake(repaired)) return false;
        if (!repaired.matches("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü]+$")) return false;

        // Evita palabras raras con k/w en letras normales, pero permite K y W.
        if (!"k".equals(firstLetter) && !"w".equals(firstLetter)) {
            if (normalized.contains("k") || normalized.contains("w")) {
                return false;
            }
        }

        if (isLikelyBadConjugation(normalized)) return false;

        return true;
    }

    private boolean isDifficultLetter(String letter) {
        return "k".equals(letter)
                || "w".equals(letter)
                || "x".equals(letter)
                || "ñ".equals(letter)
                || "y".equals(letter);
    }

    private boolean isLikelyBadConjugation(String normalized) {
        if (normalized == null || normalized.isBlank()) return true;

        return normalized.endsWith("abais")
                || normalized.endsWith("abamos")
                || normalized.endsWith("arais")
                || normalized.endsWith("erais")
                || normalized.endsWith("irais")
                || normalized.endsWith("aramos")
                || normalized.endsWith("eramos")
                || normalized.endsWith("iramos")
                || normalized.endsWith("aremos")
                || normalized.endsWith("eremos")
                || normalized.endsWith("iremos")
                || normalized.endsWith("aria")
                || normalized.endsWith("arias")
                || normalized.endsWith("arian")
                || normalized.endsWith("eria")
                || normalized.endsWith("erias")
                || normalized.endsWith("erian")
                || normalized.endsWith("iria")
                || normalized.endsWith("irias")
                || normalized.endsWith("irian")
                || normalized.endsWith("ase")
                || normalized.endsWith("ases")
                || normalized.endsWith("asen")
                || normalized.endsWith("aseis")
                || normalized.endsWith("iese")
                || normalized.endsWith("iesen")
                || normalized.endsWith("ieses")
                || normalized.endsWith("ieseis")
                || normalized.endsWith("aste")
                || normalized.endsWith("asteis")
                || normalized.endsWith("iste")
                || normalized.endsWith("isteis")
                || normalized.endsWith("aron")
                || normalized.endsWith("aran")
                || normalized.endsWith("areis")
                || normalized.endsWith("ereis")
                || normalized.endsWith("ireis")
                || normalized.endsWith("ando")
                || normalized.endsWith("iendo")
                || normalized.endsWith("ad");
    }

    /**
     * Clave con la que se agrupa una palabra del diccionario.
     *
     * Reglas tipo Pasapalabra:
     * - A-Z: la palabra debe EMPEZAR por la letra.
     * - Ñ: la palabra debe CONTENER la ñ, no necesariamente empezar por ella.
     */
    private String getDictionaryKeyForWord(String word) {
        if (word == null || word.isBlank()) return "";

        String repaired = repairAndTrim(word);
        String lower = repaired.toLowerCase(Locale.ROOT);

        if (lower.contains(ENYE)) {
            return ENYE;
        }

        String first = lower.substring(0, 1);
        return normalizeFreeText(first);
    }

    private record CandidateQuestionData(
            String letter,
            List<String> responses,
            int correctIndex,
            String correctWord
    ) {}

    private boolean isValidQuestion(Question q) {
        if (q == null) return false;
        if (q.getQuestionResponsesList() == null || q.getQuestionResponsesList().size() != 4) return false;
        if (q.getCorrectQuestionIndex() < 0 || q.getCorrectQuestionIndex() > 3) return false;
        if (q.getQuestionText() == null || q.getQuestionText().isBlank()) return false;
        if (q.getQuestionLetter() == null || q.getQuestionLetter().isBlank()) return false;

        String text = normalizeFreeText(repairAndTrim(q.getQuestionText()));
        String letter = normalizeLetter(repairAndTrim(q.getQuestionLetter()));

        String expectedStartsPrefix = "con la " + letter;
        String expectedContainsPrefix = "contiene la " + letter;

        boolean startsMode = text.startsWith(expectedStartsPrefix);
        boolean containsMode = text.startsWith(expectedContainsPrefix);

        if (!startsMode && !containsMode) {
            return false;
        }

        for (String r : q.getQuestionResponsesList()) {
            if (!looksReasonableAnswer(r)) {
                return false;
            }
        }

        String correct = repairAndTrim(q.getQuestionResponsesList().get(q.getCorrectQuestionIndex()));

        for (String r : q.getQuestionResponsesList()) {
            if (!containsLetter(repairAndTrim(r), letter)) return false;
        }

        if (startsMode && !startsWithLetter(correct, letter)) {
            return false;
        }

        if (containsMode && !containsLetter(correct, letter)) {
            return false;
        }

        if (hasDuplicateResponses(q.getQuestionResponsesList())) {
            return false;
        }

        return true;
    }

    private boolean isDuplicate(List<Question> existing, Question candidate) {
        String candidateText = normalizeFreeText(repairAndTrim(candidate.getQuestionText()));
        String candidateCorrect = normalizeFreeText(
                repairAndTrim(candidate.getQuestionResponsesList().get(candidate.getCorrectQuestionIndex()))
        );

        for (Question q : existing) {
            String existingText = normalizeFreeText(repairAndTrim(q.getQuestionText()));
            String existingCorrect = normalizeFreeText(
                    repairAndTrim(q.getQuestionResponsesList().get(q.getCorrectQuestionIndex()))
            );

            if (existingText.equals(candidateText)) return true;
            if (existingCorrect.equals(candidateCorrect)) return true;
        }

        return false;
    }

    private boolean hasDuplicateResponses(List<String> responses) {
        Set<String> set = responses.stream()
                .map(this::repairAndTrim)
                .map(this::normalizeFreeText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return set.size() != responses.size();
    }

    private boolean looksReasonableAnswer(String answer) {
        if (answer == null || answer.isBlank()) return false;

        String repaired = repairAndTrim(answer);
        String normalized = normalizeFreeText(repaired);

        if (normalized.isBlank()) return false;
        if (normalized.length() < 2 || normalized.length() > 40) return false;
        if (normalized.matches(".*\\d.*")) return false;
        if (normalized.contains("?") || normalized.contains("¿") || normalized.contains("!")) return false;
        if (normalized.contains(",")) return false;
        if (normalized.contains(";")) return false;
        if (normalized.contains(":")) return false;

        int words = repaired.trim().split("\\s+").length;
        if (words > 2) return false;

        if (looksLikeMojibake(repaired)) return false;
        if (!repaired.matches("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü\\s-]+$")) return false;

        return true;
    }

    private boolean areBatchLettersComplete(List<String> batchLetters, Map<String, List<Question>> acceptedByLetter) {
        for (String letter : batchLetters) {
            List<Question> list = acceptedByLetter.getOrDefault(letter, Collections.emptyList());
            if (list.size() < questionsPerLetter) {
                return false;
            }
        }
        return true;
    }

    private void logBatchStatus(List<String> batchLetters, Map<String, List<Question>> acceptedByLetter) {
        for (String letter : batchLetters) {
            int count = acceptedByLetter.getOrDefault(letter, Collections.emptyList()).size();
            if (count < questionsPerLetter) {
                log.warn("La letra '{}' quedó incompleta: {}/{}", letter, count, questionsPerLetter);
            } else {
                log.info("La letra '{}' quedó completa: {}/{}", letter, count, questionsPerLetter);
            }
        }
    }

    private List<List<String>> partitionLetters(List<String> letters, int batchSize) {
        List<List<String>> batches = new ArrayList<>();

        for (int i = 0; i < letters.size(); i += batchSize) {
            int end = Math.min(i + batchSize, letters.size());
            batches.add(new ArrayList<>(letters.subList(i, end)));
        }

        return batches;
    }

    private List<Question> flattenQuestions(Collection<List<Question>> values) {
        List<Question> result = new ArrayList<>();
        for (List<Question> list : values) {
            result.addAll(list.stream().limit(questionsPerLetter).toList());
        }
        return result;
    }

    private String sanitizeContent(String content) {
        if (content == null) return "";

        content = content.trim();

        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }

        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        return content.trim();
    }

    public void saveToFile(QuestionList questions, String filePath) throws Exception {
        String json = mapper.writeValueAsString(questions);
        Files.writeString(Path.of(filePath), json, StandardCharsets.UTF_8);
        log.info("Preguntas guardadas en: {}", filePath);
    }

    private boolean isQuotaException(Exception e) {
        if (e == null || e.getMessage() == null) return false;
        return e.getMessage().contains("HTTP 429");
    }

    private boolean is503Exception(Exception e) {
        if (e == null || e.getMessage() == null) return false;
        return e.getMessage().contains("HTTP 503") || e.getMessage().contains("UNAVAILABLE");
    }

    private long extractRetryDelayMillis(String message) {
        if (message == null || message.isBlank()) {
            return 0L;
        }

        Matcher matcher = Pattern.compile("Please retry in\\s+([0-9]+(?:\\.[0-9]+)?)s", Pattern.CASE_INSENSITIVE)
                .matcher(message);

        if (matcher.find()) {
            double seconds = Double.parseDouble(matcher.group(1));
            return (long) Math.ceil(seconds * 1000);
        }

        matcher = Pattern.compile("\"retryDelay\"\\s*:\\s*\"([0-9]+)s\"", Pattern.CASE_INSENSITIVE)
                .matcher(message);

        if (matcher.find()) {
            long seconds = Long.parseLong(matcher.group(1));
            return seconds * 1000L;
        }

        return 0L;
    }

    private String normalizeLetter(String letter) {
        if (letter == null) return "";
        String repaired = repairAndTrim(letter);
        String trimmed = repaired.trim().toLowerCase(Locale.ROOT);

        if ("ñ".equals(trimmed) || "├▒".equals(trimmed) || "ã±".equals(trimmed) || "Ã±".equals(trimmed) || "┬ñ".equals(trimmed)) {
            return "ñ";
        }

        return normalizeFreeText(trimmed);
    }

    private String normalizeFreeText(String text) {
        if (text == null) return "";

        String repaired = repairAndTrim(text)
                .trim()
                .toLowerCase(Locale.ROOT);

        // IMPORTANTE:
        // Normalizer NFD convierte "ñ" en "n" + tilde combinada.
        // Si luego quitamos los diacríticos, la ñ se pierde y pasa a ser n.
        // Para el rosco necesitamos conservar la ñ como letra distinta.
        repaired = repaired
                .replace("ñ", "__ENYE__")
                .replace("Ñ", "__ENYE__");

        String normalized = Normalizer.normalize(repaired, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("__ENYE__", "ñ");

        return normalized;
    }

    private boolean startsWithLetter(String word, String letter) {
        if (word == null || letter == null) return false;

        String rawWord = repairAndTrim(word).toLowerCase(Locale.ROOT);
        String normalizedLetter = normalizeLetter(letter);

        if (rawWord.isBlank() || normalizedLetter.isBlank()) return false;

        if ("ñ".equals(normalizedLetter)) {
            return rawWord.startsWith("ñ");
        }

        return normalizeFreeText(rawWord).startsWith(normalizedLetter);
    }

    private boolean containsLetter(String word, String letter) {
        if (word == null || letter == null) return false;

        String rawWord = repairAndTrim(word).toLowerCase(Locale.ROOT);
        String normalizedLetter = normalizeLetter(letter);

        if (rawWord.isBlank() || normalizedLetter.isBlank()) return false;

        if ("ñ".equals(normalizedLetter)) {
            return rawWord.contains("ñ");
        }

        return normalizeFreeText(rawWord).contains(normalizedLetter);
    }

    private String extractApiErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "sin detalle";
        }

        try {
            JsonNode root = mapper.readTree(responseBody);

            if (root.has("error")) {
                JsonNode errorNode = root.path("error");
                String message = errorNode.path("message").asText("");
                String type = errorNode.path("type").asText("");
                String code = errorNode.path("code").asText("");

                StringBuilder sb = new StringBuilder();

                if (!code.isBlank()) {
                    sb.append("code=").append(code);
                }
                if (!type.isBlank()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append("type=").append(type);
                }
                if (!message.isBlank()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append("message=").append(message);
                }

                if (sb.length() > 0) {
                    return sb.toString();
                }
            }

            String type = root.path("type").asText("");
            String message = root.path("message").asText("");
            if (!type.isBlank() || !message.isBlank()) {
                StringBuilder sb = new StringBuilder();
                if (!type.isBlank()) sb.append("type=").append(type);
                if (!message.isBlank()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append("message=").append(message);
                }
                return sb.toString();
            }

        } catch (Exception e) {
            log.warn("No se pudo parsear el error de la API: {}", e.getMessage());
        }

        return preview(responseBody, 500);
    }

    private String preview(String text, int maxLen) {
        if (text == null) return "";
        return text.substring(0, Math.min(text.length(), maxLen));
    }

    private String repairMojibakeIfNeeded(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        if (!looksLikeMojibake(input)) {
            return input;
        }

        for (Charset sourceCharset : MOJIBAKE_SOURCE_CHARSETS) {
            try {
                String repaired = new String(input.getBytes(sourceCharset), StandardCharsets.UTF_8);

                if (repaired != null
                        && !repaired.isBlank()
                        && !repaired.equals(input)
                        && scoreTextQuality(repaired) > scoreTextQuality(input)) {
                    return repaired;
                }
            } catch (Exception ignored) {
            }
        }

        String trimmed = input.trim();
        if ("├▒".equals(trimmed) || "Ã±".equals(trimmed) || "ã±".equals(trimmed) || "┬ñ".equals(trimmed)) {
            return "ñ";
        }

        return input;
    }

    private boolean looksLikeMojibake(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return text.contains("Ã")
                || text.contains("Â")
                || text.contains("├")
                || text.contains("┬")
                || text.contains("�");
    }

    private int scoreTextQuality(String text) {
        if (text == null || text.isBlank()) {
            return Integer.MIN_VALUE;
        }

        int score = 0;

        if (text.contains("Ã")) score -= 10;
        if (text.contains("Â")) score -= 10;
        if (text.contains("├")) score -= 12;
        if (text.contains("┬")) score -= 12;
        if (text.contains("�")) score -= 20;

        for (char c : text.toCharArray()) {
            if ("áéíóúÁÉÍÓÚñÑüÜ".indexOf(c) >= 0) {
                score += 3;
            }
        }

        return score;
    }

    private String repairAndTrim(String text) {
        if (text == null) {
            return "";
        }
        return repairMojibakeIfNeeded(text).trim();
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

    private static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }
}