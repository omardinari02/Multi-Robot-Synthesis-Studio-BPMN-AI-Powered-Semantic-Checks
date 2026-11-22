package it.univaq.disim.mrs.synthesis.generator;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import it.univaq.disim.mrs.synthesis.model.*;

import java.util.*;

public class UnifiedDiagramBuilder {

    public BpmnModelInstance build(MissionContext context) {
        BpmnModelInstance modelInstance = Bpmn.createEmptyModel();

        Definitions definitions = modelInstance.newInstance(Definitions.class);
        definitions.setTargetNamespace("http://it.univaq.disim/mrs/unified");
        modelInstance.setDefinitions(definitions);

        Collaboration collaboration = modelInstance.newInstance(Collaboration.class);
        collaboration.setId("Collaboration_Global_MRS");
        definitions.addChildElement(collaboration);

        BpmnDiagram diagram = modelInstance.newInstance(BpmnDiagram.class);
        diagram.setId("BPMNDiagram_Unified_MRS");
        BpmnPlane plane = modelInstance.newInstance(BpmnPlane.class);
        plane.setId("BPMNPlane_Unified_MRS");
        plane.setBpmnElement(collaboration);
        diagram.setBpmnPlane(plane);
        definitions.addChildElement(diagram);

        Map<String, Participant> pools = new HashMap<>();
        Map<String, Double> poolYMap = new HashMap<>();
        double poolHeight = 220.0;
        double poolGap = 130.0;
        double currentY = 50.0;

        List<String> orderedKeys = getOrderedRobotKeys(context);
        for (String robotModelKey : orderedKeys) {
            String robotKey = robotModelKey.replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
            poolYMap.put(robotKey.toLowerCase(), currentY);
            currentY += poolHeight + poolGap;
        }

        boolean hasBroadcast = context.getValidatedMatches() != null && context.getValidatedMatches().stream()
                .anyMatch(match -> "Broadcast".equalsIgnoreCase(match.getReceiverRobot()));
        if (hasBroadcast) {
            poolYMap.put("broadcast", currentY);
            currentY += poolHeight + poolGap;
        }

        // Mappa delle interazioni per deduplicare SendTask e ReceiveTask
        Map<String, InteractionMatch> sendTaskToMatch = new HashMap<>();
        Map<String, InteractionMatch> receiveTaskToMatch = new HashMap<>();
        if (context.getValidatedMatches() != null) {
            for (InteractionMatch match : context.getValidatedMatches()) {
                String senderKey = match.getSenderRobot().replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
                String receiverKey = match.getReceiverRobot().replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
                String sId = match.getGeneratedSendTaskId() != null ? match.getGeneratedSendTaskId() : match.getSenderTaskId();
                String rId = match.getGeneratedReceiveTaskId() != null ? match.getGeneratedReceiveTaskId() : match.getReceiverTaskId();

                sendTaskToMatch.put(senderKey.toLowerCase() + "_" + sId, match);
                if (!"Broadcast".equalsIgnoreCase(receiverKey)) {
                    receiveTaskToMatch.put(receiverKey.toLowerCase() + "_" + rId, match);
                }
            }
        }

        Map<String, String> eliminatedReceiveToTarget = new HashMap<>();
        Map<String, BpmnShape> shapeMap = new HashMap<>();

        for (String robotModelKey : orderedKeys) {
            String robotKey = robotModelKey.replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
            BpmnModelInstance enhancedModel = context.getRobotModel(robotModelKey);
            double poolY = poolYMap.get(robotKey.toLowerCase());

            // 1. Creazione del Processo
            org.camunda.bpm.model.bpmn.instance.Process unifiedProcess = modelInstance.newInstance(org.camunda.bpm.model.bpmn.instance.Process.class);
            unifiedProcess.setId("Process_" + robotKey);
            unifiedProcess.setExecutable(false);
            definitions.addChildElement(unifiedProcess);

            // 2. Creazione del Participant (Pool)
            Participant pool = modelInstance.newInstance(Participant.class);
            pool.setName(robotKey);
            pool.setId("Pool_" + robotKey);
            pool.setProcess(unifiedProcess);
            collaboration.addChildElement(pool);
            pools.put(robotKey.toLowerCase(), pool);

            BpmnShape poolShape = BpmndiGenerator.createShape(modelInstance, plane, pool, 50, poolY, 1800, poolHeight);
            shapeMap.put(pool.getId(), poolShape);

            // 3. Risoluzione dei ReceiveTask eliminati (Deduplicazione)
            org.camunda.bpm.model.bpmn.instance.Process originalProcess = enhancedModel.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class).iterator().next();
            Set<String> eliminatedInThisRobot = new HashSet<>();
            for (FlowElement element : originalProcess.getFlowElements()) {
                if (element instanceof ReceiveTask && receiveTaskToMatch.containsKey(robotKey.toLowerCase() + "_" + element.getId())) {
                    eliminatedInThisRobot.add(element.getId());
                }
            }

            for (String recId : eliminatedInThisRobot) {
                String curr = recId;
                while (curr != null && eliminatedInThisRobot.contains(curr)) {
                    String next = null;
                    for (SequenceFlow sf : originalProcess.getChildElementsByType(SequenceFlow.class)) {
                        if (sf.getSource() != null && curr.equals(sf.getSource().getId())) {
                            next = sf.getTarget().getId();
                            break;
                        }
                    }
                    curr = next;
                }
                if (curr != null) {
                    eliminatedReceiveToTarget.put(robotKey + "_" + recId, robotKey + "_" + curr);
                }
            }

            // 4. CLONAZIONE DEGLI ELEMENTI LOGICI (Escludendo i ReceiveTask deduplicati)
            for (FlowElement element : originalProcess.getFlowElements()) {
                if (element instanceof SequenceFlow) continue;
                if (element instanceof ReceiveTask && eliminatedInThisRobot.contains(element.getId())) {
                    continue; // Deduplicato: eliminato dalla rappresentazione interna del pool
                }

                Class<? extends FlowElement> type = getInterface(element);
                if (type != null) {
                    FlowElement clone = modelInstance.newInstance(type);
                    clone.setId(robotKey + "_" + element.getId());

                    if (element instanceof SendTask && sendTaskToMatch.containsKey(robotKey.toLowerCase() + "_" + element.getId())) {
                        InteractionMatch match = sendTaskToMatch.get(robotKey.toLowerCase() + "_" + element.getId());
                        String cleanMsg = match.getMessageName();
                        String actionName = getOperationNameFromGlobalMission(context, cleanMsg);
                        String sKey = match.getSenderRobot().replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
                        String rKey = match.getReceiverRobot().replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
                        String parallelMark = ("weedPosition".equalsIgnoreCase(cleanMsg) || "availability".equalsIgnoreCase(cleanMsg)) ? " |||" : "";
                        String choreoName = actionName + " [msg: " + cleanMsg + "]\n(" + sKey + " -> " + rKey + ")" + parallelMark;
                        clone.setName(choreoName);
                    } else if (element.getName() != null) {
                        clone.setName(element.getName());
                    }

                    if (element instanceof IntermediateCatchEvent) {
                        IntermediateCatchEvent originalEvent = (IntermediateCatchEvent) element;
                        TimerEventDefinition originalTimer = originalEvent.getEventDefinitions().stream()
                                .filter(TimerEventDefinition.class::isInstance)
                                .map(TimerEventDefinition.class::cast).findFirst().orElse(null);
                        if (originalTimer != null) {
                            TimerEventDefinition timerDef = modelInstance.newInstance(TimerEventDefinition.class);
                            if (originalTimer.getTimeDuration() != null) {
                                TimeDuration duration = modelInstance.newInstance(TimeDuration.class);
                                duration.setTextContent(originalTimer.getTimeDuration().getTextContent());
                                timerDef.setTimeDuration(duration);
                            }
                            clone.addChildElement(timerDef);
                        }
                    }

                    unifiedProcess.addChildElement(clone);
                }
            }

            // 5. CLONAZIONE DEI FLUSSI (SequenceFlows con re-routing per i ReceiveTask eliminati)
            for (SequenceFlow seq : originalProcess.getChildElementsByType(SequenceFlow.class)) {
                if (seq.getSource() != null && eliminatedInThisRobot.contains(seq.getSource().getId())) {
                    continue; // Flusso in uscita dal receive task eliminato
                }

                String targetId = seq.getTarget().getId();
                if (eliminatedInThisRobot.contains(targetId)) {
                    targetId = eliminatedReceiveToTarget.get(robotKey + "_" + targetId).replace(robotKey + "_", "");
                }

                FlowNode source = modelInstance.getModelElementById(robotKey + "_" + seq.getSource().getId());
                FlowNode target = modelInstance.getModelElementById(robotKey + "_" + targetId);

                if (source != null && target != null) {
                    SequenceFlow clone = modelInstance.newInstance(SequenceFlow.class);
                    clone.setId(robotKey + "_" + seq.getId());
                    if (seq.getName() != null) clone.setName(seq.getName());
                    clone.setSource(source);
                    clone.setTarget(target);
                    if (seq.getConditionExpression() != null) {
                        org.camunda.bpm.model.bpmn.instance.ConditionExpression condition =
                                modelInstance.newInstance(org.camunda.bpm.model.bpmn.instance.ConditionExpression.class);
                        condition.setTextContent(seq.getConditionExpression().getTextContent());
                        clone.setConditionExpression(condition);
                    }
                    unifiedProcess.addChildElement(clone);
                }
            }

            // 6. CLONAZIONE DELLA GRAFICA DEI NODI (BPMNDI Shapes)
            double offsetY = poolY - 50;

            for (BpmnShape shape : enhancedModel.getModelElementsByType(BpmnShape.class)) {
                BaseElement element = shape.getBpmnElement();
                if (element == null || element instanceof Participant) continue;
                if (element instanceof ReceiveTask && eliminatedInThisRobot.contains(element.getId())) {
                    continue; // Nessuna shape per il receive task eliminato
                }

                BaseElement clonedElement = modelInstance.getModelElementById(robotKey + "_" + element.getId());
                if (clonedElement != null) {
                    double newX = shape.getBounds().getX();
                    double newY = shape.getBounds().getY() + offsetY;
                    double w = shape.getBounds().getWidth();
                    double h = shape.getBounds().getHeight();

                    // Se è la Choreography Task condivisa, posizionala nel corridoio tra i due pool
                    if (element instanceof SendTask && sendTaskToMatch.containsKey(robotKey.toLowerCase() + "_" + element.getId())) {
                        InteractionMatch match = sendTaskToMatch.get(robotKey.toLowerCase() + "_" + element.getId());
                        String sKey = match.getSenderRobot().replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
                        String rKey = match.getReceiverRobot().replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
                        double sPoolY = poolYMap.getOrDefault(sKey.toLowerCase(), 50.0);
                        double rPoolY = poolYMap.getOrDefault(rKey.toLowerCase(), 50.0);
                        double topPoolY = Math.min(sPoolY, rPoolY);

                        newY = topPoolY + poolHeight + 20.0; // Perfettamente centrata nel corridoio tra i pool
                        w = 160.0;
                        h = 90.0;
                    }

                    BpmnShape createdShape = BpmndiGenerator.createShape(modelInstance, plane, clonedElement, newX, newY, w, h);
                    shapeMap.put(clonedElement.getId(), createdShape);
                }
            }

            // Aggiorna larghezza del pool per contenere tutti gli elementi
            double rightMost = poolShape.getBounds().getX() + 1800.0;
            for (BpmnShape s : plane.getChildElementsByType(BpmnShape.class)) {
                BaseElement shaped = s.getBpmnElement();
                if (shaped != null && shaped.getId() != null
                        && shaped.getId().startsWith(robotKey + "_")
                        && s.getBounds() != null) {
                    rightMost = Math.max(rightMost,
                            s.getBounds().getX() + s.getBounds().getWidth() + 80.0);
                }
            }
            poolShape.getBounds().setWidth(rightMost - poolShape.getBounds().getX());
        }

