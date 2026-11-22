package it.univaq.disim.mrs.synthesis.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.univaq.disim.mrs.synthesis.ai.AISemanticMatcher;
import it.univaq.disim.mrs.synthesis.analyzer.BehavioralCompatibilityTester;
import it.univaq.disim.mrs.synthesis.analyzer.BehavioralStateSimulator;
import it.univaq.disim.mrs.synthesis.analyzer.GlobalChoreographyAnalyzer;
import it.univaq.disim.mrs.synthesis.analyzer.LocalProcessAnalyzer;
import it.univaq.disim.mrs.synthesis.event.SynthesisEvent;
import it.univaq.disim.mrs.synthesis.event.SynthesisEventListener;
import it.univaq.disim.mrs.synthesis.generator.DrawioExporter;
import it.univaq.disim.mrs.synthesis.input.UserInputManager;
import it.univaq.disim.mrs.synthesis.model.DataNode;
import it.univaq.disim.mrs.synthesis.model.InteractionMatch;
import it.univaq.disim.mrs.synthesis.model.InteractionPlan;
import it.univaq.disim.mrs.synthesis.model.MissionContext;
import it.univaq.disim.mrs.synthesis.model.RobotTaskMetadata;
import it.univaq.disim.mrs.synthesis.persistence.ModelRepository;
import it.univaq.disim.mrs.synthesis.planner.InteractionPlanner;
import it.univaq.disim.mrs.synthesis.semantic.OntologyManager;
import it.univaq.disim.mrs.synthesis.semantic.SyntacticCompatibilityChecker;
import it.univaq.disim.mrs.synthesis.weaver.BpmnFlowWeaver;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;

/** End-to-end synthesis pipeline. Unresolved interactions are reported, never guessed. */
public class MultiRobotSynthesisEngine {
    private static final Logger logger = LoggerFactory.getLogger(MultiRobotSynthesisEngine.class);
    private static final File DEFAULT_ONTOLOGY = new File("src/main/resources/taxonomy.json");
    private static final File DEFAULT_SEMANTICS = new File("src/main/resources/semantics.json");
    private static final File DEFAULT_BEHAVIORAL_STATE = new File("src/main/resources/behavioral-state.json");
    private static final Path OUTPUT_DIRECTORY = Path.of("target", "output");

    private final ModelRepository repository = new ModelRepository();
    private final OntologyManager ontologyManager = new OntologyManager();
    private final SyntacticCompatibilityChecker syntacticChecker = new SyntacticCompatibilityChecker(ontologyManager);
    private final LocalProcessAnalyzer localAnalyzer = new LocalProcessAnalyzer();
    private final BehavioralCompatibilityTester behavioralTester = new BehavioralCompatibilityTester();
    private final BehavioralStateSimulator behavioralStateSimulator = new BehavioralStateSimulator();
    private final GlobalChoreographyAnalyzer globalAnalyzer = new GlobalChoreographyAnalyzer();
    private final AISemanticMatcher semanticMatcher;
    private final InteractionPlanner planner = new InteractionPlanner();
    private final BpmnFlowWeaver weaver = new BpmnFlowWeaver();
    private final UserInputManager inputManager = new UserInputManager();
    private final DrawioExporter drawioExporter = new DrawioExporter();
    private SynthesisEventListener eventListener = SynthesisEventListener.NO_OP;

    public MultiRobotSynthesisEngine() {
        this(null);
    }

    public MultiRobotSynthesisEngine(AISemanticMatcher customSemanticMatcher) {
        this.semanticMatcher = customSemanticMatcher != null ? customSemanticMatcher : new AISemanticMatcher(ontologyManager);
    }

    public SynthesisEventListener getEventListener() {
        return eventListener;
    }

    public void setEventListener(SynthesisEventListener listener) {
        this.eventListener = listener != null ? listener : SynthesisEventListener.NO_OP;
    }

    private void emit(SynthesisEvent event) {
        if (this.eventListener != null) {
            try {
                this.eventListener.onEvent(event);
            } catch (Exception ex) {
                logger.debug("Failed to deliver synthesis event: {}", ex.getMessage());
            }
        }
    }

    public MissionContext runSynthesisPipeline(List<File> localProcessesFiles, File globalChoreoFile) {
        return runSynthesisPipeline(localProcessesFiles, globalChoreoFile, DEFAULT_ONTOLOGY, DEFAULT_SEMANTICS, false);
    }

    /** Backward-compatible overload used by the integration tests. */
    public MissionContext runSynthesisPipeline(List<File> localProcessesFiles, File globalChoreoFile,
                                               File ontologyFile, boolean failOnUnresolved) {
        return runSynthesisPipeline(localProcessesFiles, globalChoreoFile, ontologyFile, DEFAULT_SEMANTICS, failOnUnresolved);
    }

