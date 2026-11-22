package it.univaq.disim.mrs.synthesis.generator;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;

public class BpmndiGenerator {

    public static BpmnShape createShape(BpmnModelInstance modelInstance, BpmnPlane plane, BaseElement element,
                                        double x, double y, double width, double height) {
        BpmnShape shape = modelInstance.newInstance(BpmnShape.class);
        shape.setBpmnElement(element);
        shape.setId(element.getId() + "_di");

        Bounds bounds = modelInstance.newInstance(Bounds.class);
        bounds.setX(x);
        bounds.setY(y);
        bounds.setWidth(width);
        bounds.setHeight(height);
        shape.setBounds(bounds);

        plane.addChildElement(shape);
        return shape;
    }

    public static void createEdge(BpmnModelInstance modelInstance, BpmnPlane plane, BaseElement flow,
                                  double srcX, double srcY, double trgX, double trgY) {
        java.util.List<double[]> waypoints = new java.util.ArrayList<>();
        waypoints.add(new double[]{srcX, srcY});
        waypoints.add(new double[]{trgX, trgY});
        createEdge(modelInstance, plane, flow, waypoints);
    }

    public static void createEdge(BpmnModelInstance modelInstance, BpmnPlane plane, BaseElement flow,
                                  java.util.List<double[]> waypoints) {
        BpmnEdge edge = modelInstance.newInstance(BpmnEdge.class);
        edge.setBpmnElement(flow);
        edge.setId(flow.getId() + "_di");

        for (double[] pt : waypoints) {
            Waypoint wp = modelInstance.newInstance(Waypoint.class);
            wp.setX(pt[0]);
            wp.setY(pt[1]);
            edge.getWaypoints().add(wp);
        }

        plane.addChildElement(edge);
    }
}