        // Broadcast participant
        if (hasBroadcast) {
            double broadcastY = poolYMap.get("broadcast");
            org.camunda.bpm.model.bpmn.instance.Process broadcastProcess = modelInstance.newInstance(org.camunda.bpm.model.bpmn.instance.Process.class);
            broadcastProcess.setId("Process_Broadcast");
            broadcastProcess.setExecutable(false);
            definitions.addChildElement(broadcastProcess);
            Participant broadcastPool = modelInstance.newInstance(Participant.class);
            broadcastPool.setId("Pool_Broadcast");
            broadcastPool.setName("Broadcast");
            broadcastPool.setProcess(broadcastProcess);
            collaboration.addChildElement(broadcastPool);
            pools.put("broadcast", broadcastPool);
            BpmnShape bPoolShape = BpmndiGenerator.createShape(modelInstance, plane, broadcastPool, 50, broadcastY, 1800, 180);
            shapeMap.put(broadcastPool.getId(), bPoolShape);

            StartEvent start = modelInstance.newInstance(StartEvent.class);
            start.setId("Broadcast_Start");
            EndEvent end = modelInstance.newInstance(EndEvent.class);
            end.setId("Broadcast_End");
            ReceiveTask receive = modelInstance.newInstance(ReceiveTask.class);
            receive.setId("Broadcast_Receive");
            receive.setName("Ricevi notifica di completamento missione");
            broadcastProcess.addChildElement(start);
            broadcastProcess.addChildElement(receive);
            broadcastProcess.addChildElement(end);
            SequenceFlow toReceive = modelInstance.newInstance(SequenceFlow.class);
            toReceive.setId("Broadcast_Flow_1");
            toReceive.setSource(start);
            toReceive.setTarget(receive);
            broadcastProcess.addChildElement(toReceive);
            SequenceFlow toEnd = modelInstance.newInstance(SequenceFlow.class);
            toEnd.setId("Broadcast_Flow_2");
            toEnd.setSource(receive);
            toEnd.setTarget(end);
            broadcastProcess.addChildElement(toEnd);

            BpmnShape sShape = BpmndiGenerator.createShape(modelInstance, plane, start, 150, broadcastY + 60, 36, 36);
            BpmnShape rShape = BpmndiGenerator.createShape(modelInstance, plane, receive, 260, broadcastY + 40, 140, 80);
            BpmnShape eShape = BpmndiGenerator.createShape(modelInstance, plane, end, 450, broadcastY + 60, 36, 36);
            shapeMap.put(start.getId(), sShape);
            shapeMap.put(receive.getId(), rShape);
            shapeMap.put(end.getId(), eShape);

            BpmndiGenerator.createEdge(modelInstance, plane, toReceive, 186, broadcastY + 78, 260, broadcastY + 78);
            BpmndiGenerator.createEdge(modelInstance, plane, toEnd, 400, broadcastY + 78, 450, broadcastY + 78);
        }

