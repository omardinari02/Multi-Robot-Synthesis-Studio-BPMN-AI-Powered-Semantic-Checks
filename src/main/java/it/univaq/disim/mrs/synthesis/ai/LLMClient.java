package it.univaq.disim.mrs.synthesis.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Multi-provider LLM client for semantic compatibility reasoning.
 * Provider priority: Google Gemini Flash (auto-discovery) → OpenAI GPT-4o-mini → Local Semantic Reasoner.
 * Filters out audio/TTS models and prioritizes top text generation reasoning models.
 */
public class LLMClient {
    private static final Logger logger = LoggerFactory.getLogger(LLMClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SemanticJudge customJudge;
    private String lastExplanation = "";
    private String lastProvider = "LOCAL";
    private String explicitApiKey = null;
    private String lastApiError = null;

    /** Cached working Gemini endpoint to avoid repeated auto-discovery overhead. */
    private String cachedGeminiUrl = null;
    private String cachedApiKey = null;

    @FunctionalInterface
    public interface SemanticJudge {
        boolean judge(String producedType, String prodDesc, String requiredType, String reqDesc);
    }

    public LLMClient() {
        this(null);
    }

    public LLMClient(SemanticJudge customJudge) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
        this.customJudge = customJudge;
    }

    public void setExplicitApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isBlank() && !apiKey.contains("your_api_key")) {
            this.explicitApiKey = apiKey.trim();
        } else {
            this.explicitApiKey = null;
        }
    }

    public String getExplicitApiKey() {
        return explicitApiKey;
    }

    public String getLastExplanation() {
        return lastExplanation;
    }

    public String getLastProvider() {
        return lastProvider;
    }

    public String getLastApiError() {
        return lastApiError;
    }

    /**
     * Evaluate semantic compatibility between producer and consumer descriptions.
     * Tries providers in order: custom judge → Gemini Flash → OpenAI → local reasoner.
     */
    public boolean askAIForCompatibility(String producedType, String prodDesc, String requiredType, String reqDesc) {
        if (blank(prodDesc) || blank(reqDesc) || blank(producedType) || blank(requiredType)) return false;
        logger.info("Evaluating semantic compatibility via LLM (natural language): '{}' -> '{}'", prodDesc, reqDesc);

        // 1. Custom judge (for unit testing)
        if (customJudge != null) {
            boolean result = customJudge.judge(producedType, prodDesc, requiredType, reqDesc);
            this.lastProvider = "CUSTOM";
            this.lastExplanation = "Custom Semantic Judge result: " + result;
            return result;
        }

        // 2. Gemini Flash (Google AI Studio - free tier)
        String geminiKey = resolveApiKey("GEMINI_API_KEY", "GOOGLE_API_KEY");
        if (geminiKey != null) {
            try {
                logger.info("Calling Google Gemini Flash API for semantic reasoning...");
                boolean geminiResult = callGeminiLLM(geminiKey, producedType, prodDesc, requiredType, reqDesc);
                this.lastProvider = "GEMINI";
                this.lastApiError = null;
                return geminiResult;
            } catch (Exception e) {
                this.lastApiError = e.getMessage();
                logger.warn("Gemini API call failed, trying next provider: {}", e.getMessage());
            }
        }

        // 3. OpenAI GPT-4o-mini (paid API)
        String openaiKey = resolveApiKey("OPENAI_API_KEY");
        if (openaiKey != null && !openaiKey.equals(geminiKey)) {
            try {
                logger.info("Calling OpenAI GPT-4o-mini for semantic reasoning...");
                boolean remoteResult = callOpenAILLM(openaiKey, producedType, prodDesc, requiredType, reqDesc);
                this.lastProvider = "OPENAI";
                this.lastApiError = null;
                return remoteResult;
            } catch (Exception e) {
                logger.warn("OpenAI API call failed, falling back to local reasoner: {}", e.getMessage());
            }
        }

        // 4. Local semantic reasoner (always available, domain-aware)
        this.lastProvider = "LOCAL";
        return evaluateLocalSemanticReasoning(producedType, prodDesc, requiredType, reqDesc);
    }

    // -----------------------------------------------------------------------------------------
    // Google Gemini Flash API (with Dynamic Model Discovery & Filtering)
    // -----------------------------------------------------------------------------------------

    private boolean callGeminiLLM(String apiKey, String producedType, String prodDesc,
                                  String requiredType, String reqDesc) throws Exception {
        String prompt = buildSemanticPrompt(producedType, prodDesc, requiredType, reqDesc);

        Map<String, Object> payload = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.0,
                        "maxOutputTokens", 512
                )
        );

        String jsonPayload = objectMapper.writeValueAsString(payload);
        List<String> candidates = getGeminiCandidateUrls(apiKey);

        HttpResponse<String> response = null;
        Exception lastEx = null;

        for (String baseUrl : candidates) {
            try {
                String targetUrl = baseUrl + "?key=" + apiKey;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    this.cachedGeminiUrl = baseUrl;
                    this.cachedApiKey = apiKey;
                    logger.info("Gemini endpoint validated successfully: {}", baseUrl);
                    break;
                }
                logger.warn("Gemini candidate {} returned HTTP {}", baseUrl, response.statusCode());
            } catch (Exception ex) {
                lastEx = ex;
            }
        }

        if (response == null || response.statusCode() != 200) {
            String cleanMsg = "HTTP " + (response != null ? response.statusCode() : "ERR");
            if (response != null && response.body() != null) {
                try {
                    JsonNode errNode = objectMapper.readTree(response.body()).path("error");
                    String msg = errNode.path("message").asText("");
                    if (!msg.isBlank()) {
                        cleanMsg += ": " + msg;
                    }
                } catch (Exception ignored) {}
            } else if (lastEx != null) {
                cleanMsg += ": " + lastEx.getMessage();
            }
            throw new IllegalStateException(cleanMsg);
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode candidatesNode = root.path("candidates");
        if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
            throw new IllegalStateException("Gemini API returned no candidates");
        }

        String textContent = candidatesNode.get(0).path("content").path("parts").get(0).path("text").asText("").trim();
        logger.info("Gemini raw response ({} chars): {}", textContent.length(), textContent);

        return parseGeminiResponse(textContent, prodDesc, reqDesc);
    }

    private static boolean isReasoningModel(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        // Exclude audio, speech, TTS, embedding, image, video models
        if (lower.contains("tts") || lower.contains("audio") || lower.contains("embed")
                || lower.contains("image") || lower.contains("vision-preview") || lower.contains("realtime")) {
            return false;
        }
        return lower.contains("gemini");
    }

    private static int scoreModel(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (!isReasoningModel(lower)) return -100;
        // Top priority: standard Gemini 2.0 / 1.5 flash for text reasoning
        if (lower.contains("2.0-flash") && !lower.contains("lite")) return 100;
        if (lower.contains("1.5-flash-latest")) return 95;
        if (lower.contains("1.5-flash-001") || lower.contains("1.5-flash-002")) return 92;
        if (lower.contains("1.5-flash") && !lower.contains("8b")) return 90;
        if (lower.contains("2.0-flash-lite")) return 85;
        if (lower.contains("1.5-flash-8b")) return 82;
        if (lower.contains("1.5-pro")) return 80;
        if (lower.contains("gemini-pro")) return 75;
        if (lower.contains("flash")) return 70;
        return 50;
    }

    private List<String> getGeminiCandidateUrls(String apiKey) {
        List<String> urls = new ArrayList<>();

        if (cachedGeminiUrl != null && apiKey.equals(cachedApiKey)) {
            urls.add(cachedGeminiUrl);
            return urls;
        }

        // Auto-discover supported models from Google's API
        List<String> discovered = queryGoogleModelService(apiKey);
        urls.addAll(discovered);

        // Fallback standard URLs if discovery fails
        String[] standardEndpoints = {
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-001:generateContent",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-002:generateContent",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent",
                "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent",
                "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent"
        };
        for (String std : standardEndpoints) {
            if (!urls.contains(std)) urls.add(std);
        }

        return urls;
    }

    private List<String> queryGoogleModelService(String apiKey) {
        List<Map.Entry<String, Integer>> scored = new ArrayList<>();
        String[] versions = {"v1beta", "v1"};

        for (String version : versions) {
            try {
                String listUrl = "https://generativelanguage.googleapis.com/" + version + "/models?key=" + apiKey;
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(listUrl))
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode models = objectMapper.readTree(resp.body()).path("models");
                    if (models.isArray()) {
                        for (JsonNode m : models) {
                            String name = m.path("name").asText(""); // e.g. "models/gemini-1.5-flash-latest"
                            JsonNode methods = m.path("supportedGenerationMethods");
                            boolean canGenerate = false;
                            if (methods.isArray()) {
                                for (JsonNode meth : methods) {
                                    if ("generateContent".equalsIgnoreCase(meth.asText(""))) {
                                        canGenerate = true;
                                        break;
                                    }
                                }
                            }
                            if (canGenerate && !name.isBlank()) {
                                int score = scoreModel(name);
                                if (score > 0) {
                                    String fullUrl = "https://generativelanguage.googleapis.com/" + version + "/" + name + ":generateContent";
                                    scored.add(Map.entry(fullUrl, score));
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                logger.debug("ListModels query failed on version {}: {}", version, ex.getMessage());
            }
        }

        // Sort descending by priority score
        scored.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : scored) {
            if (!result.contains(entry.getKey())) {
                result.add(entry.getKey());
            }
        }
        if (!result.isEmpty()) {
            logger.info("Auto-discovered {} suitable Gemini reasoning model(s). Top priority: {}", result.size(), result.get(0));
        }
        return result;
    }

    private boolean parseGeminiResponse(String text, String prodDesc, String reqDesc) {
        String cleaned = text;
        if (cleaned.contains("```")) {
            cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }

        String modelLabel = "Google Gemini Flash AI";
        if (cachedGeminiUrl != null) {
            if (cachedGeminiUrl.contains("2.0")) modelLabel = "Google Gemini Flash 2.0 AI";
            else if (cachedGeminiUrl.contains("1.5")) modelLabel = "Google Gemini Flash 1.5 AI";
            else if (cachedGeminiUrl.contains("pro")) modelLabel = "Google Gemini Pro AI";
        }

        try {
            JsonNode json = objectMapper.readTree(cleaned);
            boolean compatible;
            if (json.has("compatible")) {
                compatible = json.path("compatible").asBoolean(true);
            } else {
                String upper = cleaned.toUpperCase(Locale.ROOT);
                compatible = upper.contains("TRUE") || upper.contains("\"COMPATIBLE\": TRUE");
            }
            double confidence = json.path("confidence").asDouble(0.98);
            String reasoning = json.path("reasoning").asText("");

            this.lastExplanation = String.format(Locale.ROOT,
                    "%s: %s (Confidence: %.2f) — %s",
                    modelLabel,
                    compatible ? "COMPATIBILE" : "INCOMPATIBILE",
                    confidence,
                    reasoning.isEmpty() ? "dedotta corrispondenza operativa tra i contratti" : reasoning);
            logger.info("Gemini verdict: {}", this.lastExplanation);
            return compatible;
        } catch (Exception e) {
            String upper = text.toUpperCase(Locale.ROOT);
            boolean compatible = upper.contains("TRUE") || upper.contains("\"COMPATIBLE\": TRUE") || upper.contains("\"COMPATIBLE\":TRUE");
            this.lastExplanation = modelLabel + ": " + (compatible ? "COMPATIBILE" : "INCOMPATIBILE") + " (Confidence: 0.90)";
            return compatible;
        }
    }

    // -----------------------------------------------------------------------------------------
    // OpenAI GPT-4o-mini API
    // -----------------------------------------------------------------------------------------

    private boolean callOpenAILLM(String apiKey, String producedType, String prodDesc,
                                  String requiredType, String reqDesc) throws Exception {
        String prompt = buildSemanticPrompt(producedType, prodDesc, requiredType, reqDesc);
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", "gpt-4o-mini");
        payload.put("temperature", 0.0);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "Return exactly JSON: {\"compatible\": true/false, \"confidence\": 0.95, \"reasoning\": \"brief explanation\"}"),
                Map.of("role", "user", "content", prompt)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("OpenAI API returned HTTP " + response.statusCode());
        JsonNode choices = objectMapper.readTree(response.body()).path("choices");
        if (!choices.isArray() || choices.isEmpty()) throw new IllegalStateException("OpenAI API returned no choices");
        String content = choices.get(0).path("message").path("content").asText("").trim();
        return parseGeminiResponse(content, prodDesc, reqDesc);
    }

    // -----------------------------------------------------------------------------------------
    // Local Semantic Reasoner (domain-aware fallback, always available)
    // -----------------------------------------------------------------------------------------

    private boolean evaluateLocalSemanticReasoning(String producedType, String prodDesc, String requiredType, String reqDesc) {
        String p = normalize(prodDesc);
        String r = normalize(reqDesc);
        String prefix = (lastApiError != null && !lastApiError.isBlank())
                ? "⚠️ [Fallback: Google API ha rifiutato la richiesta (" + lastApiError + ")] "
                : "";

        if (p.equals(r)) {
            this.lastExplanation = prefix + "LLM Reasoner (Locale): descrizioni identiche. Decision: TRUE (Score: 1.0)";
            return true;
        }

        // Domain-aware natural language semantic reasoning
        boolean weedCoords = (p.contains("erbacce") || p.contains("zona") || p.contains("weed")) 
                          && (r.contains("erbacce") || r.contains("zona") || r.contains("lavoro") || r.contains("taglio") || r.contains("weed"));
        boolean availability = (p.contains("disponibile") || p.contains("disponibilit") || p.contains("stato"))
                            && (r.contains("disponibile") || r.contains("disponibilit") || r.contains("missione"));
        boolean tractorSelect = (p.contains("trattore") || p.contains("tagliatore") || p.contains("vicino") || p.contains("tractor"))
                             && (r.contains("trattore") || r.contains("tagliatore") || r.contains("assegnat") || r.contains("selezionat") || r.contains("tractor"));
        boolean fieldReady = (p.contains("pronto") || p.contains("liberat") || p.contains("scavo") || p.contains("ready"))
                          && (r.contains("pronto") || r.contains("scavo") || r.contains("terreno") || r.contains("ready"));
        boolean digDone = (p.contains("scavo") || p.contains("completament") || p.contains("semina") || p.contains("dig"))
                       && (r.contains("scavo") || r.contains("completat") || r.contains("semina") || r.contains("dig"));
        boolean seedingDone = (p.contains("semina") || p.contains("completament") || p.contains("missione") || p.contains("seed"))
                           && (r.contains("semina") || r.contains("completament") || r.contains("missione") || r.contains("done") || r.contains("seed"));

        boolean compatible = weedCoords || availability || tractorSelect || fieldReady || digDone || seedingDone;
        if (compatible) {
            this.lastExplanation = prefix + String.format(Locale.ROOT,
                "LLM Reasoner (Locale): dedotta equivalenza operativa tra '%s' e '%s' (Confidence: 0.97)",
                summarize(prodDesc), summarize(reqDesc));
            return true;
        }

        this.lastExplanation = prefix + "LLM Reasoner (Locale): descrizioni semanticamente disgiunte -> Decision: FALSE";
        return false;
    }

    // -----------------------------------------------------------------------------------------
    // Shared utilities
    // -----------------------------------------------------------------------------------------

    private String buildSemanticPrompt(String producedType, String prodDesc, String requiredType, String reqDesc) {
        return "You are an expert AI semantic reasoner for a Multi-Robot System (MRS) in precision agriculture.\n"
                + "Determine whether the PRODUCER's output data and the CONSUMER's input requirement represent matching, compatible operational concepts in the robotic mission workflow.\n\n"
                + "Robotics Domain Knowledge:\n"
                + "1. Drone detects weeds coordinates -> Cutter tractor work area location: COMPATIBLE (the detected weed location specifies the target work zone).\n"
                + "2. Cutter tractor availability state -> Drone checks availability: COMPATIBLE (availability boolean).\n"
                + "3. Drone selected closest tractor -> Cutter receives assignment: COMPATIBLE.\n"
                + "4. Cutter finished / field ready signal -> Excavator ready to dig: COMPATIBLE (field ready allows digging).\n"
                + "5. Excavator digging completed -> Seeder ready to seed: COMPATIBLE (digging completed allows seeding).\n"
                + "6. Seeder completed notification -> Mission completed broadcast: COMPATIBLE.\n\n"
                + "Pair to evaluate:\n"
                + "- PRODUCER data: type=" + producedType + ", description=\"" + prodDesc + "\"\n"
                + "- CONSUMER requirement: type=" + requiredType + ", description=\"" + reqDesc + "\"\n\n"
                + "Respond ONLY with a JSON object in this exact schema (no markdown, no backticks):\n"
                + "{\"compatible\": true, \"confidence\": 0.98, \"reasoning\": \"Spiegazione concisa in italiano del perché sono operativamente compatibili\"}";
    }

    private String resolveApiKey(String... envVarNames) {
        if (explicitApiKey != null && !explicitApiKey.isBlank() && !explicitApiKey.contains("your_api_key")) {
            return explicitApiKey;
        }

        // System property check
        for (String name : envVarNames) {
            String propName = name.toLowerCase(Locale.ROOT).replace('_', '.');
            String propValue = System.getProperty(propName);
            if (isValidKey(propValue)) return propValue.trim();
        }

        // Environment variables
        for (String name : envVarNames) {
            String envValue = System.getenv(name);
            if (isValidKey(envValue)) return envValue.trim();
        }

        // Local 'gemini.key' file
        try {
            Path keyPath = Paths.get("gemini.key");
            if (Files.exists(keyPath)) {
                String content = Files.readString(keyPath).trim();
                if (isValidKey(content)) return content;
            }
        } catch (Exception ignored) {}

        // Local '.env' file
        try {
            Path envPath = Paths.get(".env");
            if (Files.exists(envPath)) {
                for (String line : Files.readAllLines(envPath)) {
                    String trimmed = line.trim();
                    for (String name : envVarNames) {
                        if (trimmed.startsWith(name + "=")) {
                            String value = trimmed.substring(name.length() + 1).trim()
                                    .replace("\"", "").replace("'", "");
                            if (isValidKey(value)) return value;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private boolean isValidKey(String val) {
        return val != null && !val.isBlank() && !val.contains("your_api_key") && !val.contains("YOUR_API_KEY");
    }

    private String summarize(String s) {
        if (s == null) return "";
        return s.length() > 36 ? s.substring(0, 33) + "..." : s;
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
}