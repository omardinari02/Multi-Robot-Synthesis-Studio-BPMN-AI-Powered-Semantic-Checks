package it.univaq.disim.mrs.synthesis.analyzer;

import it.univaq.disim.mrs.synthesis.model.RobotTaskMetadata;
import it.univaq.disim.mrs.synthesis.semantic.OntologyManager;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.Task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;

/**
 * Analisi di Correttezza Comportamentale (Tesi, Sez. 5.4 e 5.6).
 * Valida i vincoli comportamentali formali per ogni interazione candidata:
 * - Livello A: Raggiungibilità del consumer dallo StartEvent.
 * - Livello B: Terminazione verso EndEvent (assenza di vicoli ciechi / dead-end).
 * - Enumerazione dei prefissi producer e continuazioni consumer per l'analisi dello stato (Livello D).
 */
public class BehavioralCompatibilityTester {
    public enum Status { PASS, FAIL, UNRESOLVED }

    public static final class Assessment {
        private final Status status;
        private final String explanation;

        private Assessment(Status status, String explanation) {
            this.status = status;
            this.explanation = explanation;
        }

        public Status getStatus() { return status; }
        public String getExplanation() { return explanation; }
    }

    public static final class ContinuationPaths {
        private final Status status;
        private final String explanation;
        private final List<List<RobotTaskMetadata>> paths;

        private ContinuationPaths(Status status, String explanation, List<List<RobotTaskMetadata>> paths) {
            this.status = status;
            this.explanation = explanation;
            this.paths = paths;
        }

        public Status getStatus() { return status; }
        public String getExplanation() { return explanation; }
        public List<List<RobotTaskMetadata>> getPaths() { return Collections.unmodifiableList(paths); }
    }

    /** Backward-compatible boolean API: only a fully passing assessment is true. */
    public boolean canConsume(BpmnModelInstance model, RobotTaskMetadata consumer,
                              String producedType, OntologyManager ontology) {
        return assess(model, consumer, producedType, ontology).getStatus() == Status.PASS;
    }

    public Assessment assess(BpmnModelInstance model, RobotTaskMetadata consumer,
                            String producedType, OntologyManager ontology) {
        if (model == null || consumer == null || ontology == null) {
            return result(Status.UNRESOLVED, "The model, consumer metadata, or ontology is missing.");
        }
        boolean inputSatisfied = consumer.getRequiredDataTypes().stream()
                .anyMatch(required -> ontology.isCompatible(producedType, required));
        if (!inputSatisfied) {
            return result(Status.FAIL, "The produced data type does not satisfy any declared consumer input.");
        }

        Object element = model.getModelElementById(consumer.getTaskId());
        if (!(element instanceof FlowNode)) {
            return result(Status.FAIL, "The consumer task ID does not resolve to a BPMN flow node.");
        }
        FlowNode task = (FlowNode) element;
        Collection<StartEvent> starts = model.getModelElementsByType(StartEvent.class);
        Collection<EndEvent> ends = model.getModelElementsByType(EndEvent.class);
        if (starts.isEmpty() || ends.isEmpty()) {
            return result(Status.FAIL, "The process must declare at least one start event and one end event.");
        }
        boolean reachableFromStart = starts.stream().anyMatch(start -> reachable(model, start, task));
        if (!reachableFromStart) {
            return result(Status.FAIL, "No declared start event can reach the consumer task.");
        }

        List<SequenceFlow> flows = new ArrayList<>(model.getModelElementsByType(SequenceFlow.class));
        Map<String, List<FlowNode>> outgoing = buildAdjacency(flows);
        Map<String, Status> memo = new HashMap<>();
        Set<String> visiting = new HashSet<>();
        Status continuation = assessTermination(task, outgoing, ends, memo, visiting);
        if (continuation == Status.FAIL) {
            return result(Status.FAIL, "At least one reachable continuation from the consumer ends in a dead end or invalid flow.");
        }
        return result(Status.PASS, "Input type is compatible, the consumer is reachable from a start event, and all reachable continuations lead to an end event without dead ends.");
    }