        // 7. ROUTING DINAMICO ORTOGONALE PER I SEQUENCE FLOW
        // Calcola i waypoints esatti a partire dai bounds attuali dei nodi in shapeMap
        for (SequenceFlow flow : modelInstance.getModelElementsByType(SequenceFlow.class)) {
            if ("Broadcast_Flow_1".equals(flow.getId()) || "Broadcast_Flow_2".equals(flow.getId())) continue;
            createOrthogonalEdge(modelInstance, plane, flow, flow.getSource(), flow.getTarget(), shapeMap);
        }

        // 8. CREAZIONE DEI MESSAGE FLOW TRA CHOREOGRAPHY TASK E RICEVENTE
        for (InteractionMatch match : context.getValidatedMatches()) {
            String senderKey = match.getSenderRobot().replace(".bpmn", "").replace("_enhanced", "");
            String receiverKey = match.getReceiverRobot().replace(".bpmn", "").replace("_enhanced", "");

            Participant sourcePool = pools.get(senderKey.toLowerCase());
            Participant targetPool = pools.get(receiverKey.toLowerCase());
            if (sourcePool == null || targetPool == null) {
                throw new IllegalStateException("Cannot create MessageFlow for '" + match.getMessageName()
                        + "': sender or receiver participant is missing (" + senderKey + " -> " + receiverKey + ").");
            }

            String sourceElementId = match.getGeneratedSendTaskId() != null
                    ? match.getGeneratedSendTaskId() : match.getSenderTaskId();
            String targetElementId = match.getGeneratedReceiveTaskId() != null
                    ? match.getGeneratedReceiveTaskId() : match.getReceiverTaskId();

            BaseElement sourceElement = modelInstance.getModelElementById(senderKey + "_" + sourceElementId);
            String resolvedTargetId = eliminatedReceiveToTarget.getOrDefault(receiverKey + "_" + targetElementId, receiverKey + "_" + targetElementId);
            BaseElement targetElement = "Broadcast".equalsIgnoreCase(match.getReceiverRobot())
                    ? modelInstance.getModelElementById("Broadcast_Receive")
                    : modelInstance.getModelElementById(resolvedTargetId);

            if (!(sourceElement instanceof InteractionNode) || !(targetElement instanceof InteractionNode)) {
                throw new IllegalStateException("Cannot create MessageFlow for '" + match.getMessageName()
                        + "': communication endpoints were not cloned as BPMN interaction nodes. Source='"
                        + (sourceElement == null ? "missing" : sourceElement.getId()) + "', target='"
                        + (targetElement == null ? "missing" : targetElement.getId()) + "'.");
            }

            Message message = ensureMessage(modelInstance, match.getMessageName());
            if (sourceElement instanceof SendTask) {
                ((SendTask) sourceElement).setMessage(message);
            }
            if (targetElement instanceof ReceiveTask) {
                ((ReceiveTask) targetElement).setMessage(message);
            }

            MessageFlow msgFlow = modelInstance.newInstance(MessageFlow.class);
            msgFlow.setId("Flow_" + UUID.randomUUID().toString().replace("-", ""));
            msgFlow.setSource((InteractionNode) sourceElement);
            msgFlow.setTarget((InteractionNode) targetElement);
            msgFlow.setMessage(message);
            msgFlow.setName(match.getMessageName());
            collaboration.addChildElement(msgFlow);

            createOrthogonalEdge(modelInstance, plane, msgFlow, (FlowNode) sourceElement, (FlowNode) targetElement, shapeMap);
        }

