package it.univaq.disim.mrs.synthesis.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import it.univaq.disim.mrs.synthesis.core.MultiRobotSynthesisEngine;
import it.univaq.disim.mrs.synthesis.event.SynthesisEvent;
import it.univaq.disim.mrs.synthesis.event.SynthesisEventListener;
import it.univaq.disim.mrs.synthesis.model.MissionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP & Server-Sent Events (SSE) server for the interactive synthesis studio.
 * Zero external web framework dependencies (built-in Java SE HttpServer).
 */
public class SynthesisWebServer {
    private static final Logger logger = LoggerFactory.getLogger(SynthesisWebServer.class);
    private static final int DEFAULT_PORT = 8080;
    private static final ObjectMapper mapper = new ObjectMapper();

    private final int port;
    private HttpServer server;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(8);
    private final List<PrintWriter> sseClients = new CopyOnWriteArrayList<>();
    private final List<SynthesisEvent> eventHistory = new CopyOnWriteArrayList<>();

    private final Path bpmnDir = Paths.get("src/main/resources/bpmn");
    private final Path outputDir = Paths.get("target/output");
    private final Path ontologyFile = Paths.get("src/main/resources/taxonomy.json");
    private final Path semanticsFile = Paths.get("src/main/resources/semantics.json");
    private final Path behavioralFile = Paths.get("src/main/resources/behavioral-state.json");

    public SynthesisWebServer() {
        this(DEFAULT_PORT);
    }