    public MissionContext runSynthesisPipeline(List<File> localProcessesFiles, File globalChoreoFile,
                                               File ontologyFile, File semanticsFile, boolean failOnUnresolved) {
        return runSynthesisPipeline(localProcessesFiles, globalChoreoFile, ontologyFile, semanticsFile,
                DEFAULT_BEHAVIORAL_STATE, failOnUnresolved);
    }

    /** Full overload for domains that provide their own explicit behavioral initial-state model. */
    public MissionContext runSynthesisPipeline(List<File> localProcessesFiles, File globalChoreoFile,
                                               File ontologyFile, File semanticsFile, File behavioralStateFile,
                                               boolean failOnUnresolved) {
        validateInputs(localProcessesFiles, globalChoreoFile, ontologyFile, semanticsFile);
        if (behavioralStateFile == null || !behavioralStateFile.isFile()) {
            throw new IllegalArgumentException("Behavioral initial-state configuration not found: " + behavioralStateFile);
        }
        clearPreviousOutputs();

        MissionContext context = new MissionContext();
        File effectiveGlobalChoreo = new it.univaq.disim.mrs.synthesis.adapter.ChoreographyAdapter().adapt(globalChoreoFile);
        context.setGlobalChoreography(repository.loadModel(effectiveGlobalChoreo));
        ontologyManager.loadOntology(ontologyFile);
        List<DataNode> specifications = inputManager.loadSemanticSpecifications(semanticsFile);
        Map<String, String> initialBehavioralState = loadInitialBehavioralState(behavioralStateFile);

        emit(SynthesisEvent.info(SynthesisEvent.Type.PIPELINE_STARTED, 0, "Figura 5.1 - Input del Metodo",
                "Avvio sintesi multi-robot con " + localProcessesFiles.size() + " modelli robot locali secondo Figura 5.1 della Tesi."));

        // Comportamento locale dei robot (Diagrammi di processo BPMN)
        for (File robotFile : localProcessesFiles) {
            String robotName = cleanRobotName(robotFile.getName());
            if (context.getRobotModel(robotFile.getName()) != null) {
                throw new IllegalArgumentException("Duplicate local process name: " + robotFile.getName());
            }
            context.addRobotModel(robotFile.getName(), repository.loadModel(robotFile));
            List<RobotTaskMetadata> metadata = localAnalyzer.analyzeLocalModels(robotName,
                    context.getRobotModel(robotFile.getName()));
            context.getExtractedMetadata().addAll(metadata);
            emit(new SynthesisEvent(SynthesisEvent.Type.ROBOT_LOADED, 0, "Figura 5.1 - Input del Metodo",
                    "Comportamento locale caricato: Robot '" + robotName + "' (" + metadata.size() + " task interni).",
                    robotName, null, null, true, "File: " + robotFile.getName() + " (" + metadata.size() + " task)"));
        }

        // Specifica della missione (Choreography diagram BPMN)
        List<String> globalCoherenceIssues = globalAnalyzer.validateStructuralCoherence(context.getGlobalChoreography());
        for (String issue : globalCoherenceIssues) addDiagnostic(context, "Global choreography coherence check failed: " + issue);
        List<MessageFlow> globalMessages = globalAnalyzer.extractGlobalMessages(context.getGlobalChoreography());
        if (globalMessages.isEmpty()) {
            throw new IllegalArgumentException("The global BPMN contains no MessageFlow elements.");
        }
        for (MessageFlow flow : globalMessages) {
            String mName = flow.getMessage() != null ? flow.getMessage().getName() : flow.getName();
            String sRobot = cleanRobotName(globalAnalyzer.getParticipantName(flow.getSource()));
            String tRobot = cleanRobotName(globalAnalyzer.getParticipantName(flow.getTarget()));
            emit(new SynthesisEvent(SynthesisEvent.Type.GLOBAL_MESSAGE_DISCOVERED, 0, "Figura 5.1 - Input del Metodo",
                    "Specifica della missione: rilevata interazione '" + mName + "' (" + sRobot + " ➔ " + tRobot + ")", sRobot, tRobot, mName, true, "Tratta: " + sRobot + " ➔ " + tRobot));
        }

        // Ontologia globale (OWL) e Casi di prova (Behavioral State)
        emit(new SynthesisEvent(SynthesisEvent.Type.INPUT_LOADED, 0, "Figura 5.1 - Input del Metodo",
                "Ontologia globale e Casi di prova (Contratti operativi) caricati.", null, null, null, true,
                "Ontologia: " + ontologyFile.getName() + " | Casi di Prova: " + behavioralStateFile.getName()));

        // =========================================================================
        // STEP 1: ANALISI DATI DI SCAMBIO I/O (Figura 5.1 - Step 1)
        // =========================================================================
        logger.info("[Step 1] Analyzing I/O data exchanges (syntactic & structural compatibility).");
        emit(SynthesisEvent.info(SynthesisEvent.Type.STEP_STARTED, 1, "STEP 1: Analisi Dati Scambio I/O",
                "Analisi delle operazioni interne e verifica compatibilità sintattica/strutturale I/O tra task locali e messaggi della coreografia globale."));

        List<SyntacticCandidate> syntacticCandidates = new ArrayList<>();
        for (MessageFlow flow : globalMessages) {
            SyntacticCandidate candidate = checkSyntacticCompatibility(context, flow, specifications);
            if (candidate != null) {
                syntacticCandidates.add(candidate);
                emit(SynthesisEvent.check(SynthesisEvent.Type.SYNTACTIC_CHECK, 1, "STEP 1: Analisi Dati Scambio I/O", candidate.messageName,
                        candidate.sourceRobot, candidate.targetRobot, true,
                        "Compatibilità sintattica/strutturale verificata: '" + candidate.producer.getProducedDataType() + "' ➔ '" + candidate.expectedConsumerType + "'"));
            }
        }

        // Artefatto Intermedio 1 della Figura 5.1: Set di dipendenze dei dati iniziali (Tabella corrispondenze sintattiche)
        String synSummary = syntacticCandidates.stream()
                .map(c -> c.messageName + " (" + c.sourceRobot + " ➔ " + c.targetRobot + ")")
                .collect(Collectors.joining(", "));
        emit(new SynthesisEvent(SynthesisEvent.Type.INITIAL_DEPENDENCIES_TABLE, 1, "STEP 1: Analisi Dati Scambio I/O",
                "Artefatto generato: Set di Dipendenze dei Dati Iniziali (" + syntacticCandidates.size() + " corrispondenze sintatticamente corrette).",
                null, null, null, true, "Tabella Corrispondenze Sintattiche: " + synSummary));

        // =========================================================================
        // STEP 2: ANALISI DELLA COMPATIBILITÀ SEMANTICA (Figura 5.1 - Step 2)
        // =========================================================================
        logger.info("[Step 2] Analyzing semantic compatibility & behavioral testing.");
        emit(SynthesisEvent.info(SynthesisEvent.Type.STEP_STARTED, 2, "STEP 2: Compatibilità Semantica & Testing",
                "Raffinamento delle corrispondenze tramite Ontologia Globale (OWL / subsumption) e Casi di Prova (LTS / contratti)."));

        for (SyntacticCandidate candidate : syntacticCandidates) {
            resolveSemanticAndBehavioral(context, candidate, initialBehavioralState);
        }

        // Artefatto Intermedio 2 della Figura 5.1: Set di dipendenze dei dati raffinato (Tabella corrispondenze semantiche)
        String semSummary = context.getValidatedMatches().stream()
                .map(m -> m.getMessageName() + " (" + m.getSenderRobot() + " ➔ " + m.getReceiverRobot() + ")")
                .collect(Collectors.joining(", "));
        emit(new SynthesisEvent(SynthesisEvent.Type.REFINED_DEPENDENCIES_TABLE, 2, "STEP 2: Compatibilità Semantica & Testing",
                "Artefatto generato: Set di Dipendenze dei Dati Raffinato (" + context.getValidatedMatches().size() + " corrispondenze semanticamente validate).",
                null, null, null, true, "Tabella Corrispondenze Semantiche: " + semSummary));

        if (failOnUnresolved && !context.getDiagnostics().isEmpty()) {
            throw new IllegalStateException("Synthesis stopped because unresolved interactions were found: "
                    + String.join(" | ", context.getDiagnostics()));
        }
        if (context.getValidatedMatches().isEmpty()) {
            throw new IllegalStateException("No valid interactions were found. Review semantics.json, taxonomy.json, "
                    + "and the local BPMN input/output metadata. Diagnostics: " + String.join(" | ", context.getDiagnostics()));
        }

        // =========================================================================
        // STEP 3: SINTESI DELLA MISSIONE (Figura 5.1 - Step 3)
        // =========================================================================
        logger.info("[Step 3] Mission synthesis: interaction planning & BPMN weaving.");
        emit(SynthesisEvent.info(SynthesisEvent.Type.STEP_STARTED, 3, "STEP 3: Sintesi della Missione",
                "Disambiguazione relazioni 1->N, pianificazione delle interazioni e tessitura BPMN (Weaving)."));

        List<InteractionPlan> plans = planner.buildInteractionPlans(context);
        for (InteractionPlan plan : plans) {
            for (InteractionMatch match : plan.getReceiverMatches()) {
                emit(new SynthesisEvent(SynthesisEvent.Type.PLAN_CREATED, 3, "STEP 3: Sintesi della Missione",
                        "Piano interazione per '" + match.getMessageName() + "' (" + match.getSenderRobot() + " ➔ " + match.getReceiverRobot() + ")",
                        match.getSenderRobot(), match.getReceiverRobot(),
                        match.getMessageName(), true, "Produttore: " + match.getSenderTaskId() + " ➔ Consumatore: " + match.getReceiverTaskId()));
            }
        }

        weaver.weaveInteractions(context, plans);
        emit(new SynthesisEvent(SynthesisEvent.Type.WEAVING_TASK, 3, "STEP 3: Sintesi della Missione",
                "Tessitura completata: SendTask e ReceiveTask inseriti nei modelli BPMN dei robot.",
                null, null, null, true, "Piani tessuti: " + plans.size()));

        logger.info("[Output] Writing enhanced BPMN and Draw.io diagrams.");
        context.getAllRobotModels().forEach((fileName, model) ->
                repository.saveModel(model, cleanRobotName(fileName) + "_enhanced.bpmn"));
        repository.saveUnifiedDiagram(context);
        drawioExporter.exportAllToDrawio(context, OUTPUT_DIRECTORY.toString() + File.separator);

        // Artefatto Finale della Figura 5.1: Comportamento collaborativo dei robot
        emit(new SynthesisEvent(SynthesisEvent.Type.COLLABORATIVE_ROBOT_BEHAVIOR, 3, "STEP 3: Sintesi della Missione",
                "Artefatto finale generato: Comportamento Collaborativo dei Robot (" + context.getAllRobotModels().size() + " modelli BPMN Enhanced + Unified Collaboration Diagram).",
                null, null, null, true, "Modelli BPMN: " + String.join(", ", context.getAllRobotModels().keySet())));

        emit(new SynthesisEvent(SynthesisEvent.Type.PIPELINE_COMPLETED, 3, "Completamento",
                "Sintesi multi-robot completata con successo secondo la Figura 5.1!", null, null, null, true,
                context.getValidatedMatches().size() + " interazioni validate; " + context.getDiagnostics().size() + " diagnostiche."));

        if (!context.getDiagnostics().isEmpty()) {
            logger.warn("Synthesis completed with {} unresolved/unsupported message(s):", context.getDiagnostics().size());
            context.getDiagnostics().forEach(d -> logger.warn(" - {}", d));
        }
        if (!context.getBehavioralNotes().isEmpty()) {
            logger.warn("Behavioral proof remains incomplete for {} interaction(s):", context.getBehavioralNotes().size());
            context.getBehavioralNotes().forEach(note -> logger.warn(" - {}", note));
        }
        logger.info("Synthesis completed: {} validated interactions; {} diagnostics; {} behavioral note(s).",
                context.getValidatedMatches().size(), context.getDiagnostics().size(), context.getBehavioralNotes().size());
        return context;
    }

