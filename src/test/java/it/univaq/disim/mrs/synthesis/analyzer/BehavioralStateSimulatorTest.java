package it.univaq.disim.mrs.synthesis.analyzer;

import it.univaq.disim.mrs.synthesis.model.RobotTaskMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BehavioralStateSimulatorTest {
    private final BehavioralStateSimulator simulator = new BehavioralStateSimulator();

    @Test
    void producerEffectCanSatisfyConsumerPrecondition() {
        RobotTaskMetadata producer = task("producer");
        producer.getEffects().add("field_ready=true");
        RobotTaskMetadata consumer = task("consumer");
        consumer.getPreconditions().add("field_ready=true");
        assertEquals(BehavioralStateSimulator.Status.PASS,
                simulator.simulate(List.of(producer, consumer), Map.of()).getStatus());
    }

    @Test
    void incompatibleStateRejectsCandidate() {
        RobotTaskMetadata producer = task("producer");
        producer.getEffects().add("field_ready=false");
        RobotTaskMetadata consumer = task("consumer");
        consumer.getPreconditions().add("field_ready=true");
        assertEquals(BehavioralStateSimulator.Status.FAIL,
                simulator.simulate(List.of(producer, consumer), Map.of()).getStatus());
    }

    @Test
    void explicitInitialStateSatisfiesDeclaredMissionPrecondition() {
        RobotTaskMetadata task = task("mission-task");
        task.getPreconditions().add("mission_started=true");
        task.getEffects().add("task_completed=true");
        assertEquals(BehavioralStateSimulator.Status.PASS,
                simulator.simulate(List.of(task), Map.of("mission_started", "true")).getStatus());
    }

    @Test
    void missingContractStateIsUnresolvedRatherThanGuessed() {
        RobotTaskMetadata consumer = task("consumer");
        consumer.getPreconditions().add("field_ready=true");
        assertEquals(BehavioralStateSimulator.Status.UNRESOLVED,
                simulator.simulate(List.of(consumer), Map.of()).getStatus());
    }

    @Test
    void absentContractsAreNotPresentedAsBehavioralProof() {
        assertEquals(BehavioralStateSimulator.Status.UNRESOLVED,
                simulator.simulate(List.of(task("consumer")), Map.of()).getStatus());
    }

    @Test
    void contradictoryEffectsInOneTaskAreRejected() {
        RobotTaskMetadata task = task("contradictory-task");
        task.getEffects().add("field_ready=true");
        task.getEffects().add("FIELD_READY=false");
        assertEquals(BehavioralStateSimulator.Status.FAIL,
                simulator.simulate(List.of(task), Map.of()).getStatus());
    }

    @Test
    void contradictoryPreconditionsInOneTaskAreRejected() {
        RobotTaskMetadata task = task("contradictory-task");
        task.getPreconditions().add("field_ready=true");
        task.getPreconditions().add("field_ready=false");
        assertEquals(BehavioralStateSimulator.Status.FAIL,
                simulator.simulate(List.of(task), Map.of("field_ready", "true")).getStatus());
    }

    @Test
    void contradictoryInitialStateKeysAreRejectedAfterNormalization() {
        RobotTaskMetadata consumer = task("consumer");
        consumer.getPreconditions().add("field_ready=true");
        Map<String, String> initialState = new java.util.LinkedHashMap<>();
        initialState.put("field_ready", "true");
        initialState.put(" FIELD_READY ", "false");
        assertEquals(BehavioralStateSimulator.Status.FAIL,
                simulator.simulate(List.of(consumer), initialState).getStatus());
    }

    @Test
    void malformedInitialStateIsUnresolvedInsteadOfSilentlyIgnored() {
        RobotTaskMetadata consumer = task("consumer");
        consumer.getPreconditions().add("field_ready=true");
        Map<String, String> initialState = new java.util.HashMap<>();
        initialState.put("field_ready", null);
        assertEquals(BehavioralStateSimulator.Status.UNRESOLVED,
                simulator.simulate(List.of(consumer), initialState).getStatus());
    }

    @Test
    void nullTaskMetadataIsUnresolvedInsteadOfThrowing() {
        assertEquals(BehavioralStateSimulator.Status.UNRESOLVED,
                simulator.simulate(java.util.Arrays.asList((RobotTaskMetadata) null), Map.of()).getStatus());
    }

    private RobotTaskMetadata task(String id) {
        RobotTaskMetadata task = new RobotTaskMetadata();
        task.setTaskId(id);
        return task;
    }
}
