package it.univaq.disim.mrs.synthesis.analyzer;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.InteractionNode;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.bpmn.instance.TimerEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.Participant;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * Passo 2 del Metodo Automatico (Capitolo 5):
 * Analizza il Choreography Diagram globale ed estrae i messaggi richiesti,
 * i partecipanti (mittente/destinatario) e i task globali.
 */
public class GlobalChoreographyAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(GlobalChoreographyAnalyzer.class);

    public List<MessageFlow> extractGlobalMessages(BpmnModelInstance globalChoreography) {
        List<MessageFlow> messageFlows = new ArrayList<>(globalChoreography.getModelElementsByType(MessageFlow.class));
        logger.info("[Passo 2] Trovati {} MessageFlow nella Global Choreography.", messageFlows.size());
        return messageFlows;
    }

    /** Structural global-choreography check: endpoints, start reachability and ability to reach an end. */
    public List<String> validateStructuralCoherence(BpmnModelInstance model) {
        List<String> issues = new ArrayList<>();
        if (model == null) {
            issues.add("Global choreography model is missing.");
            return issues;
        }
        Collection<StartEvent> starts = model.getModelElementsByType(StartEvent.class);
        Collection<EndEvent> ends = model.getModelElementsByType(EndEvent.class);
        if (starts.isEmpty() || ends.isEmpty()) {
            issues.add("Global choreography must contain at least one start event and one end event.");
            return issues;
        }
        Map<String, List<FlowNode>> outgoing = new HashMap<>();
        Map<String, List<FlowNode>> incoming = new HashMap<>();
        for (SequenceFlow flow : model.getModelElementsByType(SequenceFlow.class)) {
            FlowNode source = flow.getSource();
            FlowNode target = flow.getTarget();
            if (source == null || target == null) {
                issues.add("Sequence flow '" + flow.getId() + "' has a missing source or target.");
                continue;
            }
            outgoing.computeIfAbsent(source.getId(), ignored -> new ArrayList<>()).add(target);
            incoming.computeIfAbsent(target.getId(), ignored -> new ArrayList<>()).add(source);
        }
        Set<String> reachable = traverse(starts, outgoing);
        Set<String> canReachEnd = traverse(ends, incoming);
        for (FlowNode node : model.getModelElementsByType(FlowNode.class)) {
            if (!reachable.contains(node.getId())) {
                issues.add("Global flow node '" + node.getId() + "' is unreachable from every start event.");
            }
            if (!canReachEnd.contains(node.getId())) {
                issues.add("Global flow node '" + node.getId() + "' cannot reach any end event.");
            }
        }
        for (ExclusiveGateway gateway : model.getModelElementsByType(ExclusiveGateway.class)) {
            Collection<SequenceFlow> branches = gateway.getOutgoing();
            if (branches.size() < 2) {
                issues.add("Exclusive gateway '" + gateway.getId() + "' must have at least two outgoing branches.");
                continue;
            }
            boolean allConditionsPresent = true;
            for (SequenceFlow branch : branches) {
                if (branch.getConditionExpression() == null
                        || branch.getConditionExpression().getTextContent() == null
                        || branch.getConditionExpression().getTextContent().trim().isEmpty()) {
                    allConditionsPresent = false;
                    issues.add("Exclusive gateway branch '" + branch.getId()
                            + "' from '" + gateway.getId() + "' has no executable condition expression.");
                }
                if (branch.getTarget() == null || !canReachEnd.contains(branch.getTarget().getId())) {
                    issues.add("Exclusive gateway branch '" + branch.getId() + "' cannot reach an end event.");
                }
            }
            if (allConditionsPresent) {
                // Validate the two properties independently. A guard can be structurally
                // complementary yet still refer to a variable with no declared message source.
                if (!hasComplementaryBooleanConditions(branches)) {
                    issues.add("Exclusive gateway '" + gateway.getId()
                            + "' has conditions whose mutual exclusivity and completeness cannot be proven by the supported Boolean guard check.");
                }
                if (!guardsReferenceDeclaredMessages(branches, model)) {
                    issues.add("Exclusive gateway '" + gateway.getId()
                            + "' uses a Boolean guard variable that is not declared as a message name with a matching message flow; "
                            + "a declared message without a matching message flow is also insufficient, so its communication source cannot be verified.");
                }
            }
        }
        validateCycles(model, outgoing, issues);
        for (MessageFlow flow : model.getModelElementsByType(MessageFlow.class)) {
            if (flow.getSource() == null || flow.getTarget() == null || flow.getMessage() == null) {
                issues.add("Message flow '" + flow.getId() + "' must declare source, target, and message.");
            }
        }
        return issues;
    }

    /**
     * Conservatively validates cyclic regions. A cycle is accepted only when the
     * same strongly connected region contains a timer catch event and a guarded
     * exclusive gateway whose two Boolean outcomes are complementary and sourced
     * from a declared message. This covers the case-study availability retry loop;
     * other loop patterns are reported rather than assumed safe.
     */
    private void validateCycles(BpmnModelInstance model, Map<String, List<FlowNode>> outgoing,
                                List<String> issues) {
        Map<String, FlowNode> nodesById = new HashMap<>();
        for (FlowNode node : model.getModelElementsByType(FlowNode.class)) nodesById.put(node.getId(), node);
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowLink = new HashMap<>();
        Deque<FlowNode> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        List<Set<FlowNode>> components = new ArrayList<>();
        int[] nextIndex = {0};
        for (FlowNode node : nodesById.values()) {
            if (!index.containsKey(node.getId())) {
                strongConnect(node, outgoing, index, lowLink, stack, onStack, components, nextIndex);
            }
        }
        for (Set<FlowNode> component : components) {
            boolean cyclic = component.size() > 1;
            if (!cyclic && !component.isEmpty()) {
                FlowNode only = component.iterator().next();
                cyclic = outgoing.getOrDefault(only.getId(), List.of()).stream()
                        .anyMatch(next -> next != null && only.getId().equals(next.getId()));
            }
            if (!cyclic) continue;
            boolean timerPresent = component.stream().filter(IntermediateCatchEvent.class::isInstance)
                    .map(IntermediateCatchEvent.class::cast)
                    .anyMatch(event -> event.getEventDefinitions().stream()
                            .filter(TimerEventDefinition.class::isInstance)
                            .map(TimerEventDefinition.class::cast)
                            .anyMatch(definition -> definition.getTimeDuration() != null
                                    && definition.getTimeDuration().getTextContent() != null
                                    && !definition.getTimeDuration().getTextContent().trim().isEmpty()));
            boolean guardedGatewayPresent = component.stream().filter(ExclusiveGateway.class::isInstance)
                    .map(ExclusiveGateway.class::cast)
                    .anyMatch(gateway -> gateway.getOutgoing().size() == 2
                            && hasComplementaryBooleanConditions(gateway.getOutgoing())
                            && guardsReferenceDeclaredMessages(gateway.getOutgoing(), model));
            if (!timerPresent || !guardedGatewayPresent) {
                String ids = component.stream().map(FlowNode::getId).sorted()
                        .collect(java.util.stream.Collectors.joining(", "));
                issues.add("Global cycle [" + ids + "] is not covered by the supported retry pattern: "
                        + "a timer catch event and a complementary, message-sourced Boolean gateway are required.");
            }
        }
    }

    private void strongConnect(FlowNode node, Map<String, List<FlowNode>> outgoing,
                               Map<String, Integer> index, Map<String, Integer> lowLink,
                               Deque<FlowNode> stack, Set<String> onStack,
                               List<Set<FlowNode>> components, int[] nextIndex) {
        String id = node.getId();
        index.put(id, nextIndex[0]);
        lowLink.put(id, nextIndex[0]++);
        stack.push(node);
        onStack.add(id);
        for (FlowNode successor : outgoing.getOrDefault(id, List.of())) {
            if (successor == null) continue;
            String successorId = successor.getId();
            if (!index.containsKey(successorId)) {
                strongConnect(successor, outgoing, index, lowLink, stack, onStack, components, nextIndex);
                lowLink.put(id, Math.min(lowLink.get(id), lowLink.get(successorId)));
            } else if (onStack.contains(successorId)) {
                lowLink.put(id, Math.min(lowLink.get(id), index.get(successorId)));
            }
        }
        if (lowLink.get(id).equals(index.get(id))) {
            Set<FlowNode> component = new HashSet<>();
            FlowNode member;
            do {
                member = stack.pop();
                onStack.remove(member.getId());
                component.add(member);
            } while (!id.equals(member.getId()));
            components.add(component);
        }
    }

    /**
     * A supported Boolean guard must refer to a declared message name that is
     * actually used by at least one MessageFlow. This is a structural check only:
     * BPMN message declarations do not, by themselves, prove that an execution
     * engine maps the received payload to a process variable with the same name.
     */
    private boolean guardsReferenceDeclaredMessages(Collection<SequenceFlow> branches, BpmnModelInstance model) {
        Set<String> messageNamesWithFlows = new HashSet<>();
        Map<String, String> messageNamesById = new HashMap<>();
        for (Message message : model.getModelElementsByType(Message.class)) {
            if (message.getId() != null && message.getName() != null && !message.getName().trim().isEmpty()) {
                messageNamesById.put(message.getId(), message.getName().trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (MessageFlow messageFlow : model.getModelElementsByType(MessageFlow.class)) {
            Message message = messageFlow.getMessage();
            if (message != null) {
                String name = message.getName();
                if (name == null) name = messageNamesById.get(message.getId());
                if (name != null && !name.trim().isEmpty()) {
                    messageNamesWithFlows.add(name.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "^\\$\\{\\s*([A-Za-z_][A-Za-z0-9_.]*)\\s*==\\s*(true|false)\\s*}\\s*$",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        for (SequenceFlow branch : branches) {
            if (branch.getConditionExpression() == null) return false;
            java.util.regex.Matcher matcher = pattern.matcher(branch.getConditionExpression().getTextContent().trim());
            if (!matcher.matches() || !messageNamesWithFlows.contains(matcher.group(1).toLowerCase(java.util.Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /** Proves the supported two-way Boolean form, e.g. ${available == true} / ${available == false}. */
    private boolean hasComplementaryBooleanConditions(Collection<SequenceFlow> branches) {
        if (branches.size() != 2) return false;
        String variable = null;
        Set<String> values = new HashSet<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "^\\$\\{\\s*([A-Za-z_][A-Za-z0-9_.]*)\\s*==\\s*(true|false)\\s*}\\s*$",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        for (SequenceFlow branch : branches) {
            String expression = branch.getConditionExpression().getTextContent().trim();
            java.util.regex.Matcher matcher = pattern.matcher(expression);
            if (!matcher.matches()) return false;
            String currentVariable = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            String value = matcher.group(2).toLowerCase(java.util.Locale.ROOT);
            if (variable != null && !variable.equals(currentVariable)) return false;
            variable = currentVariable;
            values.add(value);
        }
        return values.contains("true") && values.contains("false");
    }

    private Set<String> traverse(Collection<? extends FlowNode> seeds, Map<String, List<FlowNode>> edges) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<FlowNode> queue = new ArrayDeque<>();
        for (FlowNode seed : seeds) if (seed != null && visited.add(seed.getId())) queue.add(seed);
        while (!queue.isEmpty()) {
            FlowNode node = queue.removeFirst();
            for (FlowNode next : edges.getOrDefault(node.getId(), List.of())) {
                if (next != null && visited.add(next.getId())) queue.addLast(next);
            }
        }
        return visited;
    }

    public String getParticipantName(InteractionNode node) {
        if (node instanceof Participant) {
            return ((Participant) node).getName();
        }
        return node != null ? node.getId() : "";
    }

    /**
     * Ricava la descrizione operativa del task globale associato a un determinato messaggio.
     */
    public String extractGlobalTaskDescription(BpmnModelInstance globalChoreography, String messageName) {
        if (globalChoreography == null || messageName == null) return "";
        String msgLower = messageName.toLowerCase();
        for (Task task : globalChoreography.getModelElementsByType(Task.class)) {
            if (task.getId().toLowerCase().contains(msgLower) ||
                (task.getName() != null && task.getName().toLowerCase().contains(msgLower))) {
                return task.getName().replace("\n", " ").trim();
            }
        }
        return "Richiesta messaggio " + messageName;
    }
}