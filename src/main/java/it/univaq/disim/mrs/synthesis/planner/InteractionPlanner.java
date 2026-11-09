package it.univaq.disim.mrs.synthesis.planner;

import it.univaq.disim.mrs.synthesis.model.InteractionMatch;
import it.univaq.disim.mrs.synthesis.model.InteractionPlan;
import it.univaq.disim.mrs.synthesis.model.MissionContext;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Groups interactions produced by the same original task and infers the fan-out mode. */
public class InteractionPlanner {
    private static final Logger logger = LoggerFactory.getLogger(InteractionPlanner.class);

    public List<InteractionPlan> buildInteractionPlans(MissionContext context) {
        Map<String, InteractionPlan> grouped = new LinkedHashMap<>();
        for (InteractionMatch match : context.getValidatedMatches()) {
            String key = normalize(match.getSenderRobot()) + "::" + normalize(match.getSenderTaskId());
            InteractionPlan plan = grouped.computeIfAbsent(key, ignored -> new InteractionPlan());
            plan.setSenderRobot(match.getSenderRobot());
            plan.setAnchorTaskId(match.getSenderTaskId());
            plan.addReceiverMatch(match);
        }

        List<InteractionPlan> plans = new ArrayList<>(grouped.values());
        for (InteractionPlan plan : plans) {
            int recipientCount = plan.getReceiverMatches().size();
            if (recipientCount <= 1) {
                plan.setActivationMode("SEQUENCE");
            } else {
                List<Task> globalTasks = findGlobalTasks(context, plan.getReceiverMatches());
                if (areTotallyOrdered(globalTasks)) {
                    // A global precedence relation is stronger evidence than a shared
                    // upstream gateway: tasks after a parallel split may still be ordered.
                    plan.getReceiverMatches().sort(Comparator.comparingInt(
                            match -> globalOrderRank(context, plan.getReceiverMatches(), match)));
                    plan.setActivationMode("SEQUENCE");
                } else {
                    Gateway commonGateway = findCommonUpstreamGateway(context, plan.getReceiverMatches());
                    if (commonGateway instanceof ExclusiveGateway
                            && gatewaySeparatesTasks(context, commonGateway, globalTasks)
                            && attachExclusiveBranchConditions(context, commonGateway, plan.getReceiverMatches(), globalTasks)) {
                        // Conditional fan-out is supported only when every outgoing
                        // branch maps to exactly one recipient and has an executable guard.
                        plan.setActivationMode("EXCLUSIVE");
                    } else if (commonGateway instanceof ParallelGateway
                            && gatewaySeparatesTasks(context, commonGateway, globalTasks)) {
                        // A shared gateway is evidence of parallelism only when
                        // each recipient task is reached through a distinct branch.
                        plan.setActivationMode("PARALLEL");
                    } else {
                        // Unordered tasks do not, by themselves, prove parallel intent.
                        // Failing closed avoids silently changing choreography semantics.
                        plan.setActivationMode("UNRESOLVED");
                    }
                }
            }
            logger.info("Planned {} interaction(s) from {} / {} using {} mode.", recipientCount,
                    plan.getSenderRobot(), plan.getAnchorTaskId(), plan.getActivationMode());
        }
        return plans;
    }


    private List<Task> findGlobalTasks(MissionContext context, List<InteractionMatch> matches) {
        if (context.getGlobalChoreography() == null) return Collections.emptyList();
        List<Task> tasks = new ArrayList<>();
        for (InteractionMatch match : matches) {
            Task task = findGlobalTask(context, match.getMessageName());
            if (task == null) return Collections.emptyList();
            tasks.add(task);
        }
        return tasks;
    }

    private boolean areTotallyOrdered(List<Task> tasks) {
        if (tasks.size() < 2) return false;
        for (int i = 0; i < tasks.size(); i++) {
            for (int j = i + 1; j < tasks.size(); j++) {
                Task first = tasks.get(i);
                Task second = tasks.get(j);
                if (first.getId().equals(second.getId())) return false;
                boolean firstBeforeSecond = reachable(first, second);
                boolean secondBeforeFirst = reachable(second, first);
                // A strict sequence requires exactly one precedence direction.
                // If both are reachable, the tasks may participate in a cycle/loop;
                // treating that as a total order would invent a deterministic order.
                if (firstBeforeSecond == secondBeforeFirst) return false;
            }
        }
        return true;
    }

    private int globalOrderRank(MissionContext context, List<InteractionMatch> matches, InteractionMatch target) {
        List<Task> tasks = findGlobalTasks(context, matches);
        Task targetTask = findGlobalTaskForMatch(context, target);
        if (targetTask == null) return Integer.MAX_VALUE;
        int rank = 0;
        for (Task task : tasks) {
            if (!task.getId().equals(targetTask.getId()) && reachable(task, targetTask)) rank++;
        }
        return rank;
    }

    private Task findGlobalTaskForMatch(MissionContext context, InteractionMatch match) {
        if (context.getGlobalChoreography() == null) return null;
        return findGlobalTask(context, match.getMessageName());
    }