    public SynthesisWebServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                try {
                    port = Integer.parseInt(arg.substring(7));
                } catch (NumberFormatException ignored) {}
            }
        }
        SynthesisWebServer webServer = new SynthesisWebServer(port);
        webServer.start();
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(threadPool);

            // Web UI handler
            server.createContext("/", new IndexHandler());

            // REST API handlers
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/models", new ModelsHandler());
            server.createContext("/api/inputs", new InputsHandler());
            server.createContext("/api/file-content", new FileContentHandler());
            server.createContext("/api/reset-inputs", new ResetInputsHandler());
            server.createContext("/api/events", new SseEventsHandler());
            server.createContext("/api/synthesize", new SynthesizeHandler());
            server.createContext("/api/upload", new UploadHandler());
            server.createContext("/api/bpmn", new BpmnDownloadHandler());
            server.createContext("/api/drawio", new DrawioDownloadHandler());

            server.start();
            logger.info("==========================================================================");
            logger.info(" MRS Synthesis Studio - Web Server avviato con successo!");
            logger.info(" Interfaccia grafica disponibile all'indirizzo: http://localhost:{}", port);
            logger.info("==========================================================================");
        } catch (IOException e) {
            logger.error("Impossibile avviare il web server sulla porta {}: {}", port, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            threadPool.shutdownNow();
            logger.info("Web Server arrestato.");
        }
    }

    private void broadcastEvent(SynthesisEvent event) {
        eventHistory.add(event);
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (Exception e) {
            json = "{\"type\":\"" + event.getType() + "\",\"message\":\"" + event.getMessage() + "\"}";
        }
        String sseData = "data: " + json + "\n\n";
        for (PrintWriter client : sseClients) {
            try {
                client.write(sseData);
                client.flush();
            } catch (Exception e) {
                sseClients.remove(client);
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // HTTP Handlers
    // -----------------------------------------------------------------------------------------

    private class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                sendJsonResponse(exchange, 404, Map.of("error", "API endpoint not found: " + path));
                return;
            }

            // Load UI html from resources or fallback
            byte[] content = loadResourceOrFile("web/index.html");
            if (content == null) {
                content = loadResourceOrFile("src/main/resources/web/index.html");
            }
            if (content == null) {
                String fallback = "<html><body><h1>MRS Synthesis Studio</h1><p>UI loading error. Check resources.</p></body></html>";
                content = fallback.getBytes(StandardCharsets.UTF_8);
            }

            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/html; charset=UTF-8");
            headers.set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("status", "UP");
            status.put("version", "2.1.0-Thesis");
            status.put("engine", "MultiRobotSynthesisEngine");
            status.put("port", port);
            status.put("historyEventCount", eventHistory.size());
            status.put("activeSseClients", sseClients.size());
            sendJsonResponse(exchange, 200, status);
        }
    }

    private class ModelsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String name = null;
                if (query != null && query.contains("name=")) {
                    name = query.split("name=")[1].split("&")[0];
                }
                if (name == null || name.isBlank() || name.equalsIgnoreCase("GlobalMission.bpmn")) {
                    sendJsonResponse(exchange, 400, Map.of("error", "Nome modello non valido o non cancellabile."));
                    return;
                }
                Path target = bpmnDir.resolve(Paths.get(name).getFileName().toString());
                if (Files.exists(target)) {
                    Files.delete(target);
                    logger.info("Modello robot cancellato: {}", target);
                    sendJsonResponse(exchange, 200, Map.of("status", "DELETED", "name", name));
                    return;
                }
                sendJsonResponse(exchange, 404, Map.of("error", "Modello non trovato: " + name));
                return;
            }

            List<Map<String, Object>> models = new ArrayList<>();
            if (Files.exists(bpmnDir)) {
                try (var stream = Files.list(bpmnDir)) {
                    stream.filter(p -> p.toString().endsWith(".bpmn")).forEach(p -> {
                        String name = p.getFileName().toString();
                        boolean isGlobal = name.toLowerCase().contains("global");
                        models.add(Map.of(
                                "name", name,
                                "robotName", name.replace(".bpmn", ""),
                                "isGlobal", isGlobal,
                                "sizeBytes", p.toFile().length()
                        ));
                    });
                }
            }
            sendJsonResponse(exchange, 200, Map.of("models", models));
        }
    }

    private class InputsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            Map<String, Object> result = new LinkedHashMap<>();

            // 1. Global Mission
            Path globalPath = bpmnDir.resolve("GlobalMission.bpmn");
            boolean globalExists = Files.exists(globalPath);
            Map<String, Object> globalInfo = new LinkedHashMap<>();
            globalInfo.put("filename", "GlobalMission.bpmn");
            globalInfo.put("exists", globalExists);
            globalInfo.put("sizeBytes", globalExists ? Files.size(globalPath) : 0);
            globalInfo.put("lastModified", globalExists ? Files.getLastModifiedTime(globalPath).toMillis() : 0);
            result.put("globalMission", globalInfo);

            // 2. Local Robots
            List<Map<String, Object>> robots = new ArrayList<>();
            if (Files.exists(bpmnDir)) {
                try (var stream = Files.list(bpmnDir)) {
                    stream.filter(p -> p.toString().endsWith(".bpmn"))
                          .filter(p -> !p.getFileName().toString().equalsIgnoreCase("GlobalMission.bpmn"))
                          .sorted()
                          .forEach(p -> {
                              String fName = p.getFileName().toString();
                              try {
                                  robots.add(Map.of(
                                          "filename", fName,
                                          "robotName", fName.replace(".bpmn", ""),
                                          "sizeBytes", Files.size(p),
                                          "lastModified", Files.getLastModifiedTime(p).toMillis()
                                  ));
                              } catch (IOException ignored) {}
                          });
                }
            }
            result.put("robots", robots);

            // 3. Configurations
            Map<String, Object> configs = new LinkedHashMap<>();
            configs.put("semantics", fileMeta("semantics.json", semanticsFile, "Specifiche I/O e descrizioni semantiche"));
            configs.put("taxonomy", fileMeta("taxonomy.json", ontologyFile, "Ontologia formale OWL e tassonomia tipi"));
            configs.put("behavioral", fileMeta("behavioral-state.json", behavioralFile, "Stato comportamentale iniziale LTS"));
            result.put("configs", configs);

            sendJsonResponse(exchange, 200, result);
        }

        private Map<String, Object> fileMeta(String defaultName, Path path, String description) {
            boolean exists = Files.exists(path);
            long size = 0;
            long modified = 0;
            if (exists) {
                try {
                    size = Files.size(path);
                    modified = Files.getLastModifiedTime(path).toMillis();
                } catch (IOException ignored) {}
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("filename", defaultName);
            map.put("target", path.getFileName().toString());
            map.put("exists", exists);
            map.put("sizeBytes", size);
            map.put("lastModified", modified);
            map.put("description", description);
            return map;
        }
    }

    private class SseEventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/event-stream");
            headers.set("Cache-Control", "no-cache");
            headers.set("Connection", "keep-alive");
            headers.set("Access-Control-Allow-Origin", "*");

            exchange.sendResponseHeaders(200, 0); // chunked
            OutputStream os = exchange.getResponseBody();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
            sseClients.add(writer);

            // Replay recent history to new client so they don't miss past state
            for (SynthesisEvent pastEvent : eventHistory) {
                try {
                    writer.write("data: " + mapper.writeValueAsString(pastEvent) + "\n\n");
                    writer.flush();
                } catch (Exception ignored) {}
            }
        }
    }

    private class SynthesizeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            int delay = 0;
            String matcherType = "HYBRID";
            String apiKeyParam = exchange.getRequestHeaders().getFirst("X-Api-Key");
            if (query != null) {
                if (query.contains("delay=")) {
                    try {
                        delay = Integer.parseInt(query.split("delay=")[1].split("&")[0]);
                    } catch (Exception ignored) {}
                } else if (query.contains("mode=demo")) {
                    delay = 800; // 800ms didattici per fase in modalità presentazione
                }
                if (query.contains("matcher=")) {
                    try {
                        matcherType = query.split("matcher=")[1].split("&")[0];
                    } catch (Exception ignored) {}
                }
                if (query.contains("apiKey=")) {
                    try {
                        apiKeyParam = java.net.URLDecoder.decode(query.split("apiKey=")[1].split("&")[0], StandardCharsets.UTF_8);
                    } catch (Exception ignored) {}
                }
            }
            final int stepDelay = delay;
            final String selectedMatcher = matcherType;
            final String passedApiKey = apiKeyParam;

            eventHistory.clear();
            broadcastEvent(SynthesisEvent.info(SynthesisEvent.Type.PIPELINE_STARTED, 0, "Avvio",
                    "Richiesta di sintesi ricevuta dall'interfaccia interattiva " 
                    + (stepDelay > 0 ? "[Modalità Demo: " + stepDelay + "ms]" : "[Modalità Turbo]")
                    + " [Matcher: " + selectedMatcher + "]"
                    + (passedApiKey != null && !passedApiKey.isBlank() ? " [API Key fornita]" : "")));

            threadPool.submit(() -> {
                try {
                    it.univaq.disim.mrs.synthesis.semantic.OntologyManager ontologyMgr = new it.univaq.disim.mrs.synthesis.semantic.OntologyManager();
                    it.univaq.disim.mrs.synthesis.ai.AISemanticMatcher customMatcher = new it.univaq.disim.mrs.synthesis.ai.AISemanticMatcher(ontologyMgr);
                    customMatcher.setMode(selectedMatcher);
                    if (passedApiKey != null && !passedApiKey.isBlank()) {
                        customMatcher.setExplicitApiKey(passedApiKey);
                    }

                    MultiRobotSynthesisEngine engine = new MultiRobotSynthesisEngine(customMatcher);
                    engine.setEventListener(event -> {
                        SynthesisWebServer.this.broadcastEvent(event);
                        if (stepDelay > 0) {
                            // Introduce un breve delay didattico tra le fasi per consentire alla commissione di seguire a video
                            if (event.getType() == SynthesisEvent.Type.STEP_STARTED ||
                                event.getType() == SynthesisEvent.Type.SYNTACTIC_CHECK ||
                                event.getType() == SynthesisEvent.Type.SEMANTIC_CHECK ||
                                event.getType() == SynthesisEvent.Type.BEHAVIORAL_CHECK ||
                                event.getType() == SynthesisEvent.Type.PLAN_CREATED) {
                                try {
                                    Thread.sleep(stepDelay);
                                } catch (InterruptedException ignored) {}
                            }
                        }
                    });

                    File globalFile = bpmnDir.resolve("GlobalMission.bpmn").toFile();
                    List<File> locals = new ArrayList<>();
                    if (Files.exists(bpmnDir)) {
                        try (var stream = Files.list(bpmnDir)) {
                            stream.filter(p -> p.toString().endsWith(".bpmn"))
                                  .filter(p -> !p.getFileName().toString().equalsIgnoreCase("GlobalMission.bpmn"))
                                  .sorted()
                                  .forEach(p -> locals.add(p.toFile()));
                        }
                    }

                    MissionContext context = engine.runSynthesisPipeline(
                            locals, globalFile, ontologyFile.toFile(), semanticsFile.toFile(),
                            behavioralFile.toFile(), false
                    );
                    logger.info("Sintesi via Web UI completata con successo: {} interazioni.", context.getValidatedMatches().size());
                } catch (Exception e) {
                    logger.error("Errore durante la sintesi via Web UI: {}", e.getMessage(), e);
                    broadcastEvent(new SynthesisEvent(SynthesisEvent.Type.DIAGNOSTIC, 0, "Errore",
                            "Errore durante l'esecuzione: " + e.getMessage(), null, null, null, false, e.toString()));
                }
            });

            sendJsonResponse(exchange, 202, Map.of(
                    "status", "STARTED",
                    "message", "Sintesi avviata in background. Iscriviti a /api/events per gli aggiornamenti live."
            ));
        }
    }

    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Metodo non consentito. Usa POST."));
                return;
            }

            String filename = exchange.getRequestHeaders().getFirst("X-Filename");
            String target = exchange.getRequestHeaders().getFirst("X-Target");
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("filename=")) {
                        filename = java.net.URLDecoder.decode(param.substring(9), StandardCharsets.UTF_8);
                    } else if (param.startsWith("target=")) {
                        target = java.net.URLDecoder.decode(param.substring(7), StandardCharsets.UTF_8);
                    }
                }
            }
            if (filename == null || filename.isBlank()) {
                sendJsonResponse(exchange, 400, Map.of("error", "Specificare l'header X-Filename o parametro ?filename="));
                return;
            }

            filename = Paths.get(filename).getFileName().toString(); // Sanitize
            Path targetPath;

            if ("global".equalsIgnoreCase(target)) {
                targetPath = bpmnDir.resolve("GlobalMission.bpmn");
            } else if ("robot".equalsIgnoreCase(target)) {
                String safeName = filename.endsWith(".bpmn") ? filename : filename + ".bpmn";
                targetPath = bpmnDir.resolve(safeName);
            } else if ("semantics".equalsIgnoreCase(target)) {
                targetPath = semanticsFile;
            } else if ("taxonomy".equalsIgnoreCase(target) || "ontology".equalsIgnoreCase(target)) {
                targetPath = ontologyFile;
            } else if ("behavioral".equalsIgnoreCase(target)) {
                targetPath = behavioralFile;
            } else {
                // Auto-detect based on filename
                String lower = filename.toLowerCase(Locale.ROOT);
                if (lower.contains("global")) {
                    targetPath = bpmnDir.resolve("GlobalMission.bpmn");
                } else if (lower.endsWith(".bpmn")) {
                    targetPath = bpmnDir.resolve(filename);
                } else if (lower.contains("semantic")) {
                    targetPath = semanticsFile;
                } else if (lower.contains("taxonomy") || lower.contains("ontology")) {
                    targetPath = ontologyFile;
                } else if (lower.contains("behavior")) {
                    targetPath = behavioralFile;
                } else {
                    targetPath = Paths.get("src/main/resources", filename);
                }
            }

            byte[] bytes = exchange.getRequestBody().readAllBytes();
            if (bytes.length == 0) {
                sendJsonResponse(exchange, 400, Map.of("error", "Il file caricato è vuoto (0 bytes)."));
                return;
            }

            // Quick JSON validation
            if (targetPath.toString().endsWith(".json")) {
                try {
                    mapper.readTree(bytes);
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, Map.of("error", "File JSON non valido: " + e.getMessage()));
                    return;
                }
            }

            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, bytes);
            logger.info("File utente caricato con successo: {} ({} bytes, target={})", targetPath, bytes.length, target);

            sendJsonResponse(exchange, 200, Map.of(
                    "status", "UPLOADED",
                    "filename", filename,
                    "targetFile", targetPath.getFileName().toString(),
                    "targetPath", targetPath.toString(),
                    "sizeBytes", bytes.length,
                    "message", "File salvato correttamente come " + targetPath.getFileName()
            ));
        }
    }

    private class FileContentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getQuery();
                String file = null;
                String target = null;
                if (query != null) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("file=")) {
                            file = java.net.URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8);
                        } else if (param.startsWith("target=")) {
                            target = java.net.URLDecoder.decode(param.substring(7), StandardCharsets.UTF_8);
                        }
                    }
                }

                Path path = resolvePath(file, target);
                if (path == null || !Files.exists(path)) {
                    sendJsonResponse(exchange, 404, Map.of("error", "File non trovato: " + (file != null ? file : target)));
                    return;
                }

                String content = Files.readString(path, StandardCharsets.UTF_8);
                sendJsonResponse(exchange, 200, Map.of(
                        "filename", path.getFileName().toString(),
                        "targetPath", path.toString(),
                        "content", content,
                        "sizeBytes", Files.size(path)
                ));
            } else if ("POST".equalsIgnoreCase(method)) {
                JsonNode body = mapper.readTree(exchange.getRequestBody());
                String file = body.path("file").asText(body.path("filename").asText(""));
                String target = body.path("target").asText("");
                String content = body.path("content").asText(null);

                if (content == null) {
                    sendJsonResponse(exchange, 400, Map.of("error", "Campo 'content' obbligatorio."));
                    return;
                }

                Path path = resolvePath(file, target);
                if (path == null) {
                    sendJsonResponse(exchange, 400, Map.of("error", "Destinazione file non valida."));
                    return;
                }

                // JSON syntax validation
                if (path.toString().endsWith(".json")) {
                    try {
                        mapper.readTree(content);
                    } catch (Exception e) {
                        sendJsonResponse(exchange, 400, Map.of("error", "Sintassi JSON non valida: " + e.getMessage()));
                        return;
                    }
                }

                // XML BPMN syntax validation
                if (path.toString().endsWith(".bpmn") || path.toString().endsWith(".xml")) {
                    try {
                        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                        dbf.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(content)));
                    } catch (Exception e) {
                        sendJsonResponse(exchange, 400, Map.of("error", "Sintassi XML BPMN non valida: " + e.getMessage()));
                        return;
                    }
                }

                Files.writeString(path, content, StandardCharsets.UTF_8);
                logger.info("File aggiornato via Web Editor: {} ({} caratteri)", path, content.length());

                sendJsonResponse(exchange, 200, Map.of(
                        "status", "SAVED",
                        "filename", path.getFileName().toString(),
                        "sizeBytes", Files.size(path),
                        "message", "File salvato con successo."
                ));
            } else {
                sendJsonResponse(exchange, 405, Map.of("error", "Metodo non consentito: " + method));
            }
        }

        private Path resolvePath(String file, String target) {
            if (file != null && !file.isBlank()) {
                String safe = Paths.get(file).getFileName().toString();
                if (safe.contains("_enhanced") || safe.equals("UnifiedMission.bpmn") || "output".equalsIgnoreCase(target)) {
                    Path outPath = outputDir.resolve(safe);
                    if (Files.exists(outPath)) return outPath;
                }
            }
            if ("global".equalsIgnoreCase(target) || (file != null && file.toLowerCase().contains("global"))) {
                return bpmnDir.resolve("GlobalMission.bpmn");
            }
            if ("semantics".equalsIgnoreCase(target) || (file != null && file.toLowerCase().contains("semantics"))) {
                return semanticsFile;
            }
            if ("taxonomy".equalsIgnoreCase(target) || "ontology".equalsIgnoreCase(target)
                    || (file != null && (file.toLowerCase().contains("taxonomy") || file.toLowerCase().contains("ontology")))) {
                return ontologyFile;
            }
            if ("behavioral".equalsIgnoreCase(target) || (file != null && file.toLowerCase().contains("behavior"))) {
                return behavioralFile;
            }
            if (file != null && !file.isBlank()) {
                String safe = Paths.get(file).getFileName().toString();
                if (safe.endsWith(".bpmn")) {
                    return bpmnDir.resolve(safe);
                }
                return Paths.get("src/main/resources", safe);
            }
            return null;
        }
    }

    private class ResetInputsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("error", "Metodo non consentito. Usa POST."));
                return;
            }

            Path defaultsDir = Paths.get("src/main/resources/.defaults");
            if (!Files.exists(defaultsDir)) {
                sendJsonResponse(exchange, 500, Map.of("error", "Directory dei file di default non trovata."));
                return;
            }

            // Restore defaults
            Path defaultBpmn = defaultsDir.resolve("bpmn");
            if (Files.exists(defaultBpmn)) {
                try (var stream = Files.list(defaultBpmn)) {
                    stream.forEach(p -> {
                        try {
                            Files.copy(p, bpmnDir.resolve(p.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            logger.warn("Ripristino fallito per {}: {}", p, e.getMessage());
                        }
                    });
                }
            }

            String[] configs = {"semantics.json", "taxonomy.json", "behavioral-state.json"};
            for (String cfg : configs) {
                Path src = defaultsDir.resolve(cfg);
                if (Files.exists(src)) {
                    Files.copy(src, Paths.get("src/main/resources", cfg), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }

            logger.info("Tutti i file di input della missione sono stati ripristinati ai valori originali di tesi.");
            sendJsonResponse(exchange, 200, Map.of(
                    "status", "RESET_SUCCESSFUL",
                    "message", "Dataset di default ripristinato con successo."
            ));
        }
    }

    private class BpmnDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath(); // e.g. /api/bpmn/UnifiedMission.bpmn
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (fileName.isEmpty() || fileName.equals("bpmn")) {
                fileName = "UnifiedMission.bpmn";
            }
            if (!fileName.endsWith(".bpmn")) {
                fileName += ".bpmn";
            }

            Path targetFile = outputDir.resolve(fileName);
            if (!Files.exists(targetFile)) {
                targetFile = bpmnDir.resolve(fileName);
            }
            if (!Files.exists(targetFile)) {
                sendJsonResponse(exchange, 404, Map.of("error", "BPMN file not found: " + fileName));
                return;
            }

            byte[] bytes = Files.readAllBytes(targetFile);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/xml; charset=UTF-8");
            headers.set("Access-Control-Allow-Origin", "*");
            headers.set("Content-Disposition", "inline; filename=\"" + fileName + "\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private class DrawioDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (fileName.isEmpty() || fileName.equals("drawio")) {
                fileName = "UnifiedMission_Visual_Thesis.drawio";
            }
            if (!fileName.endsWith(".drawio")) {
                fileName += ".drawio";
            }

            Path targetFile = outputDir.resolve(fileName);
            if (!Files.exists(targetFile)) {
                sendJsonResponse(exchange, 404, Map.of("error", "Draw.io file not found: " + fileName));
                return;
            }

            byte[] bytes = Files.readAllBytes(targetFile);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/xml; charset=UTF-8");
            headers.set("Access-Control-Allow-Origin", "*");
            headers.set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(data);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=UTF-8");
        headers.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendCors(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(204, -1);
    }

    private static byte[] loadResourceOrFile(String path) {
        try {
            // First try classpath
            InputStream is = SynthesisWebServer.class.getClassLoader().getResourceAsStream(path);
            if (is != null) {
                try (is) {
                    return is.readAllBytes();
                }
            }
            // Fallback to disk
            Path p = Paths.get(path);
            if (Files.exists(p)) {
                return Files.readAllBytes(p);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
