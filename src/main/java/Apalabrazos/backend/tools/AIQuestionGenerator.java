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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AIQuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionGenerator.class);

    private static final String ENYE = "ñ";
    private static final String ENYE_UPPER = "Ñ";

    private static final String DEFAULT_API_URL = "http://172.18.0.20:11434/v1/messages";
    private static final String DEFAULT_MODEL = "gemma4:latest";
    private static final String DEFAULT_FALLBACK_MODEL = "";

    private static final int DEFAULT_QUESTIONS_PER_LETTER = 3;
    private static final int DEFAULT_QUESTIONS_TO_GENERATE_PER_LETTER_IN_BATCH = 4;
    private static final int DEFAULT_LETTERS_PER_BATCH = 5;
    private static final int DEFAULT_MAX_ATTEMPTS_PER_BATCH = 3;
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

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

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

        // Resolver la Ñ localmente, sin IA
        if (normalizedTargetLetters.contains(ENYE)) {
            List<Question> enyeQuestions = generateLocalEnyeQuestions();
            acceptedByLetter.get(ENYE).addAll(enyeQuestions.stream()
                    .limit(questionsPerLetter)
                    .collect(Collectors.toList()));
            log.info("Preguntas locales generadas para la letra '{}': {}", ENYE, acceptedByLetter.get(ENYE).size());
        }

        List<String> lettersForAI = normalizedTargetLetters.stream()
                .filter(letter -> !ENYE.equals(letter))
                .collect(Collectors.toList());

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
                    String responseBody = callAIForBatch(batchLetters);
                    List<Question> parsed = parseAIResponse(responseBody, batchLetters);

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

    private List<Question> generateLocalEnyeQuestions() {
        List<Question> list = new ArrayList<>();

        list.add(new Question(
                "Con la Ñ: Tubérculo comestible muy usado en América Latina.",
                List.of("ñame", "caña", "niña", "sueño"),
                0,
                QuestionStatus.INIT,
                QuestionLevel.MEDIUM,
                ENYE,
                "init"
        ));

        list.add(new Question(
                "Contiene la Ñ: Juguete o figura con forma de persona.",
                List.of("muñeca", "caña", "niño", "sueño"),
                0,
                QuestionStatus.INIT,
                QuestionLevel.MEDIUM,
                ENYE,
                "init"
        ));

        list.add(new Question(
                "Contiene la Ñ: Objeto de cartón que se rompe en fiestas para sacar dulces.",
                List.of("piñata", "año", "caña", "niña"),
                0,
                QuestionStatus.INIT,
                QuestionLevel.MEDIUM,
                ENYE,
                "init"
        ));

        return list;
    }

    private String callAIForBatch(List<String> batchLetters) throws Exception {
        return callAIWithModel(batchLetters, model, true);
    }

    private String callAIWithModel(List<String> batchLetters, String modelToUse, boolean allowFallback) throws Exception {
        String prompt = buildBatchPrompt(batchLetters);
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

            return callAIWithModel(batchLetters, fallbackModel, false);
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

    private String buildBatchPrompt(List<String> batchLetters) {
        String lettersText = batchLetters.stream()
                .map(String::toUpperCase)
                .collect(Collectors.joining(", "));

        String rulesByLetter = batchLetters.stream()
                .map(letter -> """
- Para la letra "%s":
  - genera EXACTAMENTE %d preguntas
  - cada pregunta debe tener "questionLetter": "%s"
  - el texto debe empezar por "Con la %s: ..." o "Contiene la %s: ..."
  - TODAS las respuestas deben contener la letra "%s"
  - si la pista empieza por "Con la %s", la respuesta correcta debe empezar por "%s"
  - si la pista empieza por "Contiene la %s", la respuesta correcta debe contener "%s"
""".formatted(
                        letter,
                        questionsToGeneratePerLetterInBatch,
                        letter.toLowerCase(Locale.ROOT),
                        letter.toUpperCase(Locale.ROOT),
                        letter.toUpperCase(Locale.ROOT),
                        letter.toLowerCase(Locale.ROOT),
                        letter.toUpperCase(Locale.ROOT),
                        letter.toUpperCase(Locale.ROOT),
                        letter.toUpperCase(Locale.ROOT),
                        letter.toUpperCase(Locale.ROOT)
                ))
                .collect(Collectors.joining("\n"));

        return """
Eres un generador experto de preguntas tipo rosco de Pasapalabra en español.
Debes responder SOLO con JSON válido.
No escribas markdown. No escribas explicaciones. No escribas comentarios.

Debes generar preguntas para estas letras: %s

REGLAS GENERALES OBLIGATORIAS:
1. Devuelve SOLO un JSON con un array "questionList".
2. Cada pregunta debe tener exactamente 4 respuestas.
3. Solo una respuesta es correcta.
4. Las respuestas deben ser palabras reales, reconocibles y en español.
5. No uses nombres propios.
6. No uses siglas.
7. No inventes palabras.
8. No repitas preguntas.
9. No repitas respuestas correctas.
10. questionStatus = "init"
11. userResponseRecorded = "init"
12. questionLevel = "medium"
13. Si no puedes generar una pregunta totalmente válida, no la incluyas.

REGLAS POR LETRA:
%s

FORMATO JSON OBLIGATORIO:
{
  "questionList": [
    {
      "questionLetter": "a",
      "questionText": "Con la A: ...",
      "questionResponsesList": ["Asteroide", "Ancla", "Arena", "Axioma"],
      "correctQuestionIndex": 0,
      "questionStatus": "init",
      "questionLevel": "medium",
      "userResponseRecorded": "init"
    }
  ]
}
""".formatted(lettersText, rulesByLetter);
    }

    private List<Question> parseAIResponse(String responseBody, List<String> expectedLetters) throws Exception {
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

                int correctIndex = qNode.path("correctQuestionIndex").asInt(-1);

                List<String> responses = new ArrayList<>();
                JsonNode respNode = qNode.path("questionResponsesList");
                if (respNode.isArray()) {
                    for (JsonNode r : respNode) {
                        responses.add(repairAndTrim(r.asText("")));
                    }
                }

                if (!expected.contains(letter)) {
                    continue;
                }

                if (text.isBlank() || responses.size() != 4 || correctIndex < 0 || correctIndex > 3) {
                    continue;
                }

                Question q = new Question(
                        text,
                        responses,
                        correctIndex,
                        QuestionStatus.INIT,
                        QuestionLevel.MEDIUM,
                        letter,
                        "init"
                );

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
        String repaired = repairAndTrim(text);

        if ("ñ".equalsIgnoreCase(repaired)) {
            return "ñ";
        }

        return Normalizer.normalize(repaired.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
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