    /** Enumerates every finite local task prefix from a start event through the target task. */
    public ContinuationPaths prefixPaths(BpmnModelInstance model, String targetTaskId,
                                         List<RobotTaskMetadata> metadata, int maxPaths) {
        if (model == null || targetTaskId == null || metadata == null || maxPaths < 1) {
            return new ContinuationPaths(Status.UNRESOLVED,
                    "Missing model, target task ID, metadata, or positive path limit.", new ArrayList<>());
        }
        Object element = model.getModelElementById(targetTaskId);
        if (!(element instanceof FlowNode)) {
            return new ContinuationPaths(Status.FAIL, "Target task ID does not resolve to a BPMN flow node.", new ArrayList<>());
        }
        List<SequenceFlow> flows = new ArrayList<>(model.getModelElementsByType(SequenceFlow.class));
        Map<String, List<FlowNode>> outgoing = buildAdjacency(flows);
        Map<String, RobotTaskMetadata> metadataById = new HashMap<>();
        for (RobotTaskMetadata item : metadata) {
            if (item != null && item.getTaskId() != null) metadataById.put(item.getTaskId(), item);
        }
        List<List<RobotTaskMetadata>> paths = new ArrayList<>();
        List<RobotTaskMetadata> currentTasks = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        StringBuilder issue = new StringBuilder();
        for (StartEvent start : model.getModelElementsByType(StartEvent.class)) {
            if (!enumeratePrefixPaths(start, targetTaskId, outgoing, metadataById,
                    currentTasks, visiting, paths, maxPaths, issue)) {
                return new ContinuationPaths(Status.UNRESOLVED,
                        issue.length() == 0 ? "Could not enumerate all producer prefixes." : issue.toString(), paths);
            }
        }
        if (paths.isEmpty()) {
            return new ContinuationPaths(Status.FAIL, "No start-to-target task prefix exists for " + targetTaskId + ".", paths);
        }
        return new ContinuationPaths(Status.PASS, "Enumerated " + paths.size() + " producer prefix path(s).", paths);
    }

    private boolean enumeratePrefixPaths(FlowNode node, String targetTaskId,
                                         Map<String, List<FlowNode>> outgoing,
                                         Map<String, RobotTaskMetadata> metadataById,
                                         List<RobotTaskMetadata> currentTasks, Set<String> visiting,
                                         List<List<RobotTaskMetadata>> paths, int maxPaths, StringBuilder issue) {
        if (node == null) {
            issue.append("A producer prefix sequence flow has no target.");
            return false;
        }
        if (paths.size() >= maxPaths) {
            issue.append("Producer prefix path limit exceeded (limit=").append(maxPaths).append(").");
            return false;
        }
        if (visiting.contains(node.getId())) {
            issue.append("Cycle encountered before producer task at ").append(node.getId()).append('.');
            return false;
        }
        int previousSize = currentTasks.size();
        if (node instanceof Task) {
            RobotTaskMetadata taskMetadata = metadataById.get(node.getId());
            if (taskMetadata == null) {
                issue.append("Missing task metadata in producer prefix for ").append(node.getId()).append('.');
                return false;
            }
            currentTasks.add(taskMetadata);
        }
        if (targetTaskId.equals(node.getId())) {
            paths.add(new ArrayList<>(currentTasks));
            while (currentTasks.size() > previousSize) currentTasks.remove(currentTasks.size() - 1);
            return true;
        }
        List<FlowNode> next = outgoing.getOrDefault(node.getId(), List.of());
        if (next.isEmpty()) {
            while (currentTasks.size() > previousSize) currentTasks.remove(currentTasks.size() - 1);
            return true; // this branch does not contain the target; other branches may
        }
        visiting.add(node.getId());
        for (FlowNode successor : next) {
            if (!enumeratePrefixPaths(successor, targetTaskId, outgoing, metadataById,
                    currentTasks, visiting, paths, maxPaths, issue)) {
                visiting.remove(node.getId());
                while (currentTasks.size() > previousSize) currentTasks.remove(currentTasks.size() - 1);
                return false;
            }
        }
        visiting.remove(node.getId());
        while (currentTasks.size() > previousSize) currentTasks.remove(currentTasks.size() - 1);
        return true;
    }

    /**
     * Enumerates all task continuations from the consumer through every reachable
     * end event. This is used to simulate declared contracts beyond the receiving
     * task. It fails closed on cycles, dead ends, missing task metadata, or path
     * explosion rather than silently selecting one convenient branch.
     */
    public ContinuationPaths continuationPaths(BpmnModelInstance model, String consumerTaskId,
                                               List<RobotTaskMetadata> metadata, int maxPaths) {
        if (model == null || consumerTaskId == null || metadata == null || maxPaths < 1) {
            return new ContinuationPaths(Status.UNRESOLVED, "Missing model, consumer ID, metadata, or positive path limit.", new ArrayList<>());
        }
        Object element = model.getModelElementById(consumerTaskId);
        if (!(element instanceof FlowNode)) {
            return new ContinuationPaths(Status.FAIL, "Consumer ID does not resolve to a BPMN flow node.", new ArrayList<>());
        }
        List<SequenceFlow> flows = new ArrayList<>(model.getModelElementsByType(SequenceFlow.class));
        Map<String, List<FlowNode>> outgoing = buildAdjacency(flows);
        Map<String, RobotTaskMetadata> metadataById = new HashMap<>();
        for (RobotTaskMetadata item : metadata) {
            if (item != null && item.getTaskId() != null) metadataById.put(item.getTaskId(), item);
        }
        List<List<RobotTaskMetadata>> paths = new ArrayList<>();
        List<RobotTaskMetadata> currentTasks = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        StringBuilder issue = new StringBuilder();
        boolean complete = enumeratePaths((FlowNode) element, outgoing, metadataById, currentTasks,
                visiting, paths, maxPaths, issue);
        if (!complete) {
            return new ContinuationPaths(Status.UNRESOLVED,
                    issue.length() == 0 ? "Could not enumerate all consumer continuations." : issue.toString(), paths);
        }
        if (paths.isEmpty()) return new ContinuationPaths(Status.FAIL, "No complete continuation from consumer to an end event exists.", paths);
        return new ContinuationPaths(Status.PASS, "Enumerated " + paths.size() + " complete task continuation(s).", paths);
    }

