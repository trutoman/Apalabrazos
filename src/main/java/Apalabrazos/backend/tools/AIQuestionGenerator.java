package Apalabrazos.backend.tools;

import Apalabrazos.backend.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera baterías de preguntas para el juego Apalabrazos usando una IA
 * a través de OpenRouter (API compatible con OpenAI).
 * Cada batería cubre las 27 letras del abecedario español (incluyendo ñ).
 *
 * Variables de entorno:
 * - AI_API_KEY       → API key de OpenRouter (obligatoria para generar)
 * - AI_API_URL       → URL base de la API (default: https://openrouter.ai/api/v1/chat/completions)
 * - AI_MODEL         → Modelo a usar (default: openai/gpt-4o-mini)
 * - AI_QUESTIONS_PER_LETTER → Preguntas por letra (default: 3)
 * - AI_APP_NAME      → Nombre de la app para OpenRouter (default: Apalabrazos)
 * - AI_APP_URL       → URL de la app para OpenRouter (default: https://github.com/Apalabrazos)
 */
public class AIQuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionGenerator.class);

    private static final String DEFAULT_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String DEFAULT_MODEL = "z-ai/glm-4.5-air:free";
    private static final int DEFAULT_QUESTIONS_PER_LETTER = 3;
    private static final String DEFAULT_APP_NAME = "Apalabrazos";
    private static final String DEFAULT_APP_URL = "https://github.com/Apalabrazos";

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final int questionsPerLetter;
    private final String appName;
    private final String appUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public AIQuestionGenerator() {
        this.apiKey = readEnv("AI_API_KEY", "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");
        this.apiUrl = readEnv("AI_API_URL", DEFAULT_API_URL);
        this.model = readEnv("AI_MODEL", DEFAULT_MODEL);
        this.questionsPerLetter = readEnvInt("AI_QUESTIONS_PER_LETTER", DEFAULT_QUESTIONS_PER_LETTER);
        this.appName = readEnv("AI_APP_NAME", DEFAULT_APP_NAME);
        this.appUrl = readEnv("AI_APP_URL", DEFAULT_APP_URL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Genera una batería completa de preguntas (una por cada letra del abecedario español).
     *
     * @return QuestionList con las preguntas generadas por la IA
     * @throws Exception si la llamada a la IA falla
     */
    public QuestionList generateFullBattery() throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI_API_KEY no configurada. No se pueden generar preguntas con IA.");
        }

        log.info("Generando batería completa de preguntas con IA (modelo: {})", model);

        // Generar en bloques para no exceder límites de tokens
        // Dividimos el abecedario en bloques de 5 letras
        List<String> allLetters = new ArrayList<>(AlphabetMap.MAP.values());
        List<Question> allQuestions = new ArrayList<>();

        int batchSize = 5;
        for (int i = 0; i < allLetters.size(); i += batchSize) {
            List<String> batch = allLetters.subList(i, Math.min(i + batchSize, allLetters.size()));
            log.info("Generando preguntas para letras: {}", batch);
            List<Question> batchQuestions = generateBatch(batch);
            allQuestions.addAll(batchQuestions);
        }

        QuestionList result = new QuestionList(allQuestions, allQuestions.size());
        log.info("Batería completa generada: {} preguntas", allQuestions.size());
        return result;
    }

    /**
     * Genera preguntas para un lote de letras.
     */
    private List<Question> generateBatch(List<String> letters) throws Exception {
        String prompt = buildPrompt(letters);

        String requestBody = buildRequestBody(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", appUrl)
                .header("X-Title", appName)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120))
                .build();

        log.debug("Enviando petición a IA para letras: {}", letters);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Error de API IA (status {}): {}", response.statusCode(), response.body());
            throw new RuntimeException("Error llamando a la API de IA: HTTP " + response.statusCode());
        }

        return parseAIResponse(response.body(), letters);
    }

    /**
     * Construye el prompt del sistema para la IA.
     */
    private String buildPrompt(List<String> letters) {
        StringBuilder sb = new StringBuilder();
        sb.append("Eres un generador de preguntas para el juego de mesa 'Pasa Palabra' (tipo Rosco). ");
        sb.append("Debes generar exactamente ").append(questionsPerLetter).append(" preguntas por cada letra indicada. ");
        sb.append("Las preguntas deben ser de cultura general en español, variadas y adecuadas para un juego de mesa familiar.\n\n");
        sb.append("REGLAS ESTRICTAS:\n");
        sb.append("1. Cada pregunta debe tener exactamente 4 opciones de respuesta.\n");
        sb.append("2. Solo una opción es correcta (indicada por correctQuestionIndex: 0-3).\n");
        sb.append("3. La respuesta correcta DEBE empezar con la letra indicada en questionLetter.\n");
        sb.append("4. Las 3 respuestas incorrectas deben empezar con la misma letra de la respuesta correcta.\n");
        sb.append("5. Los niveles de dificultad: \"easy\", \"medium\" o \"hard\". Mezcla los niveles.\n");
        sb.append("6. questionStatus siempre debe ser \"init\".\n");
        sb.append("7. userResponseRecorded siempre debe ser \"init\".\n");
        sb.append("8. questionText máximo 128 caracteres.\n\n");
        sb.append("FORMATO DE SALIDA: Responde SOLO con un JSON válido, sin texto adicional, con esta estructura:\n");
        sb.append("{\n");
        sb.append("  \"questionList\": [\n");
        sb.append("    {\n");
        sb.append("      \"questionLetter\": \"a\",\n");
        sb.append("      \"questionText\": \"¿Cuál de los siguientes es un continente?\",\n");
        sb.append("      \"questionResponsesList\": [\"África\", \"Argentina\", \"Alicante\", \"Asturias\"],\n");
        sb.append("      \"correctQuestionIndex\": 0,\n");
        sb.append("      \"questionStatus\": \"init\",\n");
        sb.append("      \"questionLevel\": \"easy\",\n");
        sb.append("      \"userResponseRecorded\": \"init\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("Genera las preguntas para las siguientes letras: ").append(letters);
        return sb.toString();
    }

    /**
     * Construye el body de la petición HTTP a la API de OpenAI.
     */
    private String buildRequestBody(String prompt) throws Exception {
        // Build JSON manually to avoid extra dependencies
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", 4000);

        var messages = new ArrayList<java.util.Map<String, String>>();
        var systemMsg = new java.util.LinkedHashMap<String, String>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "Eres un generador de preguntas de cultura general para el juego Pasa Palabra. Responde SOLO con JSON válido, sin texto adicional ni bloques de código markdown.");
        messages.add(systemMsg);

        var userMsg = new java.util.LinkedHashMap<String, String>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        body.put("messages", messages);

        return mapper.writeValueAsString(body);
    }

    /**
     * Parsea la respuesta de la IA y la convierte en objetos Question.
     */
    private List<Question> parseAIResponse(String responseBody, List<String> expectedLetters) throws Exception {
        JsonNode root = mapper.readTree(responseBody);

        // Extraer el contenido del mensaje de la respuesta
        String content = root.path("choices").path(0).path("message").path("content").asText("");

        // Limpiar posibles bloques de código markdown
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        content = content.trim();
        // DEBUG: ver qué devuelve realmente la IA
        log.info("Respuesta cruda de la IA:\n{}", content);
        
        JsonNode questionsNode = mapper.readTree(content);
        JsonNode listNode = null;

        // Caso 1: objeto con questionList
        if (questionsNode.isObject() && questionsNode.has("questionList") && questionsNode.get("questionList").isArray()) {
            listNode = questionsNode.get("questionList");
        }
        // Caso 2: objeto con questions
        else if (questionsNode.isObject() && questionsNode.has("questions") && questionsNode.get("questions").isArray()) {
            listNode = questionsNode.get("questions");
        }
        // Caso 3: array directo
        else if (questionsNode.isArray()) {
            listNode = questionsNode;
        }

        if (listNode == null || !listNode.isArray()) {
            log.error("Contenido recibido de la IA:\n{}", content);
            throw new RuntimeException("La respuesta de la IA no contiene un array de preguntas válido");
        }

        List<Question> questions = new ArrayList<>();
        for (JsonNode qNode : listNode) {
            try {
                String letter = qNode.path("questionLetter").asText("");
                String text = qNode.path("questionText").asText("");
                int correctIndex = qNode.path("correctQuestionIndex").asInt(-1);
                String level = qNode.path("questionLevel").asText("medium");

                List<String> responses = new ArrayList<>();
                JsonNode respNode = qNode.path("questionResponsesList");
                if (respNode.isArray()) {
                    for (JsonNode r : respNode) {
                        responses.add(r.asText());
                    }
                }

                // Validación básica
                if (letter.isBlank() || text.isBlank() || responses.size() != 4 || correctIndex < 0 || correctIndex > 3) {
                    log.warn("Pregunta inválida descartada: letter={}, text={}", letter, text.substring(0, Math.min(text.length(), 50)));
                    continue;
                }

                QuestionLevel qLevel;
                try {
                    qLevel = QuestionLevel.valueOf(level.toUpperCase());
                } catch (IllegalArgumentException e) {
                    qLevel = QuestionLevel.MEDIUM;
                }

                Question q = new Question(text, responses, correctIndex, QuestionStatus.INIT, qLevel, letter.toLowerCase(), "init");
                questions.add(q);
            } catch (Exception e) {
                log.warn("Error parseando pregunta de IA: {}", e.getMessage());
            }
        }

        log.info("Parseadas {} preguntas válidas de la IA para letras {}", questions.size(), expectedLetters);
        return questions;
    }

    /**
     * Guarda las preguntas generadas en un archivo JSON.
     *
     * @param questions Lista de preguntas
     * @param filePath  Ruta del archivo de salida
     * @throws Exception si falla la escritura
     */
    public void saveToFile(QuestionList questions, String filePath) throws Exception {
        mapper.writeValue(new java.io.File(filePath), questions);
        log.info("Preguntas guardadas en: {}", filePath);
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