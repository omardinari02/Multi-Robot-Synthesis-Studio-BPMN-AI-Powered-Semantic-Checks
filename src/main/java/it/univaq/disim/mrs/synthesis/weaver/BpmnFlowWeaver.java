package it.univaq.disim.mrs.synthesis.weaver;

import it.univaq.disim.mrs.synthesis.model.InteractionMatch;
import it.univaq.disim.mrs.synthesis.model.InteractionPlan;
import it.univaq.disim.mrs.synthesis.model.MissionContext;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.bpmn.instance.TimerEventDefinition;
import org.camunda.bpm.model.bpmn.instance.TimeDuration;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.ReceiveTask;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Inserts communication tasks at the original producer/consumer locations.
 * Original task IDs are never overwritten by generated task IDs.
 */
public class BpmnFlowWeaver {
    private static final Logger logger = LoggerFactory.getLogger(BpmnFlowWeaver.class);
    private final BpmnAutoLayout layout = new BpmnAutoLayout();

    public void weaveInteractions(MissionContext context, List<InteractionPlan> plans) {
        Map<String, InteractionPlan> plansByProducer = new HashMap<>();
        for (InteractionPlan plan : plans) {
            plansByProducer.put(key(plan.getSenderRobot(), plan.getAnchorTaskId()), plan);
        }

        // Validate all anchors and activation modes before mutating any BPMN model.
        // A failure must not leave half of the robot models already rewritten.
        preflight(context, plans);

        for (Map.Entry<String, BpmnModelInstance> entry : context.getAllRobotModels().entrySet()) {
            String robot = cleanName(entry.getKey());
            BpmnModelInstance model = entry.getValue();
            List<InteractionMatch> outgoing = context.getValidatedMatches().stream()
                    .filter(m -> same(cleanName(m.getSenderRobot()), robot))
                    .collect(Collectors.toList());
            List<InteractionMatch> incoming = context.getValidatedMatches().stream()
                    .filter(m -> same(cleanName(m.getReceiverRobot()), robot)
                            && !"Broadcast".equalsIgnoreCase(m.getReceiverRobot()))
                    .collect(Collectors.toList());
            if (outgoing.isEmpty() && incoming.isEmpty()) continue;

            BpmnPlane plane = layout.ensureDIInitialized(model);
            insertSendTasks(context, model, plane, robot, outgoing, plansByProducer);
            insertReceiveTasks(context, model, plane, robot, incoming);
            validateLocalModel(model, robot);
        }

        // Project the global availability retry into the participating local process.
        // Without this step, the global BPMN contains the 10-second retry but the
        // Drone's enhanced process remains a straight line and cannot express it.
        projectAvailabilityRetryControl(context);
    }

