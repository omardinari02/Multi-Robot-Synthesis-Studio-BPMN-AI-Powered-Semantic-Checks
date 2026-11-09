package it.univaq.disim.mrs.synthesis.generator;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;

public class BpmndiGenerator {

    public static void createShape(BpmnModelInstance modelInstance, BpmnPlane plane, BaseElement element,
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
    }

    public static void createEdge(BpmnModelInstance modelInstance, BpmnPlane plane, BaseElement flow,
                                  double srcX, double srcY, double trgX, double trgY) {
        BpmnEdge edge = modelInstance.newInstance(BpmnEdge.class);
        edge.setBpmnElement(flow);
        edge.setId(flow.getId() + "_di");

        Waypoint start = modelInstance.newInstance(Waypoint.class);
        start.setX(srcX); start.setY(srcY);
        edge.getWaypoints().add(start);

        Waypoint end = modelInstance.newInstance(Waypoint.class);
        end.setX(trgX); end.setY(trgY);
        edge.getWaypoints().add(end);

        plane.addChildElement(edge);
    }
}