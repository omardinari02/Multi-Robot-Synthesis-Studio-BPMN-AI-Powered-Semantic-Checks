package it.univaq.disim.mrs.synthesis.adapter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.camunda.bpm.model.bpmn.instance.Participant;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class ChoreographyAdapterTest {

    private ChoreographyAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ChoreographyAdapter();
    }

    @Test
    void testParseChoreoMetaWithAllAttributes() {
        String taskName = "Invia posizione erbacce [msg: weedPosition | ack: weedAck]\n(Drone -> Tagliatore) |||";
        ChoreographyAdapter.ChoreoMeta meta = adapter.parseChoreoMeta(taskName, "Task_1");

        assertNotNull(meta);
        assertEquals("Drone", meta.getSender());
        assertEquals("Tagliatore", meta.getReceiver());
        assertEquals("weedPosition", meta.getTopMsg());
        assertEquals("weedAck", meta.getBottomMsg());
        assertTrue(meta.isParallel());
        assertEquals("Invia posizione erbacce", meta.getAction());
    }

    @Test
    void testParseChoreoMetaSequentialOnlyTopMsg() {
        String taskName = "Segnale terreno pronto [msg: fieldReady]\n(Tagliatore -> Scavatore)";
        ChoreographyAdapter.ChoreoMeta meta = adapter.parseChoreoMeta(taskName, "Task_2");

        assertNotNull(meta);
        assertEquals("Tagliatore", meta.getSender());
        assertEquals("Scavatore", meta.getReceiver());
        assertEquals("fieldReady", meta.getTopMsg());
        assertNull(meta.getBottomMsg());
        assertFalse(meta.isParallel());
    }

    @Test
    void testAdaptModelCreatesCollaborationAndMessageFlows(@TempDir Path tempDir) {
        // Creazione di un modello BPMN minimale con un task in stile Camunda plugin
        BpmnModelInstance model = Bpmn.createExecutableProcess("Process_Test")
                .startEvent("Start")
                .userTask("Task_Test")
                .name("Interazione [msg: weedCoord | ack: coordAck]\n(Drone -> Tagliatore) |||")
                .endEvent("End")
                .done();

        boolean adapted = adapter.adaptModel(model);
        assertTrue(adapted, "Il modello avrebbe dovuto essere adattato");

        // Verifica che siano stati creati i MessageFlow
        Collection<MessageFlow> flows = model.getModelElementsByType(MessageFlow.class);
        assertEquals(2, flows.size(), "Dovevano essere generati 2 MessageFlow (andata e risposta ACK)");

        Collection<Participant> participants = model.getModelElementsByType(Participant.class);
        assertEquals(2, participants.size(), "Dovevano essere generati 2 Participant (Drone e Tagliatore)");

        Collection<Message> messages = model.getModelElementsByType(Message.class);
        assertEquals(2, messages.size(), "Dovevano essere generati i messaggi weedCoord e coordAck");

        // Verifica normalizzazione del nome del task
        Task task = model.getModelElementById("Task_Test");
        assertEquals("Interazione\n(Drone -> Tagliatore)", task.getName());
    }

    @Test
    void testAdaptModelConvertsFeelExpressionsToJuel() {
        BpmnModelInstance model = Bpmn.createExecutableProcess("Process_Feel")
                .startEvent("Start")
                .exclusiveGateway("Gateway_Check")
                .condition("si", "=availability == true")
                .userTask("Task_1")
                .endEvent("End")
                .done();

        boolean adapted = adapter.adaptModel(model);
        assertTrue(adapted);

        org.camunda.bpm.model.bpmn.instance.SequenceFlow flow = model.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.SequenceFlow.class)
                .stream().filter(sf -> sf.getConditionExpression() != null).findFirst().orElse(null);
        assertNotNull(flow);
        assertEquals("${availability == true}", flow.getConditionExpression().getTextContent());
    }

    @Test
    void testAdaptUserChoreographyDiagram() {
        File userChoreo = new File("src/main/resources/bpmn/GlobalMission.bpmn");
        assertTrue(userChoreo.exists(), "Il file GlobalMission.bpmn deve esistere");

        File adapted = adapter.adapt(userChoreo);
        assertNotNull(adapted);
        assertTrue(adapted.exists());

        BpmnModelInstance adaptedModel = Bpmn.readModelFromFile(adapted);
        Collection<MessageFlow> flows = adaptedModel.getModelElementsByType(MessageFlow.class);
        assertFalse(flows.isEmpty(), "L'adapter deve aver generato i MessageFlow per il file utente");
        System.out.println("Trovati " + flows.size() + " MessageFlow generati per GlobalMission_Choreography.bpmn");

        it.univaq.disim.mrs.synthesis.analyzer.GlobalChoreographyAnalyzer analyzer = new it.univaq.disim.mrs.synthesis.analyzer.GlobalChoreographyAnalyzer();
        java.util.List<String> issues = analyzer.validateStructuralCoherence(adaptedModel);
        System.out.println("Diagnostic issues: " + issues);
        assertTrue(issues.isEmpty(), "Il modello adattato deve superare la validazione strutturale: " + issues);

        // Verifica che il motore di sintesi completo riesca ad eseguire la pipeline su questo modello
        it.univaq.disim.mrs.synthesis.core.MultiRobotSynthesisEngine engine = new it.univaq.disim.mrs.synthesis.core.MultiRobotSynthesisEngine();
        java.util.List<File> locals = java.util.List.of(
                new File("src/main/resources/bpmn/Drone.bpmn"),
                new File("src/main/resources/bpmn/Tagliatore.bpmn"),
                new File("src/main/resources/bpmn/Scavatore.bpmn"),
                new File("src/main/resources/bpmn/Seminatore.bpmn")
        );
        it.univaq.disim.mrs.synthesis.model.MissionContext context = engine.runSynthesisPipeline(
                locals, adapted,
                new File("src/main/resources/taxonomy.json"),
                new File("src/main/resources/semantics.json"),
                new File("src/main/resources/behavioral-state.json"),
                false
        );
        assertNotNull(context);
        assertEquals(6, context.getValidatedMatches().size(), "Tutte le 6 interazioni devono essere validate con successo!");
        System.out.println("Sintesi completata con successo sul modello utente: " + context.getValidatedMatches().size() + " interazioni validate!");
    }
}