    private void projectAvailabilityRetryControl(MissionContext context) {
        BpmnModelInstance global = context.getGlobalChoreography();
        if (global == null) return;

        Process globalProcess = global.getModelElementsByType(Process.class).stream()
                .filter(process -> !process.getFlowElements().isEmpty()).findFirst().orElse(null);
        if (globalProcess == null) return;

        ExclusiveGateway globalGateway = globalProcess.getChildElementsByType(ExclusiveGateway.class).stream()
                .filter(gateway -> gateway.getOutgoing().size() == 2)
                .filter(gateway -> gateway.getOutgoing().stream().anyMatch(flow -> flow.getTarget() instanceof IntermediateCatchEvent))
                .findFirst().orElse(null);
        if (globalGateway == null) return;

        SequenceFlow retryBranch = globalGateway.getOutgoing().stream()
                .filter(flow -> flow.getTarget() instanceof IntermediateCatchEvent).findFirst().orElse(null);
        SequenceFlow successBranch = globalGateway.getOutgoing().stream()
                .filter(flow -> flow != retryBranch).findFirst().orElse(null);
        if (retryBranch == null || successBranch == null || !(retryBranch.getTarget() instanceof IntermediateCatchEvent)) return;
        IntermediateCatchEvent globalTimer = (IntermediateCatchEvent) retryBranch.getTarget();
        TimerEventDefinition globalTimerDefinition = globalTimer.getEventDefinitions().stream()
                .filter(TimerEventDefinition.class::isInstance).map(TimerEventDefinition.class::cast).findFirst().orElse(null);
        if (globalTimerDefinition == null || globalTimerDefinition.getTimeDuration() == null) return;

        // Resolve endpoints from the validated interaction metadata, rather than
        // relying on generated task IDs or a fixed local-model filename.
        InteractionMatch availabilityMatch = context.getValidatedMatches().stream()
                .filter(match -> normalize(match.getMessageName()).contains("availability")
                        || normalize(match.getMessageName()).contains("disponibil"))
                .filter(match -> !"Broadcast".equalsIgnoreCase(match.getReceiverRobot()))
                .findFirst().orElse(null);
        InteractionMatch assignmentMatch = context.getValidatedMatches().stream()
                .filter(match -> normalize(match.getMessageName()).contains("closest")
                        || normalize(match.getMessageName()).contains("trattore"))
                .findFirst().orElse(null);
        if (availabilityMatch == null || assignmentMatch == null
                || !same(cleanName(availabilityMatch.getReceiverRobot()), cleanName(assignmentMatch.getSenderRobot()))) return;

        String robot = cleanName(availabilityMatch.getReceiverRobot());
        BpmnModelInstance local = context.getAllRobotModels().entrySet().stream()
                .filter(entry -> same(cleanName(entry.getKey()), robot)).map(Map.Entry::getValue).findFirst().orElse(null);
        if (local == null) return;

        FlowNode availabilityReceive = findFlowNode(local, availabilityMatch.getGeneratedReceiveTaskId());
        FlowNode availabilityConsumer = findFlowNode(local, availabilityMatch.getReceiverTaskId());
        if (availabilityReceive == null || availabilityConsumer == null || availabilityConsumer.getOutgoing().size() != 1) return;
        SequenceFlow oldFlow = availabilityConsumer.getOutgoing().iterator().next();
        FlowNode successTarget = oldFlow.getTarget();
        if (successTarget == null || !successTarget.getId().equals(assignmentMatch.getSenderTaskId())) {
            // Do not guess a control-flow insertion point when the two interactions
            // are not adjacent in the local process.
            return;
        }

        BpmnPlane plane = layout.ensureDIInitialized(local);
        // Anchor the projected control structure to the task that actually receives
        // the availability response. The previous implementation positioned the
        // gateways relative to the success target, which could put them to the left
        // of CheckAvailability and make the diagram contradict its sequence flows.
        double consumerX = layout.getX(local, availabilityConsumer);
        double consumerY = layout.getY(local, availabilityConsumer);
        double targetX = layout.getX(local, successTarget);
        double gatewayX = consumerX + 230.0;
        double gatewayY = consumerY;
        double requiredTargetX = consumerX + 500.0;
        double shift = Math.max(180.0, requiredTargetX - targetX);
        layout.shiftRightRecursive(local, successTarget, shift);
        layout.removeFlowAndDI(local, oldFlow);

        // Keep the collection gateway required by the thesis projection, but place
        // it AFTER the local availability check and before the exclusive decision.
        ParallelGateway localJoin = layout.createGateway(local, ParallelGateway.class,
                "Raccolta di tutte le disponibilità");
        layout.placeShape(local, plane, localJoin, consumerX + 125.0, gatewayY);
        ExclusiveGateway localGateway = layout.createGateway(local, ExclusiveGateway.class,
                globalGateway.getName() == null ? "Disponibilità confermata?" : globalGateway.getName());
        layout.placeShape(local, plane, localGateway, gatewayX, gatewayY);
        layout.connect(local, plane, availabilityConsumer, localJoin, "");
        layout.connect(local, plane, localJoin, localGateway, "");
        SequenceFlow yes = layout.connect(local, plane, localGateway, successTarget,
                successBranch.getName() == null ? "sì" : successBranch.getName());
        if (successBranch.getConditionExpression() != null) {
            ConditionExpression condition = local.newInstance(ConditionExpression.class);
            condition.setTextContent(successBranch.getConditionExpression().getTextContent());
            yes.setConditionExpression(condition);
        }
        SequenceFlow no = layout.connect(local, plane, localGateway, nullSafeTimerPlaceholder(local, plane, gatewayX, gatewayY),
                retryBranch.getName() == null ? "no" : retryBranch.getName());
        if (retryBranch.getConditionExpression() != null) {
            ConditionExpression condition = local.newInstance(ConditionExpression.class);
            condition.setTextContent(retryBranch.getConditionExpression().getTextContent());
            no.setConditionExpression(condition);
        }

        IntermediateCatchEvent localTimer = (IntermediateCatchEvent) no.getTarget();
        TimerEventDefinition timerDefinition = local.newInstance(TimerEventDefinition.class);
        TimeDuration duration = local.newInstance(TimeDuration.class);
        duration.setTextContent(globalTimerDefinition.getTimeDuration().getTextContent());
        timerDefinition.setTimeDuration(duration);
        localTimer.addChildElement(timerDefinition);
        layout.connect(local, plane, localTimer, availabilityReceive, "retry availability");

        // Make the positive path the gateway default only when the source model
        // explicitly declares a default branch.
        if (globalGateway.getDefault() != null && globalGateway.getDefault().getId().equals(successBranch.getId())) {
            localGateway.setDefault(yes);
        } else if (globalGateway.getDefault() != null && globalGateway.getDefault().getId().equals(retryBranch.getId())) {
            localGateway.setDefault(no);
        }
        validateLocalModel(local, robot);
        logger.info("Projected global availability retry ({} seconds) into local process {}.",
                duration.getTextContent(), robot);
    }

