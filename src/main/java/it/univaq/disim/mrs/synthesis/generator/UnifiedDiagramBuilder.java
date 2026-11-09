package it.univaq.disim.mrs.synthesis.generator;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.FormalExpression;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import it.univaq.disim.mrs.synthesis.model.*;

import java.util.HashMap;
import java.util.Map;

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
        // Preserve the global orchestration as a first-class pool in the unified
        // visualization. In particular this keeps gateways, retry loops and timer
        // definitions visible instead of silently dropping them during projection.
        double currentY = addGlobalChoreography(context, modelInstance, definitions, collaboration, plane);


        for (Map.Entry<String, BpmnModelInstance> entry : context.getAllRobotModels().entrySet()) {
            String robotKey = entry.getKey().replace(".bpmn", "").replace("_enhanced", "");
            BpmnModelInstance enhancedModel = entry.getValue();

            // 1. Creazione del Processo
            org.camunda.bpm.model.bpmn.instance.Process unifiedProcess = modelInstance.newInstance(org.camunda.bpm.model.bpmn.instance.Process.class);
            unifiedProcess.setId("Process_" + robotKey);
            // This is a visualization model. The clone intentionally does not preserve
            // all implementation/extension properties needed for deployment.
            unifiedProcess.setExecutable(false);
            definitions.addChildElement(unifiedProcess);

            // 2. Creazione del Participant (Pool)
            Participant pool = modelInstance.newInstance(Participant.class);
            pool.setName(robotKey);
            pool.setId("Pool_" + robotKey);
            pool.setProcess(unifiedProcess);
            collaboration.addChildElement(pool);
            pools.put(robotKey.toLowerCase(), pool);

            // Disegna il Pool
            double poolY = currentY;
            BpmndiGenerator.createShape(modelInstance, plane, pool, 50, poolY, 1800, 250);
            currentY += 300;

            // 3. CLONAZIONE DEGLI ELEMENTI LOGICI (Tasks, Gateways, Events)
            org.camunda.bpm.model.bpmn.instance.Process originalProcess = enhancedModel.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class).iterator().next();

            for (FlowElement element : originalProcess.getFlowElements()) {
                if (element instanceof SequenceFlow) continue;

                Class<? extends FlowElement> type = getInterface(element);
                if (type != null) {
                    FlowElement clone = modelInstance.newInstance(type);
                    clone.setId(robotKey + "_" + element.getId());
                    if (element.getName() != null) clone.setName(element.getName());

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

            // 4. CLONAZIONE DEI FLUSSI (SequenceFlows)
            for (SequenceFlow seq : originalProcess.getChildElementsByType(SequenceFlow.class)) {
                SequenceFlow clone = modelInstance.newInstance(SequenceFlow.class);
                clone.setId(robotKey + "_" + seq.getId());
                if (seq.getName() != null) clone.setName(seq.getName());
                FlowNode source = modelInstance.getModelElementById(robotKey + "_" + seq.getSource().getId());
                FlowNode target = modelInstance.getModelElementById(robotKey + "_" + seq.getTarget().getId());

                if (source != null && target != null) {
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

            // 5. CLONAZIONE DELLA GRAFICA (BPMNDI) - Aggiusta le coordinate Y per inserirle nel Pool
            double offsetY = poolY - 50;

            for (BpmnShape shape : enhancedModel.getModelElementsByType(BpmnShape.class)) {
                BaseElement element = shape.getBpmnElement();
                if (element == null || element instanceof Participant) continue;

                BaseElement clonedElement = modelInstance.getModelElementById(robotKey + "_" + element.getId());
                if (clonedElement != null) {
                    double newX = shape.getBounds().getX();
                    double newY = shape.getBounds().getY() + offsetY;
                    double w = shape.getBounds().getWidth();
                    double h = shape.getBounds().getHeight();
                    BpmndiGenerator.createShape(modelInstance, plane, clonedElement, newX, newY, w, h);
                }
            }

            for (BpmnEdge edge : enhancedModel.getModelElementsByType(BpmnEdge.class)) {
                BaseElement element = edge.getBpmnElement();
                if (element == null) continue;

                BaseElement clonedElement = modelInstance.getModelElementById(robotKey + "_" + element.getId());
                if (clonedElement != null) {
                    BpmnEdge clonedEdge = modelInstance.newInstance(BpmnEdge.class);
                    clonedEdge.setId(robotKey + "_" + edge.getId() + "_di");
                    clonedEdge.setBpmnElement(clonedElement);

                    for (Waypoint wp : edge.getWaypoints()) {
                        Waypoint clonedWp = modelInstance.newInstance(Waypoint.class);
                        clonedWp.setX(wp.getX());
                        clonedWp.setY(wp.getY() + offsetY);
                        clonedEdge.getWaypoints().add(clonedWp);
                    }
                    plane.addChildElement(clonedEdge);
                }
            }

            // A fixed 1800px pool clipped the retry-projection branch after its
            // downstream nodes were shifted to make room for the gateways. Grow the
            // pool to contain every cloned node instead of leaving shapes outside it.
            BpmnShape poolShape = plane.getChildElementsByType(BpmnShape.class).stream()
                    .filter(shape -> pool.equals(shape.getBpmnElement())).findFirst().orElse(null);
            if (poolShape != null) {
                double rightMost = poolShape.getBounds().getX() + 1800.0;
                for (BpmnShape shape : plane.getChildElementsByType(BpmnShape.class)) {
                    BaseElement shaped = shape.getBpmnElement();
                    if (shaped != null && shaped.getId() != null
                            && shaped.getId().startsWith(robotKey + "_")
                            && shape.getBounds() != null) {
                        rightMost = Math.max(rightMost,
                                shape.getBounds().getX() + shape.getBounds().getWidth() + 80.0);
                    }
                }
                poolShape.getBounds().setWidth(rightMost - poolShape.getBounds().getX());
            }
        }

        // Broadcast is an explicit logical participant in the thesis case study.
        boolean hasBroadcast = context.getValidatedMatches().stream()
                .anyMatch(match -> "Broadcast".equalsIgnoreCase(match.getReceiverRobot()));
        if (hasBroadcast) {
            org.camunda.bpm.model.bpmn.instance.Process broadcastProcess = modelInstance.newInstance(org.camunda.bpm.model.bpmn.instance.Process.class);
            broadcastProcess.setId("Process_Broadcast");
            // The synthetic Broadcast participant is also a visualization-only process.
            broadcastProcess.setExecutable(false);
            definitions.addChildElement(broadcastProcess);
            Participant broadcastPool = modelInstance.newInstance(Participant.class);
            broadcastPool.setId("Pool_Broadcast");
            broadcastPool.setName("Broadcast");
            broadcastPool.setProcess(broadcastProcess);
            collaboration.addChildElement(broadcastPool);
            pools.put("broadcast", broadcastPool);
            BpmndiGenerator.createShape(modelInstance, plane, broadcastPool, 50, currentY, 1800, 180);

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
            BpmndiGenerator.createShape(modelInstance, plane, start, 150, currentY + 60, 36, 36);
            BpmndiGenerator.createShape(modelInstance, plane, receive, 260, currentY + 40, 140, 80);
            BpmndiGenerator.createShape(modelInstance, plane, end, 450, currentY + 60, 36, 36);
            BpmndiGenerator.createEdge(modelInstance, plane, toReceive, 186, currentY + 78, 260, currentY + 78);
            BpmndiGenerator.createEdge(modelInstance, plane, toEnd, 400, currentY + 78, 450, currentY + 78);
            currentY += 220;
        }

        // 6. Creazione dei MessageFlow tra gli effettivi task di invio/ricezione.
        // Le estremità BPMN e le estremità grafiche devono riferirsi agli stessi elementi.
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
            BaseElement targetElement = "Broadcast".equalsIgnoreCase(match.getReceiverRobot())
                    ? modelInstance.getModelElementById("Broadcast_Receive")
                    : modelInstance.getModelElementById(receiverKey + "_" + targetElementId);

            if (!(sourceElement instanceof InteractionNode) || !(targetElement instanceof InteractionNode)) {
                throw new IllegalStateException("Cannot create MessageFlow for '" + match.getMessageName()
                        + "': communication endpoints were not cloned as BPMN interaction nodes. Source='"
                        + (sourceElement == null ? "missing" : sourceElement.getId()) + "', target='"
                        + (targetElement == null ? "missing" : targetElement.getId()) + "'.");
            }

            Message message = ensureMessage(modelInstance, match.getMessageName());
            // Keep Java 11 compatibility: pattern matching for instanceof requires a newer source level.
            if (sourceElement instanceof SendTask) {
                ((SendTask) sourceElement).setMessage(message);
            }
            if (targetElement instanceof ReceiveTask) {
                ((ReceiveTask) targetElement).setMessage(message);
            }

            MessageFlow msgFlow = modelInstance.newInstance(MessageFlow.class);
            msgFlow.setId("Flow_" + java.util.UUID.randomUUID().toString().replace("-", ""));
            msgFlow.setSource((InteractionNode) sourceElement);
            msgFlow.setTarget((InteractionNode) targetElement);
            msgFlow.setMessage(message);
            msgFlow.setName(match.getMessageName());
            collaboration.addChildElement(msgFlow);

            double sX = Double.NaN, sY = Double.NaN, tX = Double.NaN, tY = Double.NaN;
            for (BpmnShape shape : plane.getChildElementsByType(BpmnShape.class)) {
                BaseElement shapedElement = shape.getBpmnElement();
                if (sourceElement.equals(shapedElement)) {
                    sX = shape.getBounds().getX() + (shape.getBounds().getWidth() / 2);
                    sY = shape.getBounds().getY() + shape.getBounds().getHeight();
                }
                if (targetElement.equals(shapedElement)) {
                    tX = shape.getBounds().getX() + (shape.getBounds().getWidth() / 2);
                    tY = shape.getBounds().getY();
                }
            }
            if (Double.isNaN(sX) || Double.isNaN(sY) || Double.isNaN(tX) || Double.isNaN(tY)) {
                throw new IllegalStateException("Cannot draw MessageFlow for '" + match.getMessageName()
                        + "': BPMNDI shape missing for a communication endpoint.");
            }

            BpmnEdge msgEdge = modelInstance.newInstance(BpmnEdge.class);
            msgEdge.setId(msgFlow.getId() + "_di");
            msgEdge.setBpmnElement(msgFlow);

            Waypoint start = modelInstance.newInstance(Waypoint.class);
            start.setX(sX);
            start.setY(sY);
            msgEdge.getWaypoints().add(start);

            Waypoint end = modelInstance.newInstance(Waypoint.class);
            end.setX(tX);
            end.setY(tY);
            msgEdge.getWaypoints().add(end);
            plane.addChildElement(msgEdge);
        }

        return modelInstance;
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
        // Keep IDs unique if distinct names normalize to the same XML identifier.
        if (model.getModelElementById(message.getId()) != null) {
            message.setId(message.getId() + "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        }
        model.getDefinitions().addChildElement(message);
        return message;
    }

    private double getCenterX(BpmnModelInstance model, BpmnPlane plane, BaseElement element) {
        for (BpmnShape shape : plane.getChildElementsByType(BpmnShape.class)) {
            if (element.equals(shape.getBpmnElement())) {
                return shape.getBounds().getX() + shape.getBounds().getWidth() / 2.0;
            }
        }
        return 100;
    }

    private double getCenterY(BpmnModelInstance model, BpmnPlane plane, BaseElement element) {
        for (BpmnShape shape : plane.getChildElementsByType(BpmnShape.class)) {
            if (element.equals(shape.getBpmnElement())) {
                return shape.getBounds().getY() + shape.getBounds().getHeight() / 2.0;
            }
        }
        return 100;
    }

    private double addGlobalChoreography(MissionContext context, BpmnModelInstance target,
                                         Definitions definitions, Collaboration collaboration,
                                         BpmnPlane targetPlane) {
        BpmnModelInstance source = context.getGlobalChoreography();
        if (source == null) return 100;
        org.camunda.bpm.model.bpmn.instance.Process sourceProcess = source
                .getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class).stream()
                .findFirst().orElse(null);
        if (sourceProcess == null) return 100;

        org.camunda.bpm.model.bpmn.instance.Process globalProcess = target
                .newInstance(org.camunda.bpm.model.bpmn.instance.Process.class);
        globalProcess.setId("Process_GlobalChoreography");
        globalProcess.setName("Global Choreography");
        globalProcess.setExecutable(false);
        definitions.addChildElement(globalProcess);

        Participant globalPool = target.newInstance(Participant.class);
        globalPool.setId("Pool_GlobalChoreography");
        globalPool.setName("Global Choreography Flow");
        globalPool.setProcess(globalProcess);
        collaboration.addChildElement(globalPool);
        BpmndiGenerator.createShape(target, targetPlane, globalPool, 50, 60, 1800, 330);

        for (FlowElement element : sourceProcess.getFlowElements()) {
            if (element instanceof SequenceFlow) continue;
            Class<? extends FlowElement> type = getInterface(element);
            if (type == null) {
                throw new IllegalStateException("Unsupported global choreography element type: "
                        + element.getClass().getSimpleName() + " (" + element.getId() + ")");
            }
            FlowElement clone = target.newInstance(type);
            clone.setId("Global_" + element.getId());
            if (element.getName() != null) clone.setName(element.getName());
            if (element instanceof IntermediateCatchEvent) {
                for (EventDefinition definition : ((IntermediateCatchEvent) element).getEventDefinitions()) {
                    if (definition instanceof TimerEventDefinition) {
                        TimerEventDefinition timer = target.newInstance(TimerEventDefinition.class);
                        TimeDuration originalDuration = ((TimerEventDefinition) definition).getTimeDuration();
                        if (originalDuration != null) {
                            TimeDuration duration = target.newInstance(TimeDuration.class);
                            duration.setTextContent(originalDuration.getTextContent());
                            timer.setTimeDuration(duration);
                        }
                        clone.addChildElement(timer);
                    }
                }
            }
            globalProcess.addChildElement(clone);
        }

        for (SequenceFlow sequence : sourceProcess.getChildElementsByType(SequenceFlow.class)) {
            SequenceFlow clone = target.newInstance(SequenceFlow.class);
            clone.setId("Global_" + sequence.getId());
            if (sequence.getName() != null) clone.setName(sequence.getName());
            if (sequence.getConditionExpression() != null) {
                ConditionExpression condition = target.newInstance(ConditionExpression.class);
                condition.setTextContent(sequence.getConditionExpression().getTextContent());
                clone.setConditionExpression(condition);
            }
            FlowNode from = (FlowNode) target.getModelElementById("Global_" + sequence.getSource().getId());
            FlowNode to = (FlowNode) target.getModelElementById("Global_" + sequence.getTarget().getId());
            if (from == null || to == null) throw new IllegalStateException("Cannot clone global sequence flow " + sequence.getId());
            clone.setSource(from);
            clone.setTarget(to);
            globalProcess.addChildElement(clone);
        }
        for (ExclusiveGateway sourceGateway : sourceProcess.getChildElementsByType(ExclusiveGateway.class)) {
            if (sourceGateway.getDefault() != null) {
                ExclusiveGateway clonedGateway = (ExclusiveGateway) target.getModelElementById("Global_" + sourceGateway.getId());
                SequenceFlow clonedDefault = (SequenceFlow) target.getModelElementById("Global_" + sourceGateway.getDefault().getId());
                if (clonedGateway != null && clonedDefault != null) clonedGateway.setDefault(clonedDefault);
            }
        }

        for (BpmnShape shape : source.getModelElementsByType(BpmnShape.class)) {
            BaseElement element = shape.getBpmnElement();
            if (element == null || !(element instanceof FlowElement)) continue;
            BaseElement clone = target.getModelElementById("Global_" + element.getId());
            if (clone != null) {
                BpmndiGenerator.createShape(target, targetPlane, clone,
                        shape.getBounds().getX(), shape.getBounds().getY() + 20,
                        shape.getBounds().getWidth(), shape.getBounds().getHeight());
            }
        }
        for (BpmnEdge edge : source.getModelElementsByType(BpmnEdge.class)) {
            BaseElement element = edge.getBpmnElement();
            if (!(element instanceof SequenceFlow)) continue;
            BaseElement clonedFlow = target.getModelElementById("Global_" + element.getId());
            if (clonedFlow == null) continue;
            BpmnEdge clonedEdge = target.newInstance(BpmnEdge.class);
            clonedEdge.setId("Global_" + edge.getId() + "_di");
            clonedEdge.setBpmnElement(clonedFlow);
            for (Waypoint waypoint : edge.getWaypoints()) {
                Waypoint clonedWaypoint = target.newInstance(Waypoint.class);
                clonedWaypoint.setX(waypoint.getX());
                clonedWaypoint.setY(waypoint.getY() + 20);
                clonedEdge.getWaypoints().add(clonedWaypoint);
            }
            targetPlane.addChildElement(clonedEdge);
        }
        return 430;
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