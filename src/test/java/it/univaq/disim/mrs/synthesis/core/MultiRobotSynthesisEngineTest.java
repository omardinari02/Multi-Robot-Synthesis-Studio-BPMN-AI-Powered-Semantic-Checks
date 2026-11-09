package it.univaq.disim.mrs.synthesis.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.univaq.disim.mrs.synthesis.ai.AISemanticMatcher;
import it.univaq.disim.mrs.synthesis.analyzer.LocalProcessAnalyzer;
import it.univaq.disim.mrs.synthesis.analyzer.GlobalChoreographyAnalyzer;
import it.univaq.disim.mrs.synthesis.input.UserInputManager;
import it.univaq.disim.mrs.synthesis.model.DataNode;
import it.univaq.disim.mrs.synthesis.model.MissionContext;
import it.univaq.disim.mrs.synthesis.model.InteractionMatch;
import it.univaq.disim.mrs.synthesis.planner.InteractionPlanner;
import it.univaq.disim.mrs.synthesis.model.RobotTaskMetadata;
import it.univaq.disim.mrs.synthesis.semantic.OntologyManager;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.ReceiveTask;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MultiRobotSynthesisEngineTest {
    private static final File BPMN_DIR = new File("src/main/resources/bpmn");

    @Test
    void suppliedGlobalChoreographyPassesStructuralCoherenceCheck() {
        BpmnModelInstance global = Bpmn.readModelFromFile(new File(BPMN_DIR, "GlobalMission.bpmn"));
        assertTrue(new GlobalChoreographyAnalyzer().validateStructuralCoherence(global).isEmpty(),
                "The case-study choreography should have valid endpoints, reachable flow nodes, and a route to an end event.");
    }

    @Test
    void nearbyOneToNChoreographiesPassStructuralCoherenceCheck() {
        GlobalChoreographyAnalyzer analyzer = new GlobalChoreographyAnalyzer();
        File parallelFixture = new File("src/test/resources/one-to-n/GlobalMission_1_to_N.bpmn");
        File sequentialFixture = new File("src/test/resources/one-to-n/GlobalMission_1_to_N_Sequential.bpmn");

        for (File fixture : List.of(parallelFixture, sequentialFixture)) {
            BpmnModelInstance model = Bpmn.readModelFromFile(fixture);
            assertTrue(analyzer.validateStructuralCoherence(model).isEmpty(),
                    "The nearby supported choreography should be structurally coherent: " + fixture.getName());
        }
    }

    @Test
    void globalGatewayRejectsGuardVariableWithoutDeclaredMessageSource() {
        BpmnModelInstance global = Bpmn.readModelFromFile(new File(BPMN_DIR, "GlobalMission.bpmn"));
        ((SequenceFlow) global.getModelElementById("Flow_YES")).getConditionExpression().setTextContent("${ghostFlag == true}");
        ((SequenceFlow) global.getModelElementById("Flow_NO")).getConditionExpression().setTextContent("${ghostFlag == false}");

        List<String> issues = new GlobalChoreographyAnalyzer().validateStructuralCoherence(global);
        assertTrue(issues.stream().anyMatch(issue -> issue.contains("not declared as a message name")),
                "Complementary guards must not be accepted when the choreography declares no message source for their variable.");
    }

    @Test
    void globalGatewayRejectsGuardVariableWhoseMessageHasNoMessageFlow() {
        BpmnModelInstance global = Bpmn.readModelFromFile(new File(BPMN_DIR, "GlobalMission.bpmn"));
        org.camunda.bpm.model.bpmn.instance.MessageFlow availabilityFlow =
                global.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.MessageFlow.class).stream()
                        .filter(flow -> flow.getMessage() != null
                                && "availability".equalsIgnoreCase(flow.getMessage().getName()))
                        .findFirst().orElseThrow();
        org.camunda.bpm.model.bpmn.instance.Collaboration collaboration =
                global.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Collaboration.class)
                        .iterator().next();
        collaboration.removeChildElement(availabilityFlow);

        List<String> issues = new GlobalChoreographyAnalyzer().validateStructuralCoherence(global);
        assertTrue(issues.stream().anyMatch(issue -> issue.contains("without a matching message flow")),
                "A guard must not be treated as message-sourced merely because an unused message declaration has the same name.");
    }

    @Test
    void globalCoherenceRejectsLoopWithoutSupportedRetryStructure() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"defs\" targetNamespace=\"http://example.test\">"
                + "<bpmn:message id=\"msgFlag\" name=\"flag\"/>"
                + "<bpmn:process id=\"p\" isExecutable=\"false\">"
                + "<bpmn:startEvent id=\"start\"/><bpmn:exclusiveGateway id=\"gateway\"/>"
                + "<bpmn:task id=\"loopTask\"/><bpmn:endEvent id=\"end\"/>"
                + "<bpmn:sequenceFlow id=\"f0\" sourceRef=\"start\" targetRef=\"gateway\"/>"
                + "<bpmn:sequenceFlow id=\"f1\" sourceRef=\"gateway\" targetRef=\"end\"><bpmn:conditionExpression>${flag == true}</bpmn:conditionExpression></bpmn:sequenceFlow>"
                + "<bpmn:sequenceFlow id=\"f2\" sourceRef=\"gateway\" targetRef=\"loopTask\"><bpmn:conditionExpression>${flag == false}</bpmn:conditionExpression></bpmn:sequenceFlow>"
                + "<bpmn:sequenceFlow id=\"f3\" sourceRef=\"loopTask\" targetRef=\"gateway\"/>"
                + "</bpmn:process></bpmn:definitions>";
        File file = File.createTempFile("global-loop", ".bpmn");
        try {
            Files.writeString(file.toPath(), xml);
            BpmnModelInstance model = Bpmn.readModelFromFile(file);
            List<String> issues = new GlobalChoreographyAnalyzer().validateStructuralCoherence(model);
            assertTrue(issues.stream().anyMatch(issue -> issue.contains("not covered by the supported retry pattern")),
                    "A cyclic region without a timer must not be silently accepted as a validated retry loop.");
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void localAnalyzerExtractsDeclaredInputsAndOutputs() {
        LocalProcessAnalyzer analyzer = new LocalProcessAnalyzer();
        BpmnModelInstance model = Bpmn.readModelFromFile(new File(BPMN_DIR, "Drone.bpmn"));
        List<RobotTaskMetadata> droneMetadata = analyzer.analyzeLocalModels("Drone", model);
        assertTrue(droneMetadata.stream().anyMatch(m -> "Task_Coordinate".equals(m.getTaskId())
                && "Coordinate".equals(m.getProducedDataType())));
        assertTrue(droneMetadata.stream().anyMatch(m -> "Task_SelectClosestTractor".equals(m.getTaskId())
                && "CoordinateTrattoreVicino".equals(m.getProducedDataType())));
        assertTrue(droneMetadata.stream().anyMatch(m -> "Task_CheckAvailability".equals(m.getTaskId())
                && m.getRequiredDataTypes().contains("Boolean")));

        // Task_Taglio belongs to Tagliatore.bpmn, not Drone.bpmn.
        BpmnModelInstance cutterModel = Bpmn.readModelFromFile(new File(BPMN_DIR, "Tagliatore.bpmn"));
        List<RobotTaskMetadata> cutterMetadata = analyzer.analyzeLocalModels("Tagliatore", cutterModel);
        assertTrue(cutterMetadata.stream().anyMatch(m -> "Task_Taglio".equals(m.getTaskId())
                && m.getPreconditions().contains("robot_at_target=true")
                && m.getEffects().contains("field_ready=true")),
                "The local analyzer must extract explicit behavioral contracts from the BPMN task that declares them.");
    }

    @Test
    void availabilityRetryUsesTheThesisCollectionGatewayAndReturnsAfterTimer() {
        BpmnModelInstance global = Bpmn.readModelFromFile(new File(BPMN_DIR, "GlobalMission.bpmn"));
        org.camunda.bpm.model.bpmn.instance.ParallelGateway collection =
                (org.camunda.bpm.model.bpmn.instance.ParallelGateway) global.getModelElementById("Gateway_Join_Availability");
        assertNotNull(collection, "The global choreography must represent availability collection.");
        assertEquals("Gateway_Check_Availability", collection.getOutgoing().iterator().next().getTarget().getId());
        IntermediateCatchEvent timer = (IntermediateCatchEvent) global.getModelElementById("Event_Timer_10s");
        assertNotNull(timer);
        assertEquals("PT10S", timer.getEventDefinitions().stream()
                .filter(org.camunda.bpm.model.bpmn.instance.TimerEventDefinition.class::isInstance)
                .map(org.camunda.bpm.model.bpmn.instance.TimerEventDefinition.class::cast)
                .findFirst().orElseThrow().getTimeDuration().getTextContent());
        assertEquals("Task_availability", timer.getOutgoing().iterator().next().getTarget().getId(),
                "After waiting 10 seconds, the process must refresh availability.");
        SequenceFlow availabilityToCollection = (SequenceFlow) global.getModelElementById("Flow_3");
        assertEquals("Gateway_Join_Availability", availabilityToCollection.getTarget().getId());
        SequenceFlow collectionToDecision = (SequenceFlow) global.getModelElementById("Flow_4");
        assertEquals("Gateway_Check_Availability", collectionToDecision.getTarget().getId());
        org.camunda.bpm.model.bpmn.instance.Task availability =
                (org.camunda.bpm.model.bpmn.instance.Task) global.getModelElementById("Task_availability");
        assertEquals(2, availability.getIncoming().size(),
                "The initial request and timed retry should both re-enter availability reporting.");
    }

    @Test
    void semanticSpecificationJsonDeserializesAndValidates() {
        List<DataNode> specs = new UserInputManager().loadSemanticSpecifications(new File("src/main/resources/semantics.json"));
        assertEquals(6, specs.size());
        assertTrue(specs.stream().allMatch(DataNode::hasRequiredProducerFields));
        assertTrue(specs.stream().allMatch(s -> "WEAK".equals(s.getSyntacticCompatibility())),
                "Existing entries without a mode must retain the backwards-compatible WEAK default.");
        assertNotNull(specs.stream().filter(s -> "fieldReady".equals(s.getMessageName())).findFirst().orElseThrow().getReceiverTaskId());
    }

    @Test
    void semanticMatcherRequiresDirectionalTypeAndMatchingConcept() {
        OntologyManager ontology = new OntologyManager();
        ontology.loadOntology(new File("src/main/resources/taxonomy.json"));
        AISemanticMatcher matcher = new AISemanticMatcher(ontology);
        assertTrue(matcher.isSemanticMatch("CoordinateTrattoreVicino", "selected tractor", "Coordinate", "selected tractor",
                "selected_tractor_location", "selected_tractor_location"));
        assertFalse(matcher.isSemanticMatch("Coordinate", "selected tractor", "CoordinateTrattoreVicino", "selected tractor",
                "selected_tractor_location", "selected_tractor_location"));
        assertFalse(matcher.isSemanticMatch("Boolean", "field ready", "Boolean", "digging complete",
                "field_ready", "digging_completed"));
        assertTrue(matcher.isSemanticMatch("Coordinate", "a location", "Coordinate", "a location",
                "work_area_location", "physical_location"),
                "A specific location concept may satisfy a more general location requirement.");
        assertFalse(matcher.isSemanticMatch("Coordinate", "a location", "Coordinate", "a location",
                "work_area_location", "selected_tractor_location"),
                "Sibling semantic concepts must not be treated as equivalent.");
    }

    @Test
    void fullPipelineGeneratesFreshConnectedBpmnArtifacts() throws Exception {
        MultiRobotSynthesisEngine engine = new MultiRobotSynthesisEngine();
        List<File> locals = new ArrayList<>();
        locals.add(new File(BPMN_DIR, "Drone.bpmn"));
        locals.add(new File(BPMN_DIR, "Tagliatore.bpmn"));
        locals.add(new File(BPMN_DIR, "Scavatore.bpmn"));
        locals.add(new File(BPMN_DIR, "Seminatore.bpmn"));

        MissionContext context = engine.runSynthesisPipeline(locals, new File(BPMN_DIR, "GlobalMission.bpmn"),
                new File("src/main/resources/taxonomy.json"), new File("src/main/resources/semantics.json"), false);
        assertEquals(6, context.getValidatedMatches().size(), "All six messages in the basic case study should be resolved.");
        BpmnModelInstance enhancedDrone = context.getRobotModel("Drone.bpmn");
        assertNotNull(enhancedDrone);
        assertEquals(1, enhancedDrone.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.ParallelGateway.class).size(),
                "The enhanced Drone must include the availability-collection gateway from the thesis projection.");
        assertEquals(1, enhancedDrone.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.ExclusiveGateway.class).size(),
                "The enhanced Drone must include the availability decision.");
        IntermediateCatchEvent retryTimer = enhancedDrone.getModelElementsByType(IntermediateCatchEvent.class).stream()
                .filter(event -> event.getEventDefinitions().stream()
                        .anyMatch(org.camunda.bpm.model.bpmn.instance.TimerEventDefinition.class::isInstance))
                .findFirst().orElseThrow(() -> new AssertionError("The enhanced Drone must contain the retry timer."));
        assertEquals("PT10S", retryTimer.getEventDefinitions().stream()
                .filter(org.camunda.bpm.model.bpmn.instance.TimerEventDefinition.class::isInstance)
                .map(org.camunda.bpm.model.bpmn.instance.TimerEventDefinition.class::cast)
                .findFirst().orElseThrow().getTimeDuration().getTextContent());
        assertTrue(retryTimer.getOutgoing().stream().anyMatch(flow -> flow.getTarget() instanceof ReceiveTask),
                "After the timer, the Drone must wait for a refreshed availability message.");
        Map<String, String> expectedCaseStudyInteractions = Map.of(
                "weedPosition", "Drone|Task_Coordinate|Tagliatore|Task_Movimento|Coordinate",
                "availability", "Tagliatore|Task_Autovalutazione|Drone|Task_CheckAvailability|Boolean",
                "closestTractor", "Drone|Task_SelectClosestTractor|Tagliatore|Task_Movimento|CoordinateTrattoreVicino",
                "fieldReady", "Tagliatore|Task_Taglio|Scavatore|Task_Posizionamento|Boolean",
                "digDone", "Scavatore|Task_Scavo|Seminatore|Task_Carica|Boolean",
                "seedingDone", "Seminatore|Task_Semina|Broadcast|Broadcast|Boolean"
        );
        Map<String, String> actualCaseStudyInteractions = context.getValidatedMatches().stream()
                .collect(Collectors.toMap(InteractionMatch::getMessageName,
                        match -> String.join("|", match.getSenderRobot(), match.getSenderTaskId(),
                                match.getReceiverRobot(), match.getReceiverTaskId(), match.getDataType())));
        assertEquals(expectedCaseStudyInteractions, actualCaseStudyInteractions,
                "The six resolved interactions must match the producer, consumer, and data types described in thesis §5.8.");
        assertTrue(context.getDiagnostics().isEmpty(), "The basic case should not have unresolved message diagnostics: " + context.getDiagnostics());
        assertTrue(context.getBehavioralNotes().isEmpty(),
                "The supplied case-study interactions declare explicit contracts and initial state.");
        assertTrue(Files.exists(new File("target/output/UnifiedMission.bpmn").toPath()));

        for (String robot : List.of("Drone", "Tagliatore", "Scavatore", "Seminatore")) {
            File artifact = new File("target/output/" + robot + "_enhanced.bpmn");
            assertTrue(artifact.isFile(), "Missing generated artifact " + artifact);
            BpmnModelInstance model = Bpmn.readModelFromFile(artifact);
            Process process = model.getModelElementsByType(Process.class).iterator().next();
            assertTrue(process.getFlowElements().stream().filter(e -> e instanceof SequenceFlow)
                    .map(e -> (SequenceFlow) e).allMatch(f -> f.getSource() != null && f.getTarget() != null));
            assertTrue(process.getFlowElements().stream().anyMatch(e -> e instanceof SendTask || e instanceof ReceiveTask),
                    "Expected generated communication tasks in " + robot);
            assertTrue(model.getModelElementsByType(SendTask.class).stream()
                            .allMatch(task -> task.getMessage() != null
                                    && task.getName() != null
                                    && task.getName().equals("Invia: " + task.getMessage().getName())),
                    "Every generated SendTask must reference the corresponding BPMN Message in " + robot);
            assertTrue(model.getModelElementsByType(ReceiveTask.class).stream()
                            .allMatch(task -> task.getMessage() != null
                                    && task.getName() != null
                                    && task.getName().equals("Ricevi: " + task.getMessage().getName())),
                    "Every generated ReceiveTask must reference the corresponding BPMN Message in " + robot);
        }

        BpmnModelInstance tagliatore = Bpmn.readModelFromFile(new File("target/output/Tagliatore_enhanced.bpmn"));
        org.camunda.bpm.model.bpmn.instance.FlowNode receiveWeedPosition = tagliatore.getModelElementsByType(ReceiveTask.class)
                .stream().filter(t -> t.getName() != null && t.getName().contains("weedPosition")).findFirst().orElseThrow();
        org.camunda.bpm.model.bpmn.instance.FlowNode availabilityTask =
                (org.camunda.bpm.model.bpmn.instance.FlowNode) tagliatore.getModelElementById("Task_Autovalutazione");
        org.camunda.bpm.model.bpmn.instance.FlowNode sendAvailability = tagliatore.getModelElementsByType(SendTask.class)
                .stream().filter(t -> t.getName() != null && t.getName().contains("availability")).findFirst().orElseThrow();
        assertTrue(isReachable(receiveWeedPosition, availabilityTask),
                "weedPosition must be received before the availability-producing task.");
        assertTrue(isReachable(availabilityTask, sendAvailability),
                "availability must be sent after its producing task.");
        assertFalse(isReachable(availabilityTask, receiveWeedPosition),
                "The incoming weedPosition must not remain after availability production.");

        BpmnModelInstance unified = Bpmn.readModelFromFile(new File("target/output/UnifiedMission.bpmn"));
        assertTrue(unified.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Participant.class).stream()
                .anyMatch(p -> "Broadcast".equalsIgnoreCase(p.getName())));
        assertEquals(6, unified.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.MessageFlow.class).size());
        assertEquals(6, unified.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Message.class).size(),
                "The unified collaboration must define each exchanged message exactly once.");
        assertTrue(unified.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.MessageFlow.class).stream()
                        .allMatch(flow -> flow.getMessage() != null
                                && flow.getName() != null
                                && flow.getName().equals(flow.getMessage().getName())),
                "Every collaboration MessageFlow must reference its declared BPMN Message.");
        assertTrue(unified.getModelElementsByType(SendTask.class).stream()
                        .allMatch(task -> task.getMessage() != null),
                "Every SendTask in the unified collaboration must reference a declared message.");
        assertTrue(unified.getModelElementsByType(ReceiveTask.class).stream()
                        .allMatch(task -> task.getMessage() != null),
                "Every ReceiveTask in the unified collaboration must reference a declared message.");
        assertEquals(1, unified.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Participant.class).stream()
                .filter(p -> "Global Choreography Flow".equals(p.getName())).count(),
                "The global choreography must be cloned exactly once into the unified diagram.");
        assertNotNull(unified.getModelElementById("Global_Event_Timer_10s"));
        assertNull(unified.getModelElementById("Global_Global_Event_Timer_10s"),
                "A duplicate copy of the global control flow must not be generated.");
        assertTrue(unified.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.MessageFlow.class).stream()
                .allMatch(flow -> flow.getSource() instanceof org.camunda.bpm.model.bpmn.instance.FlowNode
                        && flow.getTarget() instanceof org.camunda.bpm.model.bpmn.instance.FlowNode),
                "The drawn message-flow endpoints must be the same task nodes used by the BPMN references.");
        org.camunda.bpm.model.bpmn.instance.ExclusiveGateway globalAvailabilityGateway =
                (org.camunda.bpm.model.bpmn.instance.ExclusiveGateway) unified.getModelElementById("Global_Gateway_Check_Availability");
        assertNotNull(globalAvailabilityGateway, "Unified diagram must retain the global availability gateway.");
        org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent globalTimer =
                (org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent) unified.getModelElementById("Global_Event_Timer_10s");
        assertNotNull(globalTimer, "Unified diagram must retain the global retry timer.");
        org.camunda.bpm.model.bpmn.instance.TimerEventDefinition timerDefinition = globalTimer.getEventDefinitions().stream()
                .filter(org.camunda.bpm.model.bpmn.instance.TimerEventDefinition.class::isInstance)
                .map(org.camunda.bpm.model.bpmn.instance.TimerEventDefinition.class::cast).findFirst().orElseThrow();
        assertEquals("PT10S", timerDefinition.getTimeDuration().getTextContent());
        assertEquals("${availability == true}", unified.getModelElementById("Global_Flow_YES")
                .getChildElementsByType(org.camunda.bpm.model.bpmn.instance.FormalExpression.class).stream()
                .findFirst().orElseThrow().getTextContent());
        assertEquals("${availability == false}", unified.getModelElementById("Global_Flow_NO")
                .getChildElementsByType(org.camunda.bpm.model.bpmn.instance.FormalExpression.class).stream()
                .findFirst().orElseThrow().getTextContent());
        org.camunda.bpm.model.bpmn.instance.SequenceFlow retryFlow =
                (org.camunda.bpm.model.bpmn.instance.SequenceFlow) unified.getModelElementById("Global_Flow_Loop");
        assertEquals("Global_Task_availability", retryFlow.getTarget().getId(),
                "The timeout path must loop back to the availability task.");
        org.camunda.bpm.model.bpmn.instance.SequenceFlow unavailableFlow =
                (org.camunda.bpm.model.bpmn.instance.SequenceFlow) unified.getModelElementById("Global_Flow_NO");
        assertEquals("Global_Event_Timer_10s", unavailableFlow.getTarget().getId(),
                "The unavailable branch must enter the retry timer.");
        assertNotNull(unavailableFlow.getConditionExpression(), "The retry branch needs an explicit condition.");
        assertTrue(unavailableFlow.getConditionExpression().getTextContent().contains("availability"));
        org.camunda.bpm.model.bpmn.instance.SequenceFlow availableFlow =
                (org.camunda.bpm.model.bpmn.instance.SequenceFlow) unified.getModelElementById("Global_Flow_YES");
        assertNotNull(availableFlow.getConditionExpression(), "The success branch needs an explicit condition.");
        assertEquals("Global_Task_seedingDone", ((org.camunda.bpm.model.bpmn.instance.SequenceFlow)
                unified.getModelElementById("Global_Flow_7")).getTarget().getId());
    }



    @Test
    void missingConsumerDoesNotFallBackToAnUnrelatedTask() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        File alteredSemantics = File.createTempFile("semantics-no-consumer", ".json");
        List<DataNode> specs = mapper.readValue(new File("src/main/resources/semantics.json"),
                new TypeReference<List<DataNode>>() {});
        DataNode fieldReady = specs.stream().filter(s -> "fieldReady".equals(s.getMessageName())).findFirst().orElseThrow();
        fieldReady.setReceiverTaskId("Task_ThatDoesNotExist");
        mapper.writeValue(alteredSemantics, specs);

        List<File> locals = List.of(
                new File(BPMN_DIR, "Drone.bpmn"),
                new File(BPMN_DIR, "Tagliatore.bpmn"),
                new File(BPMN_DIR, "Scavatore.bpmn"),
                new File(BPMN_DIR, "Seminatore.bpmn"));
        MissionContext context = new MultiRobotSynthesisEngine().runSynthesisPipeline(
                locals, new File(BPMN_DIR, "GlobalMission.bpmn"),
                new File("src/main/resources/taxonomy.json"), alteredSemantics, false);
        // Removing fieldReady also removes the state fact required by the downstream digging/seeding chain.
        // A behavioral checker must not keep those dependent interactions as proven matches.
        assertEquals(3, context.getValidatedMatches().size());
        assertTrue(context.getDiagnostics().stream().anyMatch(d -> d.contains("fieldReady") && d.contains("No compatible consumer")));
        assertFalse(context.getValidatedMatches().stream().anyMatch(m -> "fieldReady".equals(m.getMessageName())),
                "The engine must not bind an unrelated fallback task when the declared receiver is missing.");
        assertTrue(alteredSemantics.delete());
    }

    @Test
    void extendedOneToNCaseCreatesParallelFanOut() throws Exception {
        File fixtureDir = new File("src/test/resources/one-to-n");
        List<File> locals = List.of(
                new File(fixtureDir, "Drone.bpmn"),
                new File(fixtureDir, "Tagliatore.bpmn"),
                new File(fixtureDir, "Scavatore.bpmn"),
                new File(fixtureDir, "Seminatore.bpmn"));
        MissionContext context = new MultiRobotSynthesisEngine().runSynthesisPipeline(
                locals, new File(fixtureDir, "GlobalMission_1_to_N.bpmn"),
                new File("src/main/resources/taxonomy.json"),
                new File(fixtureDir, "semantics_1_to_N.json"), false);

        assertEquals(3, context.getValidatedMatches().size());
        assertTrue(context.getDiagnostics().isEmpty(), "The extended case should resolve all recipients: " + context.getDiagnostics());
        assertEquals(3, context.getBehavioralNotes().size(),
                "All three recipients should retain an explicit note that state-level proof is unavailable.");
        BpmnModelInstance drone = Bpmn.readModelFromFile(new File("target/output/Drone_enhanced.bpmn"));
        Process droneProcess = drone.getModelElementsByType(Process.class).iterator().next();
        assertEquals(3, droneProcess.getFlowElements().stream().filter(e -> e instanceof SendTask).count());
        assertEquals(2, droneProcess.getFlowElements().stream().filter(e -> e instanceof org.camunda.bpm.model.bpmn.instance.ParallelGateway).count());
        for (String robot : List.of("Tagliatore", "Scavatore", "Seminatore")) {
            BpmnModelInstance model = Bpmn.readModelFromFile(new File("target/output/" + robot + "_enhanced.bpmn"));
            assertEquals(1, model.getModelElementsByType(ReceiveTask.class).size(),
                    robot + " should receive the shared MapData once");
        }
    }

    @Test
    void orderedOneToNInteractionsAreWovenSequentiallyInGlobalOrder() throws Exception {
        File fixtureDir = new File("src/test/resources/one-to-n");
        List<File> locals = List.of(
                new File(fixtureDir, "Drone.bpmn"),
                new File(fixtureDir, "Tagliatore.bpmn"),
                new File(fixtureDir, "Scavatore.bpmn"),
                new File(fixtureDir, "Seminatore.bpmn"));
        MissionContext context = new MultiRobotSynthesisEngine().runSynthesisPipeline(
                locals, new File(fixtureDir, "GlobalMission_1_to_N_Sequential.bpmn"),
                new File("src/main/resources/taxonomy.json"),
                new File(fixtureDir, "semantics_1_to_N.json"), false);

        assertEquals(3, context.getValidatedMatches().size());
        BpmnModelInstance drone = Bpmn.readModelFromFile(new File("target/output/Drone_enhanced.bpmn"));
        List<SendTask> sends = drone.getModelElementsByType(SendTask.class).stream()
                .filter(task -> task.getName() != null && task.getName().startsWith("Invia:"))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(3, sends.size());
        assertEquals(0, drone.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.ParallelGateway.class).size(),
                "A globally ordered fan-out must not be turned into a parallel split/join.");
        SendTask weed = sends.stream().filter(task -> task.getName().contains("weedPosition")).findFirst().orElseThrow();
        SendTask dig = sends.stream().filter(task -> task.getName().contains("digPlan")).findFirst().orElseThrow();
        SendTask seed = sends.stream().filter(task -> task.getName().contains("seedPlan")).findFirst().orElseThrow();
        assertTrue(isReachable(weed, dig), "weedPosition must precede digPlan.");
        assertTrue(isReachable(dig, seed), "digPlan must precede seedPlan.");
    }

    @Test
    void plannerDoesNotAssumeParallelismWhenGlobalOrderAndGatewayEvidenceAreMissing() {
        MissionContext context = new MissionContext();
        context.setGlobalChoreography(Bpmn.readModelFromFile(new File(BPMN_DIR, "GlobalMission.bpmn")));
        context.getValidatedMatches().add(new InteractionMatch("Drone", "Task_Coordinate", "Tagliatore",
                "Task_ReceiveMap", "messageNotInChoreographyA"));
        context.getValidatedMatches().add(new InteractionMatch("Drone", "Task_Coordinate", "Scavatore",
                "Task_ReceiveMap", "messageNotInChoreographyB"));

        List<it.univaq.disim.mrs.synthesis.model.InteractionPlan> plans =
                new InteractionPlanner().buildInteractionPlans(context);
        assertEquals(1, plans.size());
        assertEquals("UNRESOLVED", plans.get(0).getActivationMode(),
                "Missing global evidence must not be silently interpreted as parallel fan-out.");
    }

    @Test
    void supportedExclusiveFanOutPreservesEveryExecutableBranchCondition() throws Exception {
        String globalXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"Defs_Exclusive\" targetNamespace=\"http://example.test\">"
                + "<bpmn:message id=\"Msg_Available\" name=\"available\"/>"
                + "<bpmn:process id=\"Global\" isExecutable=\"false\">"
                + "<bpmn:startEvent id=\"Start\"/><bpmn:exclusiveGateway id=\"Choice\"/>"
                + "<bpmn:task id=\"Task_MessageA\" name=\"messageA\"/><bpmn:task id=\"Task_MessageB\" name=\"messageB\"/>"
                + "<bpmn:endEvent id=\"End\"/>"
                + "<bpmn:sequenceFlow id=\"F_Start\" sourceRef=\"Start\" targetRef=\"Choice\"/>"
                + "<bpmn:sequenceFlow id=\"F_A\" sourceRef=\"Choice\" targetRef=\"Task_MessageA\"><bpmn:conditionExpression>${available == true}</bpmn:conditionExpression></bpmn:sequenceFlow>"
                + "<bpmn:sequenceFlow id=\"F_B\" sourceRef=\"Choice\" targetRef=\"Task_MessageB\"><bpmn:conditionExpression>${available == false}</bpmn:conditionExpression></bpmn:sequenceFlow>"
                + "<bpmn:sequenceFlow id=\"F_A_End\" sourceRef=\"Task_MessageA\" targetRef=\"End\"/>"
                + "<bpmn:sequenceFlow id=\"F_B_End\" sourceRef=\"Task_MessageB\" targetRef=\"End\"/>"
                + "</bpmn:process></bpmn:definitions>";
        String localTemplate = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"Defs_LOCAL\" targetNamespace=\"http://example.test\">"
                + "<bpmn:process id=\"LOCAL\" isExecutable=\"false\">"
                + "<bpmn:startEvent id=\"Start_LOCAL\"><bpmn:outgoing>Flow_In</bpmn:outgoing></bpmn:startEvent>"
                + "<bpmn:task id=\"ANCHOR\"><bpmn:incoming>Flow_In</bpmn:incoming><bpmn:outgoing>Flow_Out</bpmn:outgoing></bpmn:task>"
                + "<bpmn:endEvent id=\"End_LOCAL\"><bpmn:incoming>Flow_Out</bpmn:incoming></bpmn:endEvent>"
                + "<bpmn:sequenceFlow id=\"Flow_In\" sourceRef=\"Start_LOCAL\" targetRef=\"ANCHOR\"/>"
                + "<bpmn:sequenceFlow id=\"Flow_Out\" sourceRef=\"ANCHOR\" targetRef=\"End_LOCAL\"/>"
                + "</bpmn:process></bpmn:definitions>";
        File globalFile = File.createTempFile("exclusive-global", ".bpmn");
        File droneFile = File.createTempFile("exclusive-drone", ".bpmn");
        File aFile = File.createTempFile("exclusive-a", ".bpmn");
        File bFile = File.createTempFile("exclusive-b", ".bpmn");
        Files.writeString(globalFile.toPath(), globalXml);
        Files.writeString(droneFile.toPath(), localTemplate.replace("ANCHOR", "Task_Produce"));
        Files.writeString(aFile.toPath(), localTemplate.replace("ANCHOR", "Task_ConsumerA"));
        Files.writeString(bFile.toPath(), localTemplate.replace("ANCHOR", "Task_ConsumerB"));
        try {
            MissionContext context = new MissionContext();
            context.setGlobalChoreography(Bpmn.readModelFromFile(globalFile));
            context.addRobotModel("Drone", Bpmn.readModelFromFile(droneFile));
            context.addRobotModel("RobotA", Bpmn.readModelFromFile(aFile));
            context.addRobotModel("RobotB", Bpmn.readModelFromFile(bFile));
            context.getValidatedMatches().add(new InteractionMatch("Drone", "Task_Produce", "RobotA",
                    "Task_ConsumerA", "messageA"));
            context.getValidatedMatches().add(new InteractionMatch("Drone", "Task_Produce", "RobotB",
                    "Task_ConsumerB", "messageB"));

            List<it.univaq.disim.mrs.synthesis.model.InteractionPlan> plans =
                    new InteractionPlanner().buildInteractionPlans(context);
            assertEquals(1, plans.size());
            assertEquals("EXCLUSIVE", plans.get(0).getActivationMode());
            assertEquals(2, plans.get(0).getReceiverMatches().stream()
                    .filter(match -> match.getBranchConditionExpression() != null).count());

            new it.univaq.disim.mrs.synthesis.weaver.BpmnFlowWeaver().weaveInteractions(context, plans);
            BpmnModelInstance drone = context.getRobotModel("Drone");
            assertEquals(1, drone.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.ExclusiveGateway.class).size());
            List<SequenceFlow> guardedFlows = drone.getModelElementsByType(SequenceFlow.class).stream()
                    .filter(flow -> flow.getSource() instanceof org.camunda.bpm.model.bpmn.instance.ExclusiveGateway)
                    .collect(java.util.stream.Collectors.toList());
            assertEquals(2, guardedFlows.size());
            assertTrue(guardedFlows.stream().allMatch(flow -> flow.getConditionExpression() != null
                    && flow.getConditionExpression().getTextContent() != null
                    && !flow.getConditionExpression().getTextContent().trim().isEmpty()));
            assertTrue(guardedFlows.stream().anyMatch(flow -> flow.getConditionExpression().getTextContent().contains("available == true")));
            assertTrue(guardedFlows.stream().anyMatch(flow -> flow.getConditionExpression().getTextContent().contains("available == false")));
        } finally {
            assertTrue(globalFile.delete());
            assertTrue(droneFile.delete());
            assertTrue(aFile.delete());
            assertTrue(bFile.delete());
        }
    }

    @Test
    void plannerDoesNotInferParallelismWhenTargetsShareTheSameGatewayBranch() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"Defs_BranchGuard\" targetNamespace=\"http://example.test\">\n" +
                "  <bpmn:process id=\"Process_BranchGuard\" isExecutable=\"false\">\n" +
                "    <bpmn:startEvent id=\"Start\"/>\n" +
                "    <bpmn:parallelGateway id=\"Gateway_Parallel\"/>\n" +
                "    <bpmn:eventBasedGateway id=\"Gateway_EventChoice\"/>\n" +
                "    <bpmn:task id=\"Task_alpha\"/>\n" +
                "    <bpmn:task id=\"Task_beta\"/>\n" +
                "    <bpmn:task id=\"Task_Other\"/>\n" +
                "    <bpmn:endEvent id=\"End_alpha\"/>\n" +
                "    <bpmn:endEvent id=\"End_beta\"/>\n" +
                "    <bpmn:endEvent id=\"End_Other\"/>\n" +
                "    <bpmn:sequenceFlow id=\"F1\" sourceRef=\"Start\" targetRef=\"Gateway_Parallel\"/>\n" +
                "    <bpmn:sequenceFlow id=\"F2\" sourceRef=\"Gateway_Parallel\" targetRef=\"Gateway_EventChoice\"/>\n" +
                "    <bpmn:sequenceFlow id=\"F3\" sourceRef=\"Gateway_EventChoice\" targetRef=\"Task_alpha\"/>\n" +
                "    <bpmn:sequenceFlow id=\"F4\" sourceRef=\"Gateway_EventChoice\" targetRef=\"Task_beta\"/>\n" +
                "    <bpmn:sequenceFlow id=\"F5\" sourceRef=\"Task_alpha\" targetRef=\"End_alpha\"/>\n" +
                "    <bpmn:sequenceFlow id=\"F6\" sourceRef=\"Task_beta\" targetRef=\"End_beta\"/>\n" +
                "    <bpmn:sequenceFlow id=\"F7\" sourceRef=\"Gateway_Parallel\" targetRef=\"Task_Other\"/>\n" +
                "    <bpmn:sequenceFlow id=\"F8\" sourceRef=\"Task_Other\" targetRef=\"End_Other\"/>\n" +
                "  </bpmn:process>\n" +
                "</bpmn:definitions>\n";
        File bpmn = File.createTempFile("parallel-branch-guard", ".bpmn");
        Files.writeString(bpmn.toPath(), xml);
        try {
            MissionContext context = new MissionContext();
            context.setGlobalChoreography(Bpmn.readModelFromFile(bpmn));
            context.getValidatedMatches().add(new InteractionMatch("Drone", "Task_Coordinate", "RobotA",
                    "Task_Consumer", "alpha"));
            context.getValidatedMatches().add(new InteractionMatch("Drone", "Task_Coordinate", "RobotB",
                    "Task_Consumer", "beta"));

            List<it.univaq.disim.mrs.synthesis.model.InteractionPlan> plans =
                    new InteractionPlanner().buildInteractionPlans(context);
            assertEquals(1, plans.size());
            assertEquals("UNRESOLVED", plans.get(0).getActivationMode(),
                    "A shared upstream parallel gateway is insufficient when both targets are on the same branch.");
        } finally {
            assertTrue(bpmn.delete());
        }
    }

    @Test
    void localAnalyzerAlsoExtractsMetadataFromUserTasks() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "                  xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\"\n" +
                "                  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "                  id=\"Definitions_Test\" targetNamespace=\"http://example.test\">\n" +
                "  <bpmn:process id=\"Process_Test\" isExecutable=\"false\">\n" +
                "    <bpmn:startEvent id=\"Start_Test\" />\n" +
                "    <bpmn:userTask id=\"User_Review\" name=\"Review result\">\n" +
                "      <bpmn:extensionElements>\n" +
                "        <camunda:inputOutput>\n" +
                "          <camunda:inputParameter name=\"requiredData\">Coordinate</camunda:inputParameter>\n" +
                "          <camunda:outputParameter name=\"producedData\">ReviewedCoordinate</camunda:outputParameter>\n" +
                "        </camunda:inputOutput>\n" +
                "      </bpmn:extensionElements>\n" +
                "    </bpmn:userTask>\n" +
                "    <bpmn:endEvent id=\"End_Test\" />\n" +
                "    <bpmn:sequenceFlow id=\"Flow_1\" sourceRef=\"Start_Test\" targetRef=\"User_Review\" />\n" +
                "    <bpmn:sequenceFlow id=\"Flow_2\" sourceRef=\"User_Review\" targetRef=\"End_Test\" />\n" +
                "  </bpmn:process>\n" +
                "</bpmn:definitions>\n";
        File bpmn = File.createTempFile("user-task-metadata", ".bpmn");
        Files.writeString(bpmn.toPath(), xml);
        try {
            BpmnModelInstance model = Bpmn.readModelFromFile(bpmn);
            List<RobotTaskMetadata> metadata = new LocalProcessAnalyzer().analyzeLocalModels("RobotA", model);
            RobotTaskMetadata review = metadata.stream()
                    .filter(item -> "User_Review".equals(item.getTaskId())).findFirst().orElseThrow();
            assertEquals("ReviewedCoordinate", review.getProducedDataType());
            assertTrue(review.getRequiredDataTypes().contains("Coordinate"));
        } finally {
            assertTrue(bpmn.delete());
        }
    }

    @Test
    void behavioralCheckerAcceptsTaskReachableFromAnyStartEvent() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "                  id=\"Definitions_MultiStart\" targetNamespace=\"http://example.test\">\n" +
                "  <bpmn:process id=\"Process_MultiStart\" isExecutable=\"false\">\n" +
                "    <bpmn:startEvent id=\"Start_Unrelated\" />\n" +
                "    <bpmn:startEvent id=\"Start_Valid\" />\n" +
                "    <bpmn:task id=\"Task_Consumer\" />\n" +
                "    <bpmn:endEvent id=\"End_Valid\" />\n" +
                "    <bpmn:sequenceFlow id=\"Flow_Valid_1\" sourceRef=\"Start_Valid\" targetRef=\"Task_Consumer\" />\n" +
                "    <bpmn:sequenceFlow id=\"Flow_Valid_2\" sourceRef=\"Task_Consumer\" targetRef=\"End_Valid\" />\n" +
                "  </bpmn:process>\n" +
                "</bpmn:definitions>\n";
        File bpmn = File.createTempFile("behavior-multi-start", ".bpmn");
        Files.writeString(bpmn.toPath(), xml);
        try {
            BpmnModelInstance model = Bpmn.readModelFromFile(bpmn);
            RobotTaskMetadata consumer = new RobotTaskMetadata();
            consumer.setTaskId("Task_Consumer");
            consumer.getRequiredDataTypes().add("Coordinate");
            assertTrue(new it.univaq.disim.mrs.synthesis.analyzer.BehavioralCompatibilityTester()
                    .canConsume(model, consumer, "Coordinate", new OntologyManager()));
        } finally {
            assertTrue(bpmn.delete());
        }
    }

    @Test
    void invalidSemanticSpecificationFailsInsteadOfReturningEmptyList() throws Exception {
        File invalid = File.createTempFile("semantics-invalid", ".json");
        Files.writeString(invalid.toPath(), "[{\"messageName\":\"m\"}]");
        assertThrows(IllegalArgumentException.class, () -> new UserInputManager().loadSemanticSpecifications(invalid));
        assertTrue(invalid.delete());
    }

    @Test
    void demonstrateLlmUsageMatchesOntologyWhenSemanticConceptsAreOmitted() throws Exception {
        List<File> locals = new ArrayList<>();
        locals.add(new File(BPMN_DIR, "Drone.bpmn"));
        locals.add(new File(BPMN_DIR, "Tagliatore.bpmn"));
        locals.add(new File(BPMN_DIR, "Scavatore.bpmn"));
        locals.add(new File(BPMN_DIR, "Seminatore.bpmn"));

        // 1. Baseline: execution using domain ontology semantic concepts
        MultiRobotSynthesisEngine baselineEngine = new MultiRobotSynthesisEngine();
        MissionContext baselineContext = baselineEngine.runSynthesisPipeline(locals,
                new File(BPMN_DIR, "GlobalMission.bpmn"),
                new File("src/main/resources/taxonomy.json"),
                new File("src/main/resources/semantics.json"), false);
        assertEquals(6, baselineContext.getValidatedMatches().size());

        // 2. LLM Alternative: Create semantics fixture where semanticConcept and receiverSemanticConcept are omitted
        List<DataNode> specs = new UserInputManager().loadSemanticSpecifications(new File("src/main/resources/semantics.json"));
        for (DataNode spec : specs) {
            spec.setSemanticConcept(null);
            spec.setReceiverSemanticConcept(null);
        }
        File noConceptsFile = File.createTempFile("semantics-no-concepts", ".json");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(noConceptsFile, specs);

        try {
            // Track LLM invocations to verify the LLM branch is actively used
            java.util.concurrent.atomic.AtomicInteger llmInvocations = new java.util.concurrent.atomic.AtomicInteger(0);
            it.univaq.disim.mrs.synthesis.ai.LLMClient mockLLM = new it.univaq.disim.mrs.synthesis.ai.LLMClient(
                    (prodType, prodDesc, reqType, reqDesc) -> {
                        llmInvocations.incrementAndGet();
                        // Simulates LLM natural language reasoning confirming compatibility
                        return true;
                    });

            OntologyManager ontology = new OntologyManager();
            ontology.loadOntology(new File("src/main/resources/taxonomy.json"));
            AISemanticMatcher llmMatcher = new AISemanticMatcher(ontology, mockLLM);

            MultiRobotSynthesisEngine llmEngine = new MultiRobotSynthesisEngine(llmMatcher);
            MissionContext llmContext = llmEngine.runSynthesisPipeline(locals,
                    new File(BPMN_DIR, "GlobalMission.bpmn"),
                    new File("src/main/resources/taxonomy.json"),
                    noConceptsFile, false);

            assertTrue(llmInvocations.get() > 0, "LLM must be actively invoked when semantic concepts are omitted.");
            assertEquals(6, llmContext.getValidatedMatches().size(), "LLM path must validate all 6 interactions.");

            // Verify exact equivalence of validated interactions between Ontology mode and LLM mode
            List<String> baselineMatches = baselineContext.getValidatedMatches().stream()
                    .map(m -> m.getMessageName() + ":" + m.getSenderRobot() + "->" + m.getReceiverRobot())
                    .sorted().collect(Collectors.toList());
            List<String> llmMatches = llmContext.getValidatedMatches().stream()
                    .map(m -> m.getMessageName() + ":" + m.getSenderRobot() + "->" + m.getReceiverRobot())
                    .sorted().collect(Collectors.toList());
            assertEquals(baselineMatches, llmMatches,
                    "Synthesized interactions via LLM must be identical to ontology-based synthesis.");
        } finally {
            assertTrue(noConceptsFile.delete());
        }
    }

    private static boolean isReachable(org.camunda.bpm.model.bpmn.instance.FlowNode start,
                                       org.camunda.bpm.model.bpmn.instance.FlowNode target) {
        java.util.Deque<org.camunda.bpm.model.bpmn.instance.FlowNode> pending = new java.util.ArrayDeque<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            org.camunda.bpm.model.bpmn.instance.FlowNode current = pending.removeFirst();
            if (current.getId().equals(target.getId())) return true;
            if (!visited.add(current.getId())) continue;
            current.getOutgoing().forEach(flow -> {
                if (flow.getTarget() != null) pending.addLast(flow.getTarget());
            });
        }
        return false;
    }

}