    private IntermediateCatchEvent nullSafeTimerPlaceholder(BpmnModelInstance model, BpmnPlane plane,
                                                              double x, double y) {
        IntermediateCatchEvent timer = layout.createTimer(model, "Attesa 10 secondi");
        for (org.camunda.bpm.model.bpmn.instance.EventDefinition definition : new ArrayList<>(timer.getEventDefinitions())) {
            timer.removeChildElement(definition);
        }
        layout.placeShape(model, plane, timer, x + 80.0, y + 110.0);
        return timer;
    }

    private void preflight(MissionContext context, List<InteractionPlan> plans) {
        for (InteractionPlan plan : plans) {
            String mode = plan.getActivationMode();
            if ("EXCLUSIVE".equalsIgnoreCase(mode)) {
                if (plan.getReceiverMatches().size() < 2 || plan.getReceiverMatches().stream()
                        .anyMatch(match -> match.getBranchConditionExpression() == null
                                || match.getBranchConditionExpression().trim().isEmpty())) {
                    fail(context, "Exclusive fan-out from producer '" + plan.getAnchorTaskId()
                            + "' requires an explicit executable condition for every recipient branch.");
                }
            }
            if ("UNRESOLVED".equalsIgnoreCase(mode)) {
                fail(context, "Cannot infer a safe activation mode for fan-out from producer '" + plan.getAnchorTaskId()
                        + "': global choreography has neither a total order nor a common parallel/exclusive gateway.");
            }
            if (!"SEQUENCE".equalsIgnoreCase(mode) && !"PARALLEL".equalsIgnoreCase(mode)
                    && !"EXCLUSIVE".equalsIgnoreCase(mode)) {
                fail(context, "Unsupported activation mode '" + mode + "' for producer '" + plan.getAnchorTaskId() + "'.");
            }
        }

        for (Map.Entry<String, BpmnModelInstance> entry : context.getAllRobotModels().entrySet()) {
            String robot = cleanName(entry.getKey());
            BpmnModelInstance model = entry.getValue();
            for (InteractionMatch match : context.getValidatedMatches()) {
                if (same(cleanName(match.getSenderRobot()), robot)) {
                    FlowNode producer = findFlowNode(model, match.getSenderTaskId());
                    if (producer == null || producer.getOutgoing().size() != 1) {
                        int count = producer == null ? 0 : producer.getOutgoing().size();
                        fail(context, "Producer anchor '" + match.getSenderTaskId() + "' in " + robot
                                + " must exist and have exactly one outgoing flow before insertion; found " + count + ".");
                    }
                }
                if (same(cleanName(match.getReceiverRobot()), robot)
                        && !"Broadcast".equalsIgnoreCase(match.getReceiverRobot())) {
                    String anchor = effectiveReceiveAnchor(context, model, robot, match);
                    FlowNode consumer = findFlowNode(model, anchor);
                    if (consumer == null || consumer.getIncoming().size() != 1) {
                        int count = consumer == null ? 0 : consumer.getIncoming().size();
                        fail(context, "Consumer anchor '" + anchor + "' in " + robot
                                + " must exist and have exactly one incoming flow before insertion; found " + count + ".");
                    }
                }
            }
        }
    }

