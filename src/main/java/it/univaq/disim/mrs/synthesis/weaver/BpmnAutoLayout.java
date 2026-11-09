package it.univaq.disim.mrs.synthesis.weaver;

import it.univaq.disim.mrs.synthesis.generator.BpmndiGenerator;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;

import java.util.*;

/**
 * Gestisce il layout grafico automatico (BPMNDI) durante l'iniezione dei task
 * di coordinamento, spostando ricorsivamente gli elementi a valle (shiftRightRecursive)
 * per evitare sovrapposizioni visive in Camunda Modeler.
 */
public class BpmnAutoLayout {

    public BpmnPlane ensureDIInitialized(BpmnModelInstance model) {
        Process process = model.getModelElementsByType(Process.class).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cannot create BPMNDI: model has no process."));
        BpmnPlane plane = model.getModelElementsByType(BpmnPlane.class).stream().findFirst().orElse(null);
        if (plane == null) {
            BpmnDiagram diagram = model.newInstance(BpmnDiagram.class);
            diagram.setId("Diagram_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            plane = model.newInstance(BpmnPlane.class);
            plane.setId("Plane_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            plane.setBpmnElement(process);
            diagram.setBpmnPlane(plane);
            model.getDefinitions().addChildElement(diagram);
        }

        Set<String> represented = new HashSet<>();
        double nextX = 100.0;
        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            if (shape.getBpmnElement() != null) represented.add(shape.getBpmnElement().getId());
            if (shape.getBounds() != null) nextX = Math.max(nextX, shape.getBounds().getX() + shape.getBounds().getWidth() + 50.0);
        }
        for (FlowElement element : process.getFlowElements()) {
            if (element instanceof FlowNode && !represented.contains(element.getId())) {
                placeShape(model, plane, (FlowNode) element, nextX, 100.0);
                nextX += 150.0;
            }
        }
        return plane;
    }

    public void placeShape(BpmnModelInstance model, BpmnPlane plane, FlowNode n, double x, double y) {
        if (plane == null) return;
        double width = (n instanceof IntermediateCatchEvent || n instanceof StartEvent || n instanceof EndEvent) ? 36
                : ((n instanceof Gateway) ? 50 : 100);
        double height = (n instanceof IntermediateCatchEvent || n instanceof StartEvent || n instanceof EndEvent) ? 36
                : ((n instanceof Gateway) ? 50 : 80);
        double yOff = (n instanceof IntermediateCatchEvent) ? 80 : ((n instanceof Gateway) ? 15 : 0);
        BpmndiGenerator.createShape(model, plane, n, x, y + yOff, width, height);
    }

    public SequenceFlow connect(BpmnModelInstance model, BpmnPlane plane, FlowNode from, FlowNode to, String name) {
        SequenceFlow flow = model.newInstance(SequenceFlow.class);
        flow.setId("Flow_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        if (name != null && !name.isEmpty()) {
            flow.setName(name);
        }

        // The SequenceFlow must belong to the BPMN model before endpoint
        // references and FlowNode incoming/outgoing references are assigned.
        Process process = model.getModelElementsByType(Process.class).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot connect flow: model has no process."));
        process.addChildElement(flow);
        flow.setSource(from);
        flow.setTarget(to);

        // Camunda stores the SequenceFlow endpoint references (sourceRef/targetRef)
        // separately from the FlowNode <incoming>/<outgoing> collections. Keep both
        // representations synchronized, but only after the flow belongs to the model.
        if (!from.getOutgoing().contains(flow)) {
            from.getOutgoing().add(flow);
        }
        if (!to.getIncoming().contains(flow)) {
            to.getIncoming().add(flow);
        }

        if (plane != null) {
            double w1 = from instanceof Gateway ? 50 : ((from instanceof Event) ? 36 : 100);
            double h1 = from instanceof Gateway ? 50 : ((from instanceof Event) ? 36 : 80);
            double h2 = to instanceof Gateway ? 50 : ((to instanceof Event) ? 36 : 80);

            double fromX = getX(model, from);
            double fromY = getY(model, from);
            double toX = getX(model, to);
            double toY = getY(model, to);

            BpmndiGenerator.createEdge(model, plane, flow, fromX + w1, fromY + (h1 / 2.0), toX, toY + (h2 / 2.0));
        }
        return flow;
    }

    public void removeFlowAndDI(BpmnModelInstance model, SequenceFlow flow) {
        if (flow == null) return;
        List<BpmnEdge> edgesToRemove = new ArrayList<>();
        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
            if (edge.getBpmnElement() != null && flow.getId().equals(edge.getBpmnElement().getId())) {
                edgesToRemove.add(edge);
            }
        }
        for (BpmnEdge edge : edgesToRemove) {
            if (edge.getParentElement() != null) {
                edge.getParentElement().removeChildElement(edge);
            }
        }
        if (flow.getSource() != null) {
            flow.getSource().getOutgoing().remove(flow);
        }
        if (flow.getTarget() != null) {
            flow.getTarget().getIncoming().remove(flow);
        }
        if (flow.getParentElement() != null) {
            flow.getParentElement().removeChildElement(flow);
        }
    }