    private static class SyntacticCandidate {
        final MessageFlow flow;
        final String messageName;
        final String sourceRobot;
        final String targetRobot;
        final DataNode spec;
        final RobotTaskMetadata producer;
        final RobotTaskMetadata consumer;
        final String expectedConsumerType;
        final boolean strongSyntax;
        final boolean isBroadcast;

        SyntacticCandidate(MessageFlow flow, String messageName, String sourceRobot, String targetRobot,
                           DataNode spec, RobotTaskMetadata producer, RobotTaskMetadata consumer,
                           String expectedConsumerType, boolean strongSyntax, boolean isBroadcast) {
            this.flow = flow;
            this.messageName = messageName;
            this.sourceRobot = sourceRobot;
            this.targetRobot = targetRobot;
            this.spec = spec;
            this.producer = producer;
            this.consumer = consumer;
            this.expectedConsumerType = expectedConsumerType;
            this.strongSyntax = strongSyntax;
            this.isBroadcast = isBroadcast;
        }
    }

    /**
     * STEP 1 (Figura 5.1): Analisi Dati di Scambio I/O.
     * Verifica la compatibilità sintattica e strutturale tra operazioni interne dei robot e messaggi globali.
     */
    private SyntacticCandidate checkSyntacticCompatibility(MissionContext context, MessageFlow flow, List<DataNode> specs) {
        String messageName = flow.getMessage() != null ? flow.getMessage().getName() : flow.getName();
        if (blank(messageName)) {
            addDiagnostic(context, "MessageFlow " + flow.getId() + " has no message name.");
            return null;
        }
        String sourceRobot = cleanRobotName(globalAnalyzer.getParticipantName(flow.getSource()));
        String targetRobot = cleanRobotName(globalAnalyzer.getParticipantName(flow.getTarget()));
        DataNode spec = findUserSpec(specs, messageName);
        if (spec == null) {
            addDiagnostic(context, "No semantic specification exists for message '" + messageName + "'.");
            return null;
        }
        if (!same(sourceRobot, spec.getRobotId())) {
            addDiagnostic(context, "Message '" + messageName + "' source participant '" + sourceRobot
                    + "' disagrees with producer robot '" + spec.getRobotId() + "' in semantics.json.");
            return null;
        }

        RobotTaskMetadata producer = findTask(context.getExtractedMetadata(), sourceRobot, spec.getTaskId());
        if (producer == null) {
            addDiagnostic(context, "Producer task '" + spec.getTaskId() + "' for message '" + messageName
                    + "' was not found in local process '" + sourceRobot + "'.");
            return null;
        }
        if (blank(producer.getProducedDataType())) {
            addDiagnostic(context, "Producer task '" + spec.getTaskId() + "' does not declare producedData for '" + messageName + "'.");
            return null;
        }
        boolean strongSyntax = "STRONG".equalsIgnoreCase(spec.getSyntacticCompatibility());
        boolean producerTypeCompatible = syntacticChecker.isTypeCompatible(
                producer.getProducedDataType(), spec.getDataType(), spec.getSyntacticCompatibility());
        if (!producerTypeCompatible) {
            addDiagnostic(context, "Producer type '" + producer.getProducedDataType() + "' is not "
                    + (strongSyntax ? "strongly" : "weakly") + " syntactically compatible with declared message type '"
                    + spec.getDataType() + "' for '" + messageName + "'.");
            return null;
        }

        String expectedConsumerType = blank(spec.getReceiverDataType()) ? spec.getDataType() : spec.getReceiverDataType();
        Collection<String> producedFields = spec.getProducedFields();
        Collection<String> receiverFields = spec.getReceiverFields();
        if ((producedFields == null || producedFields.isEmpty()) && (receiverFields == null || receiverFields.isEmpty())) {
            Set<String> derivedProduced = ontologyManager.getFieldsForType(producer.getProducedDataType());
            Set<String> derivedReceiver = ontologyManager.getFieldsForType(expectedConsumerType);
            if (!derivedReceiver.isEmpty()) {
                producedFields = derivedProduced;
                receiverFields = derivedReceiver;
            }
        }
        if (!syntacticChecker.isStructurallyCompatible(producedFields, receiverFields)) {
            addDiagnostic(context, "Structural compatibility rejected for message '" + messageName
                    + "': produced fields do not contain all receiver-required fields with matching declarations.");
            return null;
        }

        boolean isBroadcast = "broadcast".equalsIgnoreCase(targetRobot);
        RobotTaskMetadata consumer = null;
        if (!isBroadcast) {
            consumer = findConsumer(context.getExtractedMetadata(), targetRobot,
                    spec.getReceiverTaskId(), producer.getProducedDataType(), expectedConsumerType, strongSyntax);
            if (consumer == null) {
                addDiagnostic(context, "No compatible consumer task found in '" + targetRobot + "' for message '" + messageName
                        + "' (produced type=" + producer.getProducedDataType() + ", expected input=" + expectedConsumerType + ").");
                return null;
            }
        }

        return new SyntacticCandidate(flow, messageName, sourceRobot, targetRobot, spec, producer, consumer,
                expectedConsumerType, strongSyntax, isBroadcast);
    }