    private void insertSendTasks(MissionContext context, BpmnModelInstance model, BpmnPlane plane,
                                 String robot, List<InteractionMatch> matches,
                                 Map<String, InteractionPlan> plansByProducer) {
        Map<String, List<InteractionMatch>> byProducer = matches.stream().collect(Collectors.groupingBy(
                InteractionMatch::getSenderTaskId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<InteractionMatch>> entry : byProducer.entrySet()) {
            FlowNode producer = findFlowNode(model, entry.getKey());
            if (producer == null) fail(context, "Producer anchor '" + entry.getKey() + "' does not exist in " + robot + ".");
            if (producer.getOutgoing().size() != 1) {
                fail(context, "Producer task '" + entry.getKey() + "' in " + robot
                        + " must have exactly one outgoing flow before communication insertion; found " + producer.getOutgoing().size() + ".");
            }
            SequenceFlow oldFlow = producer.getOutgoing().iterator().next();
            FlowNode successor = oldFlow.getTarget();
            if (successor == null) fail(context, "Producer flow from '" + entry.getKey() + "' has no target.");

            List<InteractionMatch> group = new ArrayList<>(entry.getValue());
            InteractionPlan plan = plansByProducer.get(key(robot, entry.getKey()));
            String mode = plan == null ? "SEQUENCE" : plan.getActivationMode();
            if ("EXCLUSIVE".equalsIgnoreCase(mode) && group.stream()
                    .anyMatch(match -> match.getBranchConditionExpression() == null
                            || match.getBranchConditionExpression().trim().isEmpty())) {
                fail(context, "Exclusive fan-out from producer '" + entry.getKey()
                        + "' requires an explicit executable condition for every recipient branch.");
            }
            if ("UNRESOLVED".equalsIgnoreCase(mode)) {
                fail(context, "Cannot infer a safe activation mode for fan-out from producer '" + entry.getKey()
                        + "': global choreography has neither a total order nor a common parallel/exclusive gateway.");
            }
            if (!"SEQUENCE".equalsIgnoreCase(mode) && !"PARALLEL".equalsIgnoreCase(mode)
                    && !"EXCLUSIVE".equalsIgnoreCase(mode)) {
                fail(context, "Unsupported activation mode '" + mode + "' for producer '" + entry.getKey() + "'.");
            }
            if (plan != null && "SEQUENCE".equalsIgnoreCase(mode)) {
                // The planner orders this list by the global choreography when the
                // messages are constrained by a global sequence.
                group = new ArrayList<>(plan.getReceiverMatches());
            }
            double producerX = layout.getX(model, producer);
            double producerY = layout.getY(model, producer);
            int count = group.size();
            double shift = count == 1 ? 150.0 : (count + 2) * 150.0;
            layout.shiftRightRecursive(model, successor, shift);
            layout.removeFlowAndDI(model, oldFlow);

            if (count == 1) {
                InteractionMatch match = group.get(0);
                SendTask send = layout.createTask(model, SendTask.class, "Invia: " + match.getMessageName());
                match.setGeneratedSendTaskId(send.getId());
                bindMessage(model, send, match.getMessageName());
                layout.placeShape(model, plane, send, producerX + 150, producerY);
                layout.connect(model, plane, producer, send, "");
                layout.connect(model, plane, send, successor, "");
            } else if ("SEQUENCE".equalsIgnoreCase(mode)) {
                FlowNode previous = producer;
                for (int i = 0; i < count; i++) {
                    InteractionMatch match = group.get(i);
                    SendTask send = layout.createTask(model, SendTask.class, "Invia: " + match.getMessageName());
                    match.setGeneratedSendTaskId(send.getId());
                    bindMessage(model, send, match.getMessageName());
                    layout.placeShape(model, plane, send, producerX + (i + 1) * 150.0, producerY);
                    layout.connect(model, plane, previous, send, "");
                    previous = send;
                }
                layout.connect(model, plane, previous, successor, "");
            } else if ("PARALLEL".equalsIgnoreCase(mode)) {
                org.camunda.bpm.model.bpmn.instance.Gateway split = layout.createGateway(model, ParallelGateway.class,
                        "Invio parallelo");
                org.camunda.bpm.model.bpmn.instance.Gateway join = layout.createGateway(model, ParallelGateway.class,
                        "Sincronizzazione invii");
                layout.placeShape(model, plane, split, producerX + 150, producerY);
                layout.placeShape(model, plane, join, producerX + (count + 2) * 150.0, producerY);
                layout.connect(model, plane, producer, split, "");
                for (int i = 0; i < count; i++) {
                    InteractionMatch match = group.get(i);
                    SendTask send = layout.createTask(model, SendTask.class, "Invia: " + match.getMessageName());
                    match.setGeneratedSendTaskId(send.getId());
                    bindMessage(model, send, match.getMessageName());
                    layout.placeShape(model, plane, send, producerX + 300, producerY + i * 110.0);
                    layout.connect(model, plane, split, send, "");
                    layout.connect(model, plane, send, join, "");
                }
                layout.connect(model, plane, join, successor, "");
            } else if ("EXCLUSIVE".equalsIgnoreCase(mode)) {
                org.camunda.bpm.model.bpmn.instance.Gateway split = layout.createGateway(model, ExclusiveGateway.class,
                        "Scelta destinatario");
                layout.placeShape(model, plane, split, producerX + 150, producerY);
                layout.connect(model, plane, producer, split, "");
                for (int i = 0; i < count; i++) {
                    InteractionMatch match = group.get(i);
                    SendTask send = layout.createTask(model, SendTask.class, "Invia: " + match.getMessageName());
                    match.setGeneratedSendTaskId(send.getId());
                    bindMessage(model, send, match.getMessageName());
                    layout.placeShape(model, plane, send, producerX + 300, producerY + i * 110.0);
                    SequenceFlow guarded = layout.connect(model, plane, split, send, "");
                    ConditionExpression condition = model.newInstance(ConditionExpression.class);
                    condition.setTextContent(match.getBranchConditionExpression());
                    guarded.setConditionExpression(condition);
                    // Exclusive branches merge implicitly at the original successor.
                    layout.connect(model, plane, send, successor, "");
                }
            }
            logger.info("Inserted {} send task(s) after {} / {}.", count, robot, entry.getKey());
        }
    }

    private void insertReceiveTasks(MissionContext context, BpmnModelInstance model, BpmnPlane plane,
                                    String robot, List<InteractionMatch> matches) {
        Map<String, List<InteractionMatch>> byConsumer = matches.stream().collect(Collectors.groupingBy(
                match -> effectiveReceiveAnchor(context, model, robot, match), LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<InteractionMatch>> entry : byConsumer.entrySet()) {
            FlowNode consumer = findFlowNode(model, entry.getKey());
            if (consumer == null) fail(context, "Consumer anchor '" + entry.getKey() + "' does not exist in " + robot + ".");
            if (consumer.getIncoming().size() != 1) {
                fail(context, "Consumer task '" + entry.getKey() + "' in " + robot
                        + " must have exactly one incoming flow before communication insertion; found " + consumer.getIncoming().size() + ".");
            }
            SequenceFlow oldFlow = consumer.getIncoming().iterator().next();
            FlowNode predecessor = oldFlow.getSource();
            if (predecessor == null) fail(context, "Consumer flow into '" + entry.getKey() + "' has no source.");
            List<InteractionMatch> group = entry.getValue();
            double predecessorX = layout.getX(model, predecessor);
            double predecessorY = layout.getY(model, predecessor);
            layout.shiftRightRecursive(model, consumer, group.size() * 150.0);
            layout.removeFlowAndDI(model, oldFlow);

            FlowNode previous = predecessor;
            for (int i = 0; i < group.size(); i++) {
                InteractionMatch match = group.get(i);
                ReceiveTask receive = layout.createTask(model, ReceiveTask.class, "Ricevi: " + match.getMessageName());
                match.setGeneratedReceiveTaskId(receive.getId());
                bindMessage(model, receive, match.getMessageName());
                layout.placeShape(model, plane, receive, predecessorX + (i + 1) * 150.0, predecessorY);
                layout.connect(model, plane, previous, receive, "");
                previous = receive;
            }
            layout.connect(model, plane, previous, consumer, "");
            logger.info("Inserted {} receive task(s) before {} / {}.", group.size(), robot, entry.getKey());
        }
    }


    /**
     * Places an incoming message before an earlier local action when the global
     * choreography proves that the incoming communication precedes a later
     * outgoing communication. This avoids preserving a locally valid but
     * globally contradictory order such as availability-before-weedPosition.
     * The move is only applied when the global precedence is unambiguous.
     */
    private String effectiveReceiveAnchor(MissionContext context, BpmnModelInstance localModel,
                                          String robot, InteractionMatch incoming) {
        String originalAnchor = incoming.getReceiverTaskId();
        org.camunda.bpm.model.bpmn.instance.Process globalProcess = context.getGlobalChoreography() == null
                ? null : context.getGlobalChoreography().getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class)
                .stream().filter(p -> !p.getFlowElements().isEmpty()).findFirst().orElse(null);
        if (globalProcess == null) return originalAnchor;

        Task incomingGlobalTask = findGlobalMessageTask(globalProcess, incoming.getMessageName());
        if (incomingGlobalTask == null) return originalAnchor;

        List<InteractionMatch> laterOutgoing = context.getValidatedMatches().stream()
                .filter(m -> same(cleanName(m.getSenderRobot()), robot))
                .filter(m -> {
                    Task outgoingGlobalTask = findGlobalMessageTask(globalProcess, m.getMessageName());
                    FlowNode producer = findFlowNode(localModel, m.getSenderTaskId());
                    FlowNode consumer = findFlowNode(localModel, originalAnchor);
                    return outgoingGlobalTask != null && producer != null && consumer != null
                            && globallyPrecedes(incomingGlobalTask, outgoingGlobalTask)
                            && reachable(producer, consumer);
                }).collect(Collectors.toList());

        if (laterOutgoing.isEmpty()) return originalAnchor;
        List<InteractionMatch> earliest = laterOutgoing.stream().filter(candidate -> {
            Task candidateTask = findGlobalMessageTask(globalProcess, candidate.getMessageName());
            return candidateTask != null && laterOutgoing.stream().allMatch(other -> {
                if (candidate == other) return true;
                Task otherTask = findGlobalMessageTask(globalProcess, other.getMessageName());
                return otherTask != null && globallyPrecedes(candidateTask, otherTask);
            });
        }).collect(Collectors.toList());
        if (earliest.size() != 1) {
            logger.warn("Could not determine a unique earlier local anchor for incoming message '{}' in {}; keeping '{}'.",
                    incoming.getMessageName(), robot, originalAnchor);
            return originalAnchor;
        }
        String anchor = earliest.get(0).getSenderTaskId();
        logger.info("Moving receive '{}' before local task '{}' in {} to respect global choreography order.",
                incoming.getMessageName(), anchor, robot);
        return anchor;
    }

    private Task findGlobalMessageTask(org.camunda.bpm.model.bpmn.instance.Process process, String messageName) {
        String target = normalize(messageName);
        if (target.isEmpty()) return null;
        for (Task task : process.getChildElementsByType(Task.class)) {
            if (normalize(task.getId()).contains(target)
                    || (task.getName() != null && normalize(task.getName()).contains(target))) return task;
        }
        return null;
    }

    private boolean globallyPrecedes(FlowNode first, FlowNode second) {
        return first != null && second != null && !first.getId().equals(second.getId())
                && reachable(first, second) && !reachable(second, first);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void validateLocalModel(BpmnModelInstance model, String robot) {
        Process process = model.getModelElementsByType(Process.class).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No BPMN process found for " + robot));
        Set<String> ids = new HashSet<>();
        for (org.camunda.bpm.model.bpmn.instance.FlowElement element : process.getFlowElements()) {
            if (element.getId() != null && !ids.add(element.getId())) {
                throw new IllegalStateException("Duplicate BPMN element ID '" + element.getId() + "' in " + robot);
            }
            if (element instanceof SequenceFlow) {
                SequenceFlow flow = (SequenceFlow) element;
                if (flow.getSource() == null || flow.getTarget() == null) {
                    throw new IllegalStateException("SequenceFlow '" + flow.getId() + "' has an unresolved endpoint in " + robot);
                }
                if (!flow.getSource().getOutgoing().contains(flow) || !flow.getTarget().getIncoming().contains(flow)) {
                    throw new IllegalStateException("SequenceFlow endpoint collections are inconsistent for '" + flow.getId() + "' in " + robot);
                }
            }
        }
        List<StartEvent> starts = new ArrayList<>(model.getModelElementsByType(StartEvent.class));
        List<EndEvent> ends = new ArrayList<>(model.getModelElementsByType(EndEvent.class));
        if (starts.isEmpty() || ends.isEmpty()) throw new IllegalStateException("Process " + robot + " needs a start and end event.");
        for (org.camunda.bpm.model.bpmn.instance.FlowElement element : process.getFlowElements()) {
            if (element instanceof SendTask || element instanceof ReceiveTask) {
                FlowNode node = (FlowNode) element;
                if (node.getIncoming().isEmpty() || node.getOutgoing().isEmpty()) {
                    throw new IllegalStateException("Generated communication task '" + node.getId() + "' is disconnected in " + robot);
                }
                boolean reachableFromAnyStart = starts.stream().anyMatch(start -> reachable(start, node));
                if (!reachableFromAnyStart || !canReachAnyEnd(node, ends)) {
                    throw new IllegalStateException("Generated communication task '" + node.getId()
                            + "' is not on a path from start to end in " + robot);
                }
            }
        }
    }

    private boolean reachable(FlowNode start, FlowNode target) {
        return search(start, target, false);
    }

    private boolean canReachAnyEnd(FlowNode start, List<EndEvent> ends) {
        for (EndEvent end : ends) if (search(start, end, false)) return true;
        return false;
    }

    private boolean search(FlowNode start, FlowNode target, boolean reverse) {
        Deque<FlowNode> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            FlowNode current = pending.removeFirst();
            if (current.getId().equals(target.getId())) return true;
            if (!visited.add(current.getId())) continue;
            if (reverse) {
                current.getIncoming().forEach(flow -> { if (flow.getSource() != null) pending.addLast(flow.getSource()); });
            } else {
                current.getOutgoing().forEach(flow -> { if (flow.getTarget() != null) pending.addLast(flow.getTarget()); });
            }
        }
        return false;
    }

    private FlowNode findFlowNode(BpmnModelInstance model, String id) {
        if (id == null) return null;
        Object element = model.getModelElementById(id);
        return element instanceof FlowNode ? (FlowNode) element : null;
    }

    private void bindMessage(BpmnModelInstance model, SendTask task, String messageName) {
        task.setMessage(ensureMessage(model, messageName));
    }

    private void bindMessage(BpmnModelInstance model, ReceiveTask task, String messageName) {
        task.setMessage(ensureMessage(model, messageName));
    }

    private Message ensureMessage(BpmnModelInstance model, String messageName) {
        String normalized = messageName == null ? "" : messageName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A BPMN communication task must have a non-empty message name.");
        }
        Message existing = model.getModelElementsByType(Message.class).stream()
                .filter(message -> normalized.equalsIgnoreCase(message.getName()))
                .findFirst().orElse(null);
        if (existing != null) return existing;

        Message message = model.newInstance(Message.class);
        message.setId("Message_" + normalized.replaceAll("[^A-Za-z0-9_]", "_")
                + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        message.setName(normalized);
        Definitions definitions = model.getDefinitions();
        if (definitions == null) {
            throw new IllegalStateException("Cannot define BPMN message '" + normalized + "': definitions are missing.");
        }
        definitions.addChildElement(message);
        return message;
    }

    private void fail(MissionContext context, String message) {
        context.addDiagnostic(message);
        throw new IllegalStateException(message);
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        return name.replace(".bpmn", "").replace("Part_", "").replace("_enhanced", "").trim();
    }

    private static String key(String robot, String taskId) {
        return cleanName(robot).toLowerCase(Locale.ROOT) + "::" + (taskId == null ? "" : taskId.toLowerCase(Locale.ROOT));
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