        return modelInstance;
    }

    private void createOrthogonalEdge(BpmnModelInstance model, BpmnPlane plane, BaseElement element,
                                      FlowNode sourceNode, FlowNode targetNode, Map<String, BpmnShape> shapeMap) {
        if (sourceNode == null || targetNode == null) return;
        BpmnShape sourceShape = shapeMap.get(sourceNode.getId());
        BpmnShape targetShape = shapeMap.get(targetNode.getId());
        if (sourceShape == null || targetShape == null) return;

        double sX = sourceShape.getBounds().getX();
        double sY = sourceShape.getBounds().getY();
        double sW = sourceShape.getBounds().getWidth();
        double sH = sourceShape.getBounds().getHeight();

        double tX = targetShape.getBounds().getX();
        double tY = targetShape.getBounds().getY();
        double tW = targetShape.getBounds().getWidth();
        double tH = targetShape.getBounds().getHeight();

        BpmnEdge edge = model.newInstance(BpmnEdge.class);
        edge.setId(element.getId() + "_di");
        edge.setBpmnElement(element);

        List<Waypoint> waypoints = new ArrayList<>();

        if (Math.abs((sY + sH / 2.0) - (tY + tH / 2.0)) < 30.0) {
            // Roughly horizontal alignment
            waypoints.add(createWaypoint(model, sX + sW, sY + sH / 2.0));
            waypoints.add(createWaypoint(model, tX, tY + tH / 2.0));
        } else if (tY > sY + sH) {
            // Target is below source (e.g. into corridor choreo task, or down to pool below)
            double startX = sX + sW / 2.0;
            double startY = sY + sH;
            double endX = tX + tW / 2.0;
            double endY = tY;

            if (element instanceof SequenceFlow) {
                // SequenceFlow exits right of task and turns down into corridor
                waypoints.add(createWaypoint(model, sX + sW, sY + sH / 2.0));
                waypoints.add(createWaypoint(model, endX, sY + sH / 2.0));
                waypoints.add(createWaypoint(model, endX, endY));
            } else {
                // MessageFlow leaves bottom of choreo task and goes down to receiver
                waypoints.add(createWaypoint(model, startX, startY));
                double midY = (startY + endY) / 2.0;
                if (Math.abs(startX - endX) > 2.0) {
                    waypoints.add(createWaypoint(model, startX, midY));
                    waypoints.add(createWaypoint(model, endX, midY));
                }
                waypoints.add(createWaypoint(model, endX, endY));
            }
        } else if (tY + tH < sY) {
            // Target is above source (e.g. availability to Drone above)
            double startX = sX + sW / 2.0;
            double startY = sY;
            double endX = tX + tW / 2.0;
            double endY = tY + tH;

            if (element instanceof SequenceFlow) {
                waypoints.add(createWaypoint(model, sX + sW, sY + sH / 2.0));
                waypoints.add(createWaypoint(model, endX, sY + sH / 2.0));
                waypoints.add(createWaypoint(model, endX, endY));
            } else {
                waypoints.add(createWaypoint(model, startX, startY));
                double midY = (startY + endY) / 2.0;
                if (Math.abs(startX - endX) > 2.0) {
                    waypoints.add(createWaypoint(model, startX, midY));
                    waypoints.add(createWaypoint(model, endX, midY));
                }
                waypoints.add(createWaypoint(model, endX, endY));
            }
        } else {
            // Stepped horizontal
            double midX = (sX + sW + tX) / 2.0;
            waypoints.add(createWaypoint(model, sX + sW, sY + sH / 2.0));
            waypoints.add(createWaypoint(model, midX, sY + sH / 2.0));
            waypoints.add(createWaypoint(model, midX, tY + tH / 2.0));
            waypoints.add(createWaypoint(model, tX, tY + tH / 2.0));
        }

        edge.getWaypoints().addAll(waypoints);
        plane.addChildElement(edge);
    }

    private Waypoint createWaypoint(BpmnModelInstance model, double x, double y) {
        Waypoint wp = model.newInstance(Waypoint.class);
        wp.setX(x);
        wp.setY(y);
        return wp;
    }

    private Message ensureMessage(BpmnModelInstance model, String messageName) {
        String normalized = messageName == null ? "" : messageName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A unified BPMN message must have a non-empty name.");
        }
        Message existing = model.getModelElementsByType(Message.class).stream()
                .filter(message -> normalized.equalsIgnoreCase(message.getName()))
                .findFirst().orElse(null);
        if (existing != null) return existing;

        Message message = model.newInstance(Message.class);
        message.setId("Message_" + normalized.replaceAll("[^A-Za-z0-9_]", "_"));
        message.setName(normalized);
        if (model.getModelElementById(message.getId()) != null) {
            message.setId(message.getId() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        }
        model.getDefinitions().addChildElement(message);
        return message;
    }

    public static String getOperationNameFromGlobalMission(MissionContext context, String messageName) {
        if (messageName == null || messageName.trim().isEmpty()) return "Interazione";
        String cleanMsg = messageName.trim();

        if (context != null && context.getGlobalChoreography() != null) {
            BpmnModelInstance global = context.getGlobalChoreography();
            for (FlowNode node : global.getModelElementsByType(FlowNode.class)) {
                String name = node.getName();
                if (name != null && (name.contains(cleanMsg) || name.toLowerCase().contains(cleanMsg.toLowerCase()))) {
                    String op = name;
                    if (op.contains("[")) {
                        op = op.substring(0, op.indexOf('[')).trim();
                    }
                    if (op.contains("(")) {
                        op = op.substring(0, op.indexOf('(')).trim();
                    }
                    if (op.contains("\n")) {
                        op = op.substring(0, op.indexOf('\n')).trim();
                    }
                    op = op.replace("|||", "").trim();
                    if (!op.isEmpty() && !op.equalsIgnoreCase(cleanMsg)) {
                        return op;
                    }
                }
            }
        }

        if ("weedPosition".equalsIgnoreCase(cleanMsg)) return "Invia posizione erbacce";
        if ("availability".equalsIgnoreCase(cleanMsg)) return "Invia disponibilità";
        if ("closestTractor".equalsIgnoreCase(cleanMsg)) return "Notifica trattore più vicino";
        if ("fieldReady".equalsIgnoreCase(cleanMsg)) return "Segnale terreno pronto";
        if ("digDone".equalsIgnoreCase(cleanMsg)) return "Scavo completato";
        if ("seedingDone".equalsIgnoreCase(cleanMsg)) return "Segnale missione completata";
        if ("digPlan".equalsIgnoreCase(cleanMsg)) return "Invio piano scavo";
        if ("seedPlan".equalsIgnoreCase(cleanMsg)) return "Invio piano semina";

        return cleanMsg;
    }

    public static List<String> getOrderedRobotKeys(MissionContext context) {
        List<String> ordered = new ArrayList<>();
        if (context.getValidatedMatches() != null) {
            for (InteractionMatch match : context.getValidatedMatches()) {
                addIfRobotKey(context, ordered, match.getSenderRobot());
                addIfRobotKey(context, ordered, match.getReceiverRobot());
            }
        }
        for (String key : context.getAllRobotModels().keySet()) {
            addIfRobotKey(context, ordered, key);
        }
        return ordered;
    }

    private static void addIfRobotKey(MissionContext context, List<String> ordered, String rawName) {
        if (rawName == null || rawName.trim().isEmpty() || "Broadcast".equalsIgnoreCase(rawName.trim())) return;
        String matchKey = null;
        for (String key : context.getAllRobotModels().keySet()) {
            String cleanKey = key.replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
            String cleanRaw = rawName.replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
            if (cleanKey.equalsIgnoreCase(cleanRaw)) {
                matchKey = key;
                break;
            }
        }
        if (matchKey != null && !ordered.contains(matchKey)) {
            ordered.add(matchKey);
        }
    }

    private Class<? extends FlowElement> getInterface(FlowElement element) {
        if (element instanceof StartEvent) return StartEvent.class;
        if (element instanceof EndEvent) return EndEvent.class;
        if (element instanceof IntermediateCatchEvent) return IntermediateCatchEvent.class;
        if (element instanceof SendTask) return SendTask.class;
        if (element instanceof ReceiveTask) return ReceiveTask.class;
        if (element instanceof ServiceTask) return ServiceTask.class;
        if (element instanceof Task) return Task.class;
        if (element instanceof ExclusiveGateway) return ExclusiveGateway.class;
        if (element instanceof ParallelGateway) return ParallelGateway.class;
        return null;
    }
}