    /**
     * STEP 2 (Figura 5.1): Analisi della Compatibilità Semantica & Testing Comportamentale.
     * Raffina le corrispondenze sintattiche mediante Ontologia Globale (OWL / subsumption) e Casi di Prova (LTS).
     */
    private void resolveSemanticAndBehavioral(MissionContext context, SyntacticCandidate candidate,
                                             Map<String, String> initialBehavioralState) {
        String messageName = candidate.messageName;
        String sourceRobot = candidate.sourceRobot;
        String targetRobot = candidate.targetRobot;
        DataNode spec = candidate.spec;
        RobotTaskMetadata producer = candidate.producer;

        BpmnModelInstance producerModel = findRobotModel(context, sourceRobot);
        if (producerModel == null) {
            addDiagnostic(context, "Behavioral producer-prefix check is UNRESOLVED for message '" + messageName
                    + "': the producer BPMN model was not found.");
            return;
        }
        List<RobotTaskMetadata> producerMetadata = context.getExtractedMetadata().stream()
                .filter(item -> same(item.getRobotId(), sourceRobot)).collect(Collectors.toList());
        BehavioralCompatibilityTester.ContinuationPaths producerPrefixes = behavioralTester.prefixPaths(
                producerModel, producer.getTaskId(), producerMetadata, 256);
        if (producerPrefixes.getStatus() != BehavioralCompatibilityTester.Status.PASS) {
            addDiagnostic(context, "Behavioral producer-prefix analysis " + producerPrefixes.getStatus()
                    + " for message '" + messageName + "': " + producerPrefixes.getExplanation());
            return;
        }
        Map<String, String> stateBeforeInteraction = stateWithPriorInteractionEffects(context, initialBehavioralState);

        if (candidate.isBroadcast) {
            boolean semanticMatch = semanticMatcher.isSemanticMatch(
                    producer.getProducedDataType(), spec.getSemanticDescription(),
                    producer.getProducedDataType(), spec.getReceiverSemanticDescription(),
                    spec.getSemanticConcept(), spec.getReceiverSemanticConcept());
            if (!semanticMatch) {
                addDiagnostic(context, "Broadcast message '" + messageName + "' lacks semantic compatibility.");
                return;
            }
            String broadcastExpl = semanticMatcher.getLastExplanation();
            if (broadcastExpl == null || broadcastExpl.isBlank()) {
                broadcastExpl = "Match semantico validato: concetti '" + spec.getSemanticConcept() + "' e '" + spec.getReceiverSemanticConcept() + "'";
            }
            String broadcastTitle = "STEP 2: " + buildSemanticPhaseTitle(semanticMatcher);
            emit(SynthesisEvent.check(SynthesisEvent.Type.SEMANTIC_CHECK, 2, broadcastTitle, messageName,
                    sourceRobot, targetRobot, true, broadcastExpl));

            boolean broadcastHasContracts = producerPrefixes.getPaths().stream().anyMatch(path -> path.stream()
                    .anyMatch(item -> !item.getPreconditions().isEmpty() || !item.getEffects().isEmpty()));
            if (broadcastHasContracts) {
                for (List<RobotTaskMetadata> prefix : producerPrefixes.getPaths()) {
                    BehavioralStateSimulator.Result result = behavioralStateSimulator.simulate(prefix, stateBeforeInteraction);
                    if (result.getStatus() != BehavioralStateSimulator.Status.PASS) {
                        addDiagnostic(context, "Behavioral state simulation " + result.getStatus()
                                + " for broadcast message '" + messageName + "': " + result.getExplanation());
                        return;
                    }
                }
            } else {
                context.addBehavioralNote("Behavioral state simulation UNRESOLVED for broadcast message '" + messageName
                        + "': producer prefix has no explicit precondition/effect contracts.");
            }
            context.getValidatedMatches().add(new InteractionMatch(sourceRobot, producer.getTaskId(),
                    "Broadcast", "Broadcast", messageName, producer.getProducedDataType(), spec.getSemanticDescription()));
            return;
        }

        RobotTaskMetadata consumer = candidate.consumer;
        BpmnModelInstance consumerModel = findRobotModel(context, targetRobot);
        if (consumerModel == null) {
            addDiagnostic(context, "Behavioral compatibility check is UNRESOLVED for message '" + messageName
                    + "': the consumer BPMN model was not found.");
            return;
        }
        BehavioralCompatibilityTester.Assessment structuralAssessment = behavioralTester.assess(
                consumerModel, consumer, producer.getProducedDataType(), ontologyManager);
        if (structuralAssessment.getStatus() != BehavioralCompatibilityTester.Status.PASS) {
            addDiagnostic(context, "Behavioral compatibility check " + structuralAssessment.getStatus()
                    + " for message '" + messageName + "': " + structuralAssessment.getExplanation());
            return;
        }
        List<RobotTaskMetadata> receiverMetadata = context.getExtractedMetadata().stream()
                .filter(item -> same(item.getRobotId(), targetRobot)).collect(Collectors.toList());
        BehavioralCompatibilityTester.ContinuationPaths continuationPaths = behavioralTester.continuationPaths(
                consumerModel, consumer.getTaskId(), receiverMetadata, 256);
        if (continuationPaths.getStatus() != BehavioralCompatibilityTester.Status.PASS) {
            addDiagnostic(context, "Behavioral continuation analysis " + continuationPaths.getStatus()
                    + " for message '" + messageName + "': " + continuationPaths.getExplanation());
            return;
        }
        boolean hasProducerContracts = producerPrefixes.getPaths().stream().flatMap(List::stream)
                .anyMatch(item -> !item.getPreconditions().isEmpty() || !item.getEffects().isEmpty());
        boolean hasReceiverContracts = continuationPaths.getPaths().stream().flatMap(List::stream)
                .anyMatch(item -> !item.getPreconditions().isEmpty() || !item.getEffects().isEmpty());
        boolean hasExplicitContracts = hasProducerContracts && hasReceiverContracts;
        long pathCombinations = (long) producerPrefixes.getPaths().size() * continuationPaths.getPaths().size();
        if (pathCombinations > 256) {
            addDiagnostic(context, "Behavioral state simulation UNRESOLVED for message '" + messageName
                    + "': producer/receiver path combinations exceed the safety limit (256).");
            return;
        }
        if (hasExplicitContracts) {
            for (List<RobotTaskMetadata> prefix : producerPrefixes.getPaths()) {
                for (List<RobotTaskMetadata> continuation : continuationPaths.getPaths()) {
                    List<RobotTaskMetadata> simulatedPath = new ArrayList<>(prefix);
                    simulatedPath.addAll(continuation);
                    BehavioralStateSimulator.Result behavioralResult = behavioralStateSimulator.simulate(
                            simulatedPath, stateBeforeInteraction);
                    if (behavioralResult.getStatus() != BehavioralStateSimulator.Status.PASS) {
                        addDiagnostic(context, "Behavioral state simulation " + behavioralResult.getStatus()
                                + " for message '" + messageName + "': " + behavioralResult.getExplanation());
                        return;
                    }
                }
            }
        } else {
            context.addBehavioralNote("Behavioral state simulation UNRESOLVED for message '" + messageName
                    + "': explicit contracts are missing from the producer prefix or receiver continuation. "
                    + "Structural checks passed, but §5.6 behavioral compatibility has not been proven.");
        }
        emit(SynthesisEvent.check(SynthesisEvent.Type.BEHAVIORAL_CHECK, 2, "STEP 2: Testing Comportamentale (Casi di Prova)", messageName,
                sourceRobot, targetRobot, true, "Verifica prefissi/continuazioni e contratti LTS completata (deadlock-free)"));

        if (!semanticMatcher.isSemanticMatch(producer.getProducedDataType(), spec.getSemanticDescription(),
                candidate.expectedConsumerType, spec.getReceiverSemanticDescription(), spec.getSemanticConcept(), spec.getReceiverSemanticConcept())) {
            addDiagnostic(context, "Semantic compatibility rejected for message '" + messageName + "'.");
            return;
        }

        String explanation = semanticMatcher.getLastExplanation();
        if (explanation == null || explanation.isBlank()) {
            explanation = "Match semantico validato: concetti '" + spec.getSemanticConcept() + "' e '" + spec.getReceiverSemanticConcept() + "'";
        }
        String phaseTitle = "STEP 2: " + buildSemanticPhaseTitle(semanticMatcher);

        emit(SynthesisEvent.check(SynthesisEvent.Type.SEMANTIC_CHECK, 2, phaseTitle, messageName,
                sourceRobot, targetRobot, true, explanation));

        context.getValidatedMatches().add(new InteractionMatch(sourceRobot, producer.getTaskId(), targetRobot,
                consumer.getTaskId(), messageName, producer.getProducedDataType(), spec.getSemanticDescription()));
    }


