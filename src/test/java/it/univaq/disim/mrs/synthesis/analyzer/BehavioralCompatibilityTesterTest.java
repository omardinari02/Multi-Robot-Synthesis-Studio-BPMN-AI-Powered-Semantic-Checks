package it.univaq.disim.mrs.synthesis.analyzer;

import it.univaq.disim.mrs.synthesis.model.RobotTaskMetadata;
import it.univaq.disim.mrs.synthesis.semantic.OntologyManager;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BehavioralCompatibilityTesterTest {
    private final BehavioralCompatibilityTester tester = new BehavioralCompatibilityTester();

    @Test
    void acceptsConsumerWhenEveryContinuationTerminates() throws Exception {
        String xml = model("<bpmn:sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"consumer\"/>"
                + "<bpmn:sequenceFlow id=\"f2\" sourceRef=\"consumer\" targetRef=\"end\"/>");
        assertEquals(BehavioralCompatibilityTester.Status.PASS, assessModel(xml).getStatus());
    }

    @Test
    void rejectsConsumerWhenOneBranchDeadEnds() throws Exception {
        String xml = model("<bpmn:sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"consumer\"/>"
                + "<bpmn:sequenceFlow id=\"f2\" sourceRef=\"consumer\" targetRef=\"end\"/>"
                + "<bpmn:sequenceFlow id=\"f3\" sourceRef=\"consumer\" targetRef=\"deadEnd\"/>",
                "<bpmn:task id=\"deadEnd\" name=\"Dead end\"/>");
        assertEquals(BehavioralCompatibilityTester.Status.FAIL, assessModel(xml).getStatus());
    }

    @Test
    void acceptsLoopWithExitToEndEventWithoutFailingOnCycles() throws Exception {
        String xml = model("<bpmn:sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"consumer\"/>"
                + "<bpmn:sequenceFlow id=\"f2\" sourceRef=\"consumer\" targetRef=\"end\"/>"
                + "<bpmn:sequenceFlow id=\"f3\" sourceRef=\"consumer\" targetRef=\"loopTask\"/>"
                + "<bpmn:sequenceFlow id=\"f4\" sourceRef=\"loopTask\" targetRef=\"consumer\"/>",
                "<bpmn:task id=\"loopTask\" name=\"Loop task\"/>");
        assertEquals(BehavioralCompatibilityTester.Status.PASS, assessModel(xml).getStatus());
    }

    @Test
    void enumeratesEveryTaskOnTheContinuationPath() throws Exception {
        String xml = model("<bpmn:sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"consumer\"/>"
                + "<bpmn:sequenceFlow id=\"f2\" sourceRef=\"consumer\" targetRef=\"downstream\"/>"
                + "<bpmn:sequenceFlow id=\"f3\" sourceRef=\"downstream\" targetRef=\"end\"/>",
                "<bpmn:task id=\"downstream\" name=\"Downstream task\"/>");
        File file = File.createTempFile("behavior-paths", ".bpmn");
        try {
            Files.writeString(file.toPath(), xml);
            BpmnModelInstance instance = Bpmn.readModelFromFile(file);
            RobotTaskMetadata consumer = task("consumer");
            RobotTaskMetadata downstream = task("downstream");
            BehavioralCompatibilityTester.ContinuationPaths paths = tester.continuationPaths(
                    instance, "consumer", java.util.List.of(consumer, downstream), 32);
            assertEquals(BehavioralCompatibilityTester.Status.PASS, paths.getStatus());
            assertEquals(1, paths.getPaths().size());
            assertEquals(java.util.List.of("consumer", "downstream"), paths.getPaths().get(0).stream()
                    .map(RobotTaskMetadata::getTaskId).collect(java.util.stream.Collectors.toList()));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void enumeratesProducerPrefixFromStartToTargetTask() throws Exception {
        String xml = model("<bpmn:sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"consumer\"/>"
                + "<bpmn:sequenceFlow id=\"f2\" sourceRef=\"consumer\" targetRef=\"downstream\"/>"
                + "<bpmn:sequenceFlow id=\"f3\" sourceRef=\"downstream\" targetRef=\"end\"/>",
                "<bpmn:task id=\"downstream\" name=\"Producer task\"/>");
        File file = File.createTempFile("behavior-prefixes", ".bpmn");
        try {
            Files.writeString(file.toPath(), xml);
            BpmnModelInstance instance = Bpmn.readModelFromFile(file);
            BehavioralCompatibilityTester.ContinuationPaths paths = tester.prefixPaths(
                    instance, "downstream", java.util.List.of(task("consumer"), task("downstream")), 32);
            assertEquals(BehavioralCompatibilityTester.Status.PASS, paths.getStatus());
            assertEquals(1, paths.getPaths().size());
            assertEquals(java.util.List.of("consumer", "downstream"), paths.getPaths().get(0).stream()
                    .map(RobotTaskMetadata::getTaskId).collect(java.util.stream.Collectors.toList()));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    private RobotTaskMetadata task(String id) {
        RobotTaskMetadata metadata = new RobotTaskMetadata();
        metadata.setTaskId(id);
        return metadata;
    }

    private BehavioralCompatibilityTester.Assessment assessModel(String xml) throws Exception {
        File file = File.createTempFile("behavior-assessment", ".bpmn");
        try {
            Files.writeString(file.toPath(), xml);
            BpmnModelInstance instance = Bpmn.readModelFromFile(file);
            RobotTaskMetadata consumer = new RobotTaskMetadata();
            consumer.setTaskId("consumer");
            consumer.getRequiredDataTypes().add("Coordinate");
            return tester.assess(instance, consumer, "Coordinate", new OntologyManager());
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    private String model(String flows, String... extraNodes) {
        StringBuilder nodes = new StringBuilder();
        for (String node : extraNodes) nodes.append(node);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " id=\"defs\" targetNamespace=\"http://example.test\">"
                + "<bpmn:process id=\"process\" isExecutable=\"false\">"
                + "<bpmn:startEvent id=\"start\"/><bpmn:task id=\"consumer\"/>"
                + "<bpmn:endEvent id=\"end\"/>" + nodes + flows
                + "</bpmn:process></bpmn:definitions>";
    }
}
