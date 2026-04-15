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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AIQuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionGenerator.class);

    private static final String DEFAULT_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String DEFAULT_MODEL = "google/gemini-2.0-flash-001";
    private static final int DEFAULT_QUESTIONS_PER_LETTER = 3;
    private static final String DEFAULT_APP_NAME = "Apalabrazos";
    private static final String DEFAULT_APP_URL = "https://github.com/Apalabrazos";

    private static final int DEFAULT_BATCH_SIZE = 5;
    private static final int DEFAULT_MAX_BATCH_ATTEMPTS = 5;
    private static final int DEFAULT_MAX_PARSE_LOG_CHARS = 4000;

    private static final Set<String> DIFFICULT_LETTERS = Set.of("k", "w", "x", "y", "ñ");

    private static final Set<String> BANNED_ANSWERS = Set.of(
            "waterfox", "resquemor", "ñafiles", "aproximacion", "yeru", "xisquion", "xilopalo"
    );

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final int questionsPerLetter;
    private final String appName;
    private final String appUrl;
    private final int batchSize;
    private final int maxBatchAttempts;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public AIQuestionGenerator() {
        this.apiKey = readEnv("AI_API_KEY", "");
        this.apiUrl = readEnv("AI_API_URL", DEFAULT_API_URL);
        this.model = readEnv("AI_MODEL", DEFAULT_MODEL);
        this.questionsPerLetter = readEnvInt("AI_QUESTIONS_PER_LETTER", DEFAULT_QUESTIONS_PER_LETTER);
        this.appName = readEnv("AI_APP_NAME", DEFAULT_APP_NAME);
        this.appUrl = readEnv("AI_APP_URL", DEFAULT_APP_URL);
        this.batchSize = readEnvInt("AI_BATCH_SIZE", DEFAULT_BATCH_SIZE);
        this.maxBatchAttempts = readEnvInt("AI_MAX_BATCH_ATTEMPTS", DEFAULT_MAX_BATCH_ATTEMPTS);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);

        log.info("AIQuestionGenerator configurado. modelo={}, batchSize={}, questionsPerLetter={}",
                model, batchSize, questionsPerLetter);
    }

    public QuestionList generateFullBattery() throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI_API_KEY no configurada. No se pueden generar preguntas con IA.");
        }

        List<String> allLetters = new ArrayList<>(AlphabetMap.MAP.values());

        Map<String, List<Question>> byLetter = new LinkedHashMap<>();
        for (String letter : allLetters) {
            byLetter.put(normalizeLetter(letter), new ArrayList<>());
        }

        for (int i = 0; i < allLetters.size(); i += batchSize) {
            List<String> batch = allLetters.subList(i, Math.min(i + batchSize, allLetters.size()));
            completeBatch(batch, byLetter);
        }

        List<Question> allQuestions = new ArrayList<>();
        for (String letter : allLetters) {
            allQuestions.addAll(byLetter.get(normalizeLetter(letter)));
        }

        QuestionList result = new QuestionList(allQuestions, allQuestions.size());
        log.info("Batería completa generada: {} preguntas", allQuestions.size());

        for (String letter : allLetters) {
            int count = byLetter.get(normalizeLetter(letter)).size();
            if (count < questionsPerLetter) {
                log.warn("La letra '{}' quedó incompleta: {}/{}", letter, count, questionsPerLetter);
            }
        }

        return result;
    }

    private void completeBatch(List<String> originalBatch, Map<String, List<Question>> byLetter) throws Exception {
        List<String> missingLetters = originalBatch.stream()
                .map(this::normalizeLetter)
                .collect(Collectors.toCollection(ArrayList::new));

        int attempt = 1;

        while (!missingLetters.isEmpty() && attempt <= maxBatchAttempts) {
            log.info("Generando lote intento {}/{} para letras: {}", attempt, maxBatchAttempts, missingLetters);

            String responseBody;
            try {
                responseBody = callAI(missingLetters);
            } catch (Exception e) {
                log.error("No se pudo llamar a la IA para el lote {}: {}", missingLetters, e.getMessage());
                attempt++;
                continue;
            }

            List<Question> parsed;
            try {
                parsed = parseAIResponse(responseBody, missingLetters);
            } catch (Exception e) {
                log.error("Error parseando respuesta de IA para letras {}: {}", missingLetters, e.getMessage());
                attempt++;
                continue;
            }

            int accepted = 0;

            for (Question q : parsed) {
                String letter = normalizeLetter(q.getQuestionLetter());
                List<Question> bucket = byLetter.get(letter);
                if (bucket == null || bucket.size() >= questionsPerLetter) {
                    continue;
                }
                if (isDuplicate(bucket, q)) {
                    continue;
                }

                bucket.add(q);
                accepted++;
            }

            missingLetters = originalBatch.stream()
                    .map(this::normalizeLetter)
                    .filter(letter -> byLetter.get(letter).size() < questionsPerLetter)
                    .collect(Collectors.toCollection(ArrayList::new));

            log.info("Intento {} completado. Aceptadas: {}. Letras pendientes: {}", attempt, accepted, missingLetters);
            attempt++;
        }

        if (!missingLetters.isEmpty()) {
            log.info("Recuperando letras individualmente para: {}", missingLetters);
            for (String letter : new ArrayList<>(missingLetters)) {
                int singleAttempt = 1;
                while (byLetter.get(letter).size() < questionsPerLetter && singleAttempt <= maxBatchAttempts) {
                    log.info("Generando letra individual intento {}/{} para letra: {}", singleAttempt, maxBatchAttempts, letter);
                    try {
                        String responseBody = callAI(List.of(letter));
                        List<Question> parsed = parseAIResponse(responseBody, List.of(letter));
                        for (Question q : parsed) {
                            String normalizedLetter = normalizeLetter(q.getQuestionLetter());
                            if (!letter.equals(normalizedLetter)) {
                                continue;
                            }
                            List<Question> bucket = byLetter.get(normalizedLetter);
                            if (bucket == null || bucket.size() >= questionsPerLetter) {
                                continue;
                            }
                            if (isDuplicate(bucket, q)) {
                                continue;
                            }
                            bucket.add(q);
                        }
                    } catch (Exception e) {
                        log.error("Fallo generación individual para letra {}: {}", letter, e.getMessage());
                    }
                    singleAttempt++;
                }
                missingLetters = originalBatch.stream()
                        .map(this::normalizeLetter)
                        .filter(l -> byLetter.get(l).size() < questionsPerLetter)
                        .collect(Collectors.toCollection(ArrayList::new));
                if (!missingLetters.contains(letter)) {
                    log.info("Letra {} completada en recuperación individual.", letter);
                }
            }
            log.info("Recuperación individual completada. Letras todavía pendientes: {}", missingLetters);
        }
    }

    private String callAI(List<String> letters) throws Exception {
        String prompt = buildPrompt(letters);
        String requestBody = buildRequestBody(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", appUrl)
                .header("X-Title", appName)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        log.info("HTTP status IA: {}", response.statusCode());

        if (response.statusCode() != 200) {
            log.error("Error de API IA (status {}): {}", response.statusCode(), response.body());
            throw new RuntimeException("Error llamando a la API de IA: HTTP " + response.statusCode());
        }

        return response.body();
    }

    private String buildPrompt(List<String> letters) {
        StringBuilder sb = new StringBuilder();

        sb.append("Eres un generador experto del rosco de Pasapalabra.\n");
        sb.append("Debes generar exactamente ").append(questionsPerLetter)
                .append(" preguntas por cada letra indicada.\n\n");

        sb.append("IMPORTANTE SOBRE EL ESTILO:\n");
        sb.append("- Para letras normales, prioriza pistas del tipo 'Con la X: ...'\n");
        sb.append("- Para letras difíciles (K, W, X, Y, Ñ), puedes priorizar 'Contiene la X: ...' si así la pista es más natural.\n\n");

        sb.append("REGLAS OBLIGATORIAS:\n");
        sb.append("1. Responde SOLO con JSON válido.\n");
        sb.append("2. Cada objeto debe tener EXACTAMENTE estos campos: questionLetter, questionText, questionResponsesList, correctQuestionIndex, questionStatus, questionLevel, userResponseRecorded.\n");
        sb.append("3. Cada pregunta debe tener exactamente 4 respuestas.\n");
        sb.append("4. Solo una respuesta es correcta.\n");
        sb.append("5. questionStatus siempre 'init'.\n");
        sb.append("6. userResponseRecorded siempre 'init'.\n");
        sb.append("7. questionLevel debe ser 'easy', 'medium' o 'hard'.\n");
        sb.append("8. questionText debe ser una pista estilo rosco.\n");
        sb.append("9. Si la pista empieza por 'Con la X:', la respuesta correcta debe EMPEZAR por X.\n");
        sb.append("10. Si la pista empieza por 'Contiene la X:', la respuesta correcta debe CONTENER X pero no empezar por X.\n");
        sb.append("11. Las respuestas incorrectas deben ser plausibles, pero incorrectas.\n");
        sb.append("12. Las respuestas incorrectas NO deben cumplir la regla de la letra indicada.\n");
        sb.append("13. Usa solo palabras reales, comunes y reconocibles en español con acentos y ñ cuando correspondan.\n");
        sb.append("14. No uses palabras inventadas, arcaicas, artificiales o extremadamente raras.\n");
        sb.append("15. No uses definiciones ambiguas ni respuestas que no encajen claramente con la pista.\n");
        sb.append("16. Evita respuestas absurdas o discutibles.\n");
        sb.append("17. No repitas preguntas ni respuestas.\n");
        sb.append("18. questionText máximo 128 caracteres.\n");
        sb.append("19. Si no puedes generar una pregunta buena para una letra, omítela.\n\n");

        sb.append("EJEMPLOS BUENOS:\n");
        sb.append("- 'Con la H: ciencia que estudia la sangre' -> 'Hematología'\n");
        sb.append("- 'Contiene la W: programa de radio o televisión' -> 'Show'\n\n");

        sb.append("FORMATO DE SALIDA EXACTO:\n");
        sb.append("{\n");
        sb.append("  \"questionList\": [\n");
        sb.append("    {\n");
        sb.append("      \"questionLetter\": \"h\",\n");
        sb.append("      \"questionText\": \"Con la H: ciencia que estudia la sangre\",\n");
        sb.append("      \"questionResponsesList\": [\"Hematología\", \"Biología\", \"Anatomía\", \"Cardiología\"],\n");
        sb.append("      \"correctQuestionIndex\": 0,\n");
        sb.append("      \"questionStatus\": \"init\",\n");
        sb.append("      \"questionLevel\": \"medium\",\n");
        sb.append("      \"userResponseRecorded\": \"init\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");

        sb.append("Genera preguntas para estas letras: ").append(letters).append("\n");
        sb.append("No añadas explicaciones, markdown ni comentarios.");

        return sb.toString();
    }

    private String buildRequestBody(String prompt) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("temperature", 0.1);
        body.put("max_tokens", 4000);

        var messages = new ArrayList<Map<String, String>>();

        var systemMsg = new LinkedHashMap<String, String>();
        systemMsg.put("role", "system");
        systemMsg.put("content",
                "Eres un generador experto del rosco de Pasapalabra. " +
                        "Responde SOLO con JSON válido, sin texto adicional.");
        messages.add(systemMsg);

        var userMsg = new LinkedHashMap<String, String>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        body.put("messages", messages);
        return mapper.writeValueAsString(body);
    }

    private List<Question> parseAIResponse(String responseBody, List<String> expectedLetters) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        content = sanitizeContent(content);

        log.info("Respuesta cruda de la IA:\n{}", preview(content, DEFAULT_MAX_PARSE_LOG_CHARS));

        JsonNode questionsNode = mapper.readTree(content);
        JsonNode listNode = null;

        if (questionsNode.isObject() && questionsNode.has("questionList") && questionsNode.get("questionList").isArray()) {
            listNode = questionsNode.get("questionList");
        } else if (questionsNode.isArray()) {
            listNode = questionsNode;
        }

        if (listNode == null || !listNode.isArray()) {
            throw new RuntimeException("La respuesta de la IA no contiene un array de preguntas válido");
        }

        Set<String> expectedSet = expectedLetters.stream()
                .map(this::normalizeLetter)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Question> questions = new ArrayList<>();

        for (JsonNode qNode : listNode) {
            try {
                String letter = normalizeLetter(qNode.path("questionLetter").asText(""));
                String text = qNode.path("questionText").asText("").trim();
                int correctIndex = qNode.path("correctQuestionIndex").asInt(-1);
                String level = qNode.path("questionLevel").asText("medium");

                List<String> responses = new ArrayList<>();
                JsonNode respNode = qNode.path("questionResponsesList");
                if (respNode.isArray()) {
                    for (JsonNode r : respNode) {
                        responses.add(r.asText("").trim());
                    }
                }

                if (letter.isBlank() || text.isBlank() || responses.size() != 4 || correctIndex < 0 || correctIndex > 3) {
                    continue;
                }

                if (!expectedSet.contains(letter)) {
                    continue;
                }

                String correctAnswer = responses.get(correctIndex);

                if (isBannedAnswer(correctAnswer)) {
                    continue;
                }

                if (!isRoscoRuleSatisfied(text, correctAnswer, letter)) {
                    continue;
                }

                if (wrongOptionsBreakRule(text, responses, correctIndex, letter)) {
                    continue;
                }

                if (!looksReasonableQuestionText(text)) {
                    continue;
                }

                if (!looksReasonableAnswer(correctAnswer)) {
                    continue;
                }

                if (hasDuplicateResponses(responses)) {
                    continue;
                }

                QuestionLevel qLevel;
                try {
                    qLevel = QuestionLevel.valueOf(level.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    qLevel = QuestionLevel.MEDIUM;
                }

                questions.add(new Question(
                        text,
                        responses,
                        correctIndex,
                        QuestionStatus.INIT,
                        qLevel,
                        letter,
                        "init"
                ));
            } catch (Exception e) {
                log.warn("Error parseando pregunta IA: {}", e.getMessage());
            }
        }

        log.info("Parseadas {} preguntas válidas de la IA para letras {}", questions.size(), expectedLetters);
        return questions;
    }

    public void saveToFile(QuestionList questions, String filePath) throws Exception {
        String json = mapper.writeValueAsString(questions);
        Files.writeString(Path.of(filePath), json, StandardCharsets.UTF_8);
        log.info("Preguntas guardadas en: {}", filePath);
    }

    private boolean isDuplicate(List<Question> existing, Question candidate) {
        String normalizedText = normalizeFreeText(candidate.getQuestionText());

        for (Question q : existing) {
            if (normalizeFreeText(q.getQuestionText()).equals(normalizedText)) {
                return true;
            }

            String existingCorrect = q.getQuestionResponsesList().get(q.getCorrectQuestionIndex());
            String candidateCorrect = candidate.getQuestionResponsesList().get(candidate.getCorrectQuestionIndex());

            if (normalizeFreeText(existingCorrect).equals(normalizeFreeText(candidateCorrect))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDuplicateResponses(List<String> responses) {
        Set<String> set = responses.stream().map(this::normalizeFreeText).collect(Collectors.toSet());
        return set.size() != responses.size();
    }

    private boolean isRoscoRuleSatisfied(String questionText, String correctAnswer, String expectedLetter) {
        String text = normalizeFreeText(questionText);

        if (text.startsWith("con la ")) {
            return startsWithLetter(correctAnswer, expectedLetter);
        }

        if (text.startsWith("contiene la ")) {
            return containsLetter(correctAnswer, expectedLetter) && !startsWithLetter(correctAnswer, expectedLetter);
        }

        if (preferContainsMode(expectedLetter)) {
            return containsLetter(correctAnswer, expectedLetter);
        }

        return startsWithLetter(correctAnswer, expectedLetter);
    }

    private boolean wrongOptionsBreakRule(String questionText, List<String> responses, int correctIndex, String expectedLetter) {
        String text = normalizeFreeText(questionText);
        boolean conLa = text.startsWith("con la ");
        boolean contieneLa = text.startsWith("contiene la ");

        for (int i = 0; i < responses.size(); i++) {
            if (i == correctIndex) continue;

            String option = responses.get(i);

            if (conLa && startsWithLetter(option, expectedLetter)) {
                return true;
            }

            if (contieneLa && containsLetter(option, expectedLetter)) {
                return true;
            }

            if (!conLa && !contieneLa) {
                if (preferContainsMode(expectedLetter)) {
                    if (containsLetter(option, expectedLetter)) return true;
                } else {
                    if (startsWithLetter(option, expectedLetter)) return true;
                }
            }
        }

        return false;
    }

    private boolean preferContainsMode(String letter) {
        return DIFFICULT_LETTERS.contains(normalizeLetter(letter));
    }

    private boolean isBannedAnswer(String answer) {
        return BANNED_ANSWERS.contains(normalizeFreeText(answer));
    }

    private boolean looksReasonableQuestionText(String text) {
        String normalized = normalizeFreeText(text);

        if (!(normalized.startsWith("con la ") || normalized.startsWith("contiene la "))) {
            return false;
        }

        return normalized.length() >= 12 && normalized.length() <= 128;
    }

    private boolean looksReasonableAnswer(String answer) {
        String normalized = normalizeFreeText(answer);

        if (normalized.isBlank()) return false;
        if (normalized.length() < 2 || normalized.length() > 30) return false;
        if (normalized.matches(".*\\d.*")) return false;
        if (normalized.contains("?") || normalized.contains("¿") || normalized.contains("!")) return false;

        return true;
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

    private String normalizeLetter(String letter) {
        if (letter == null) return "";
        String trimmed = letter.trim().toLowerCase(Locale.ROOT);
        if ("ñ".equals(trimmed)) return "ñ";
        return normalizeFreeText(trimmed);
    }

    private String normalizeFreeText(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private boolean startsWithLetter(String word, String letter) {
        if (word == null || letter == null) return false;

        String rawWord = word.trim().toLowerCase(Locale.ROOT);
        String normalizedLetter = normalizeLetter(letter);

        if (rawWord.isBlank() || normalizedLetter.isBlank()) return false;

        if ("ñ".equals(normalizedLetter)) {
            return rawWord.startsWith("ñ");
        }

        return normalizeFreeText(rawWord).startsWith(normalizedLetter);
    }

    private boolean containsLetter(String word, String letter) {
        if (word == null || letter == null) return false;

        String rawWord = word.trim().toLowerCase(Locale.ROOT);
        String normalizedLetter = normalizeLetter(letter);

        if (rawWord.isBlank() || normalizedLetter.isBlank()) return false;

        if ("ñ".equals(normalizedLetter)) {
            return rawWord.contains("ñ");
        }

        return normalizeFreeText(rawWord).contains(normalizedLetter);
    }

    private String preview(String text, int maxLen) {
        if (text == null) return "";
        return text.substring(0, Math.min(text.length(), maxLen));
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
}