    private boolean enumeratePaths(FlowNode node, Map<String, List<FlowNode>> outgoing,
                                   Map<String, RobotTaskMetadata> metadataById,
                                   List<RobotTaskMetadata> currentTasks, Set<String> visiting,
                                   List<List<RobotTaskMetadata>> paths, int maxPaths, StringBuilder issue) {
        if (node == null) {
            issue.append("A sequence flow has no target.");
            return false;
        }
        if (paths.size() >= maxPaths) {
            issue.append("Continuation path limit exceeded (limit=").append(maxPaths).append(").");
            return false;
        }
        if (visiting.contains(node.getId())) {
            issue.append("Cycle encountered while enumerating continuations at ").append(node.getId()).append('.');
            return false;
        }
        int previousSize = currentTasks.size();
        if (node instanceof Task) {
            RobotTaskMetadata taskMetadata = metadataById.get(node.getId());
            if (taskMetadata == null) {
                issue.append("Missing task metadata for downstream task ").append(node.getId()).append('.');
                return false;
            }
            currentTasks.add(taskMetadata);
        }
        if (node instanceof EndEvent) {
            paths.add(new ArrayList<>(currentTasks));
            while (currentTasks.size() > previousSize) currentTasks.remove(currentTasks.size() - 1);
            return true;
        }
        List<FlowNode> next = outgoing.getOrDefault(node.getId(), List.of());
        if (next.isEmpty()) {
            issue.append("Dead end after node ").append(node.getId()).append('.');
            while (currentTasks.size() > previousSize) currentTasks.remove(currentTasks.size() - 1);
            return false;
        }
        visiting.add(node.getId());
        for (FlowNode successor : next) {
            if (!enumeratePaths(successor, outgoing, metadataById, currentTasks, visiting, paths, maxPaths, issue)) {
                visiting.remove(node.getId());
                while (currentTasks.size() > previousSize) currentTasks.remove(currentTasks.size() - 1);
                return false;
            }
        }
        visiting.remove(node.getId());
        while (currentTasks.size() > previousSize) currentTasks.remove(currentTasks.size() - 1);
        return true;
    }

    private Status assessTermination(FlowNode node, Map<String, List<FlowNode>> outgoing,
                                    Collection<EndEvent> ends, Map<String, Status> memo,
                                    Set<String> visiting) {
        if (node == null) return Status.FAIL;
        if (ends.stream().anyMatch(end -> end.getId().equals(node.getId()))) return Status.PASS;
        String id = node.getId();
        if (visiting.contains(id)) return Status.PASS; // Cycles/loops are allowed control flow, not dead-ends
        Status cached = memo.get(id);
        if (cached != null) return cached;
        List<FlowNode> next = outgoing.getOrDefault(id, List.of());
        if (next.isEmpty()) {
            memo.put(id, Status.FAIL);
            return Status.FAIL;
        }
        visiting.add(id);
        for (FlowNode successor : next) {
            Status child = assessTermination(successor, outgoing, ends, memo, visiting);
            if (child == Status.FAIL) {
                visiting.remove(id);
                memo.put(id, Status.FAIL);
                return Status.FAIL;
            }
        }
        visiting.remove(id);
        memo.put(id, Status.PASS);
        return Status.PASS;
    }

    private Map<String, List<FlowNode>> buildAdjacency(List<SequenceFlow> flows) {
        Map<String, List<FlowNode>> outgoing = new HashMap<>();
        for (SequenceFlow flow : flows) {
            FlowNode source = flow.getSource();
            FlowNode target = flow.getTarget();
            if (source == null || target == null) continue;
            outgoing.computeIfAbsent(source.getId(), ignored -> new ArrayList<>()).add(target);
        }
        return outgoing;
    }

    private boolean reachable(BpmnModelInstance model, FlowNode start, FlowNode target) {
        if (model == null || start == null || target == null) return false;
        if (start.getId().equals(target.getId())) return true;
        List<SequenceFlow> flows = new ArrayList<>(model.getModelElementsByType(SequenceFlow.class));
        Map<String, List<FlowNode>> outgoing = buildAdjacency(flows);
        Deque<FlowNode> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            FlowNode current = pending.removeFirst();
            if (!visited.add(current.getId())) continue;
            for (FlowNode destination : outgoing.getOrDefault(current.getId(), List.of())) {
                if (destination.getId().equals(target.getId())) return true;
                if (!visited.contains(destination.getId())) pending.addLast(destination);
            }
        }
        return false;
    }

    private Assessment result(Status status, String explanation) {
        return new Assessment(status, explanation);
    }
}