    /**
     * Resolve a choreography task across all BPMN processes. Selecting the first
     * non-empty Process silently ignored tasks in later processes; cross-process
     * ambiguity now fails closed instead of inventing an ordering.
     */
    private Task findGlobalTask(MissionContext context, String messageName) {
        if (context.getGlobalChoreography() == null) return null;
        List<Task> exact = new ArrayList<>();
        List<Task> partial = new ArrayList<>();
        for (Process process : context.getGlobalChoreography().getModelElementsByType(Process.class)) {
            for (Task task : process.getChildElementsByType(Task.class)) {
                String target = normalize(messageName);
                if (target.isEmpty()) return null;
                boolean exactMatch = target.equals(normalize(task.getId()))
                        || (task.getName() != null && target.equals(normalize(task.getName())));
                if (exactMatch) {
                    if (!exact.contains(task)) exact.add(task);
                } else if (normalize(task.getId()).contains(target)
                        || (task.getName() != null && normalize(task.getName()).contains(target))) {
                    if (!partial.contains(task)) partial.add(task);
                }
            }
        }
        if (exact.size() == 1) return exact.get(0);
        if (exact.size() > 1) return null;
        return partial.size() == 1 ? partial.get(0) : null;
    }

    private boolean reachable(FlowNode start, FlowNode target) {
        if (start == null || target == null) return false;
        Deque<FlowNode> pending = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            FlowNode current = pending.removeFirst();
            if (current.getId().equals(target.getId())) return true;
            if (!visited.add(current.getId())) continue;
            // Do not rely on BPMN <incoming>/<outgoing> child references being
            // present. Some valid models only declare sourceRef/targetRef on
            // SequenceFlow elements, so derive adjacency from the actual flows.
            for (SequenceFlow flow : current.getModelInstance().getModelElementsByType(SequenceFlow.class)) {
                FlowNode source = flow.getSource();
                FlowNode destination = flow.getTarget();
                if (source != null && destination != null
                        && source.getId().equals(current.getId())) {
                    pending.addLast(destination);
                }
            }
        }
        return false;
    }

    private Gateway findCommonUpstreamGateway(MissionContext context, List<InteractionMatch> matches) {
        if (context.getGlobalChoreography() == null || matches.isEmpty()) return null;
        Process process = context.getGlobalChoreography().getModelElementsByType(Process.class).stream()
                .filter(p -> !p.getFlowElements().isEmpty()).findFirst().orElse(null);
        if (process == null) return null;

        Set<String> commonIds = null;
        Map<String, Gateway> gateways = new LinkedHashMap<>();
        for (InteractionMatch match : matches) {
            Task task = findGlobalTask(process, match.getMessageName());
            if (task == null) return null;
            Set<String> upstream = upstreamGatewayIds(task, gateways);
            if (commonIds == null) commonIds = new LinkedHashSet<>(upstream);
            else commonIds.retainAll(upstream);
        }
        if (commonIds == null || commonIds.isEmpty()) return null;
        // The traversal order is nearest-to-farthest from the message tasks.
        // The nearest common gateway determines the local fan-out pattern.
        for (String id : commonIds) {
            Gateway gateway = gateways.get(id);
            if (gateway instanceof ExclusiveGateway || gateway instanceof ParallelGateway) return gateway;
        }
        return null;
    }

    private Set<String> upstreamGatewayIds(Task task, Map<String, Gateway> gateways) {
        Set<String> result = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<FlowNode> pending = new ArrayDeque<>();
        pending.add(task);
        while (!pending.isEmpty()) {
            FlowNode current = pending.removeFirst();
            if (!visited.add(current.getId())) continue;
            // Resolve predecessor nodes from SequenceFlow sourceRef/targetRef
            // directly; this also supports BPMN files that omit explicit
            // <incoming> and <outgoing> elements on flow nodes.
            for (SequenceFlow incoming : current.getModelInstance().getModelElementsByType(SequenceFlow.class)) {
                FlowNode source = incoming.getSource();
                FlowNode destination = incoming.getTarget();
                if (source == null || destination == null
                        || !destination.getId().equals(current.getId())) continue;
                if (source instanceof Gateway) {
                    gateways.put(source.getId(), (Gateway) source);
                    result.add(source.getId());
                }
                if (!(source instanceof org.camunda.bpm.model.bpmn.instance.StartEvent)) pending.addLast(source);
            }
        }
        return result;
    }

    private boolean gatewaySeparatesTasks(MissionContext context, Gateway gateway, List<Task> tasks) {
        if (context.getGlobalChoreography() == null || gateway == null || tasks.size() < 2) return false;
        List<SequenceFlow> flows = new ArrayList<>(
                context.getGlobalChoreography().getModelElementsByType(SequenceFlow.class));
        Set<String> selectedBranches = new LinkedHashSet<>();
        for (Task task : tasks) {
            Set<String> branchesReachingTask = new LinkedHashSet<>();
            for (SequenceFlow flow : flows) {
                FlowNode source = flow.getSource();
                FlowNode target = flow.getTarget();
                if (source == null || target == null || !gateway.getId().equals(source.getId())) continue;
                if (reachableUsingFlows(target, task, flows)) branchesReachingTask.add(flow.getId());
            }
            // A task reached by multiple outgoing branches is ambiguous (e.g. a
            // loop or merge); do not guess which branch activates its interaction.
            if (branchesReachingTask.size() != 1) return false;
            String branchId = branchesReachingTask.iterator().next();
            if (!selectedBranches.add(branchId)) return false;
        }
        return true;
    }


    /**
     * Attach the executable guard of each outgoing exclusive-gateway branch to
     * its recipient. Partial projections are rejected: silently dropping an
     * unrepresented branch could change the global choice semantics.
     */
    private boolean attachExclusiveBranchConditions(MissionContext context, Gateway gateway,
                                                    List<InteractionMatch> matches, List<Task> tasks) {
        if (!(gateway instanceof ExclusiveGateway) || matches.size() != 2 || tasks.size() != matches.size()) return false;
        List<SequenceFlow> allFlows = new ArrayList<>(context.getGlobalChoreography().getModelElementsByType(SequenceFlow.class));
        List<SequenceFlow> outgoing = allFlows.stream().filter(flow -> flow.getSource() != null
                && gateway.getId().equals(flow.getSource().getId())).collect(java.util.stream.Collectors.toList());
        if (outgoing.size() != matches.size() || !hasSupportedComplementaryGuards(outgoing, context)) return false;
        Set<String> usedFlowIds = new LinkedHashSet<>();
        for (int i = 0; i < matches.size(); i++) {
            Task task = tasks.get(i);
            List<SequenceFlow> branches = new ArrayList<>();
            for (SequenceFlow flow : outgoing) {
                FlowNode target = flow.getTarget();
                if (target != null && reachableUsingFlows(target, task, allFlows)) {
                    branches.add(flow);
                }
            }
            if (branches.size() != 1) return false;
            SequenceFlow branch = branches.get(0);
            String expression = branch.getConditionExpression() == null ? null
                    : branch.getConditionExpression().getTextContent();
            if (expression == null || expression.trim().isEmpty() || !usedFlowIds.add(branch.getId())) return false;
            matches.get(i).setBranchConditionExpression(expression.trim());
        }
        return usedFlowIds.size() == outgoing.size();
    }

    private boolean hasSupportedComplementaryGuards(List<SequenceFlow> branches, MissionContext context) {
        if (branches.size() != 2) return false;
        String variable = null;
        Set<String> values = new LinkedHashSet<>();
        for (SequenceFlow branch : branches) {
            String expression = branch.getConditionExpression() == null ? null
                    : branch.getConditionExpression().getTextContent();
            if (expression == null) return false;
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "(?i)^\\s*\\$?\\{?\\s*([a-z_][a-z0-9_]*)\\s*==\\s*(true|false)\\s*}?\\s*$")
                    .matcher(expression.trim());
            if (!matcher.matches()) return false;
            String currentVariable = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(2).toLowerCase(Locale.ROOT);
            if (variable != null && !variable.equals(currentVariable)) return false;
            variable = currentVariable;
            if (!values.add(value)) return false;
        }
        if (!values.contains("true") || !values.contains("false") || variable == null) return false;
        final String expected = variable;
        return context.getGlobalChoreography().getModelElementsByType(Message.class).stream()
                .anyMatch(message -> message.getName() != null
                        && normalize(message.getName()).equals(normalize(expected)));
    }

    private boolean reachableUsingFlows(FlowNode start, FlowNode target, List<SequenceFlow> flows) {
        if (start == null || target == null) return false;
        Deque<FlowNode> pending = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            FlowNode current = pending.removeFirst();
            if (current.getId().equals(target.getId())) return true;
            if (!visited.add(current.getId())) continue;
            for (SequenceFlow flow : flows) {
                FlowNode source = flow.getSource();
                FlowNode destination = flow.getTarget();
                if (source != null && destination != null
                        && source.getId().equals(current.getId())) pending.addLast(destination);
            }
        }
        return false;
    }

    private Task findGlobalTask(Process process, String messageName) {
        String target = normalize(messageName);
        if (target.isEmpty()) return null;
        List<Task> tasks = new ArrayList<>(process.getChildElementsByType(Task.class));

        // Prefer exact identifier/name matches. Substring matching is only a
        // compatibility fallback for older datasets whose message and task
        // labels differ slightly.
        List<Task> exact = new ArrayList<>();
        for (Task task : tasks) {
            if (target.equals(normalize(task.getId()))
                    || (task.getName() != null && target.equals(normalize(task.getName())))) {
                exact.add(task);
            }
        }
        if (exact.size() == 1) return exact.get(0);
        if (exact.size() > 1) return null;

        List<Task> partial = new ArrayList<>();
        for (Task task : tasks) {
            if (normalize(task.getId()).contains(target)
                    || (task.getName() != null && normalize(task.getName()).contains(target))) {
                if (!partial.contains(task)) partial.add(task);
            }
        }
        return partial.size() == 1 ? partial.get(0) : null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