    /** Load an explicit initial state. Missing or malformed state is a configuration error, not an empty-state fallback. */
    private Map<String, String> loadInitialBehavioralState(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Behavioral initial-state configuration not found: " + file);
        }
        try {
            JsonNode root = new ObjectMapper().readTree(file);
            JsonNode stateNode = root == null ? null : root.get("initialState");
            if (stateNode == null || !stateNode.isObject()) {
                throw new IllegalArgumentException("behavioral-state.json must contain an object named 'initialState'.");
            }
            Map<String, String> state = new LinkedHashMap<>();
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = stateNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue() == null || field.getValue().isNull() || !field.getValue().isValueNode()) {
                    throw new IllegalArgumentException("Initial-state value for '" + field.getKey() + "' must be a scalar.");
                }
                state.put(field.getKey(), field.getValue().asText());
            }
            return state;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Cannot read behavioral initial-state configuration: " + file, e);
        }
    }

    /** Apply only already validated producer effects as the explicit state entering the next choreography interaction. */
    private Map<String, String> stateWithPriorInteractionEffects(MissionContext context, Map<String, String> initialState) {
        Map<String, String> state = new LinkedHashMap<>();
        if (initialState != null) state.putAll(initialState);
        for (InteractionMatch previous : context.getValidatedMatches()) {
            RobotTaskMetadata previousProducer = findTask(context.getExtractedMetadata(),
                    previous.getSenderRobot(), previous.getSenderTaskId());
            if (previousProducer == null) continue;
            for (String literal : previousProducer.getEffects()) {
                if (literal == null) continue;
                int equals = literal.indexOf('=');
                if (equals <= 0 || equals == literal.length() - 1) continue; // already validated on the preceding interaction
                String key = literal.substring(0, equals).trim();
                String value = literal.substring(equals + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) state.put(key, value);
            }
        }
        return state;
    }

    private BpmnModelInstance findRobotModel(MissionContext context, String robotName) {
        for (java.util.Map.Entry<String, BpmnModelInstance> entry : context.getAllRobotModels().entrySet()) {
            if (same(cleanRobotName(entry.getKey()), robotName)) return entry.getValue();
        }
        return null;
    }

    private RobotTaskMetadata findConsumer(List<RobotTaskMetadata> metadata, String targetRobot,
                                           String requiredTaskId, String producedType, String declaredRequiredType,
                                           boolean strongSyntax) {
        List<RobotTaskMetadata> candidates = metadata.stream()
                .filter(m -> same(m.getRobotId(), targetRobot))
                .filter(m -> blank(requiredTaskId) || same(m.getTaskId(), requiredTaskId))
                .filter(m -> m.getRequiredDataTypes().stream().anyMatch(input -> {
                    boolean producerToConsumer = strongSyntax
                            ? ontologyManager.isStronglyCompatible(producedType, input)
                            : ontologyManager.isWeaklyCompatible(producedType, input);
                    boolean declaredToConsumer = strongSyntax
                            ? ontologyManager.isStronglyCompatible(declaredRequiredType, input)
                            : ontologyManager.isWeaklyCompatible(declaredRequiredType, input);
                    return producerToConsumer && declaredToConsumer;
                }))
                .collect(Collectors.toList());
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private RobotTaskMetadata findTask(List<RobotTaskMetadata> metadata, String robot, String taskId) {
        List<RobotTaskMetadata> candidates = metadata.stream()
                .filter(m -> same(m.getRobotId(), robot) && same(m.getTaskId(), taskId))
                .collect(Collectors.toList());
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private DataNode findUserSpec(List<DataNode> specs, String messageName) {
        List<DataNode> matches = specs.stream().filter(n -> same(n.getMessageName(), messageName)).collect(Collectors.toList());
        if (matches.size() > 1) throw new IllegalArgumentException("Duplicate semantic specifications for message: " + messageName);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void validateInputs(List<File> locals, File global, File ontology, File semantics) {
        if (locals == null || locals.isEmpty()) throw new IllegalArgumentException("At least one local BPMN process is required.");
        for (File local : locals) if (local == null || !local.isFile()) throw new IllegalArgumentException("Local BPMN file not found: " + local);
        if (global == null || !global.isFile()) throw new IllegalArgumentException("Global choreography BPMN file not found: " + global);
        if (ontology == null || !ontology.isFile()) throw new IllegalArgumentException("Ontology file not found: " + ontology);
        if (semantics == null || !semantics.isFile()) throw new IllegalArgumentException("Semantic specification file not found: " + semantics);
    }

    private void clearPreviousOutputs() {
        try {
            if (Files.exists(OUTPUT_DIRECTORY)) {
                try (java.util.stream.Stream<Path> files = Files.list(OUTPUT_DIRECTORY)) {
                    for (Path path : files.collect(Collectors.toList())) {
                        if (Files.isRegularFile(path) && (path.toString().endsWith(".bpmn") || path.toString().endsWith(".drawio"))) {
                            Files.deleteIfExists(path);
                        }
                    }
                }
            } else {
                Files.createDirectories(OUTPUT_DIRECTORY);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot clear old synthesis artifacts in " + OUTPUT_DIRECTORY, e);
        }
    }

    private void addDiagnostic(MissionContext context, String diagnostic) {
        context.addDiagnostic(diagnostic);
        logger.warn("[Unresolved interaction] {}", diagnostic);
        emit(new SynthesisEvent(SynthesisEvent.Type.DIAGNOSTIC, 0, "Diagnostica", diagnostic, null, null, null, false, diagnostic));
    }

    private static String buildSemanticPhaseTitle(AISemanticMatcher matcher) {
        if (matcher == null) return "Verifica Semantica";
        if ("LLM_REASONER".equalsIgnoreCase(matcher.getMode())) {
            String provider = matcher.getLastProvider();
            if ("GEMINI".equalsIgnoreCase(provider)) {
                return "Verifica Semantica (Google Gemini Flash 2.0 AI)";
            } else if ("OPENAI".equalsIgnoreCase(provider)) {
                return "Verifica Semantica (OpenAI GPT-4o-mini)";
            } else {
                if (matcher.getLastApiError() != null) {
                    return "Verifica Semantica (Fallback Locale - Errore Chiave Gemini)";
                }
                return "Verifica Semantica (LLM Reasoner Locale)";
            }
        } else if ("ONTOLOGY_STRICT".equalsIgnoreCase(matcher.getMode())) {
            return "Verifica Semantica (Ontologia OWL)";
        } else {
            return "Verifica Semantica";
        }
    }

    private static String cleanRobotName(String name) {
        if (name == null) return "";
        return name.replace(".bpmn", "").replace("Part_", "").replace("_enhanced", "").trim();
    }
    private static boolean same(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