    public void shiftRightRecursive(BpmnModelInstance model, FlowNode startNode, double offsetAmount) {
        if (offsetAmount <= 0 || startNode == null) return;
        Set<FlowNode> visited = new HashSet<>();
        Queue<FlowNode> queue = new LinkedList<>();
        queue.add(startNode);
        Set<SequenceFlow> shiftedFlows = new HashSet<>();

        while (!queue.isEmpty()) {
            FlowNode node = queue.poll();
            if (!visited.contains(node)) {
                visited.add(node);

                for (BpmnShape s : model.getModelElementsByType(BpmnShape.class)) {
                    if (s.getBpmnElement() != null && node.getId().equals(s.getBpmnElement().getId())) {
                        s.getBounds().setX(s.getBounds().getX() + offsetAmount);
                    }
                }

                for (SequenceFlow in : node.getIncoming()) {
                    if (!shiftedFlows.contains(in)) {
                        shiftedFlows.add(in);
                        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
                            if (edge.getBpmnElement() != null && in.getId().equals(edge.getBpmnElement().getId())) {
                                for (Waypoint wp : edge.getWaypoints()) {
                                    wp.setX(wp.getX() + offsetAmount);
                                }
                            }
                        }
                    }
                }

                for (SequenceFlow out : node.getOutgoing()) {
                    if (!shiftedFlows.contains(out)) {
                        shiftedFlows.add(out);
                        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
                            if (edge.getBpmnElement() != null && out.getId().equals(edge.getBpmnElement().getId())) {
                                for (Waypoint wp : edge.getWaypoints()) {
                                    wp.setX(wp.getX() + offsetAmount);
                                }
                            }
                        }
                    }
                    if (out.getTarget() != null) {
                        queue.add(out.getTarget());
                    }
                }
            }
        }
    }

    public <T extends FlowNode> T createTask(BpmnModelInstance model, Class<T> clazz, String name) {
        T task = model.newInstance(clazz);
        task.setId(clazz.getSimpleName() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        task.setName(name);
        Process process = model.getModelElementsByType(Process.class).iterator().next();
        process.addChildElement(task);
        return task;
    }

    public <T extends Gateway> T createGateway(BpmnModelInstance model, Class<T> clazz, String name) {
        T gw = model.newInstance(clazz);
        gw.setId(clazz.getSimpleName() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        if (name != null) {
            gw.setName(name);
        }
        Process process = model.getModelElementsByType(Process.class).iterator().next();
        process.addChildElement(gw);
        return gw;
    }

    public IntermediateCatchEvent createTimer(BpmnModelInstance model, String name) {
        IntermediateCatchEvent timer = model.newInstance(IntermediateCatchEvent.class);
        timer.setId("Event_Timer_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        if (name != null) {
            timer.setName(name);
        }
        TimerEventDefinition def = model.newInstance(TimerEventDefinition.class);
        timer.getEventDefinitions().add(def);
        Process process = model.getModelElementsByType(Process.class).iterator().next();
        process.addChildElement(timer);
        return timer;
    }

    public double getX(BpmnModelInstance m, BaseElement e) {
        if (e == null) return 100.0;
        for (BpmnShape s : m.getModelElementsByType(BpmnShape.class)) {
            if (s.getBpmnElement() != null && e.getId().equals(s.getBpmnElement().getId())) {
                return s.getBounds().getX();
            }
        }
        return 100.0;
    }

    public double getY(BpmnModelInstance m, BaseElement e) {
        if (e == null) return 100.0;
        for (BpmnShape s : m.getModelElementsByType(BpmnShape.class)) {
            if (s.getBpmnElement() != null && e.getId().equals(s.getBpmnElement().getId())) {
                return s.getBounds().getY();
            }
        }
        return 100.0;
    }
}