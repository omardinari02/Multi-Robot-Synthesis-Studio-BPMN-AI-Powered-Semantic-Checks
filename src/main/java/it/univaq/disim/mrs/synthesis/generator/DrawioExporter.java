package it.univaq.disim.mrs.synthesis.generator;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.univaq.disim.mrs.synthesis.model.InteractionMatch;
import it.univaq.disim.mrs.synthesis.model.MissionContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DrawioExporter {
    private static final Logger logger = LoggerFactory.getLogger(DrawioExporter.class);

    public void exportAllToDrawio(MissionContext context, String outputDir) {
        logger.info("--- Generating 100% Dynamic Draw.io Visual Diagrams with Shared Choreography Tasks ---");
        try {
            File dir = new File(outputDir);
            if (!dir.exists()) dir.mkdirs();

            // 1. Generate one diagram for each discovered local robot model.
            for (Map.Entry<String, BpmnModelInstance> entry : context.getAllRobotModels().entrySet()) {
                String robotName = entry.getKey().replace(".bpmn", "").replace("_enhanced", "");
                String filePath = outputDir + robotName + "_Visual_Thesis.drawio";
                generateDynamicDrawio(entry.getValue(), robotName, filePath);
            }

            // 2. Generer le diagramme unifie (Unified Mission) b n-nidam dyal t-taw7id
            generateUnifiedDrawio(context, outputDir + "UnifiedMission_Visual_Thesis.drawio");

            logger.info("Draw.io files generated successfully in {}", outputDir);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate Draw.io files in " + outputDir, e);
        }
    }

    private void generateDynamicDrawio(BpmnModelInstance model, String robotName, String filePath) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<mxfile><diagram name=\"").append(robotName).append("_Enhanced\"><mxGraphModel dx=\"1000\" dy=\"1000\" grid=\"1\" gridSize=\"10\" guides=\"1\" tooltips=\"1\" connect=\"1\" arrows=\"1\" fold=\"1\" page=\"1\" pageScale=\"1\" pageWidth=\"1169\" pageHeight=\"827\" math=\"0\" shadow=\"0\">\n");
        xml.append("  <root>\n");
        xml.append("    <mxCell id=\"0\" />\n");
        xml.append("    <mxCell id=\"1\" parent=\"0\" />\n");

        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            BaseElement element = shape.getBpmnElement();
            double x = shape.getBounds().getX();
            double y = shape.getBounds().getY();
            double w = shape.getBounds().getWidth();
            double h = shape.getBounds().getHeight();
            String id = element.getId();
            String name = (element instanceof FlowNode && ((FlowNode) element).getName() != null) ? ((FlowNode) element).getName() : "";

            if (element instanceof SendTask || element instanceof ReceiveTask) {
                String sender = element instanceof SendTask ? robotName : "?";
                String receiver = element instanceof ReceiveTask ? robotName : "?";
                xml.append(createChoreographyTask(id, sender, name, receiver, x, y, 140, 90));
            } else if (element instanceof ServiceTask) {
                xml.append(createNormalTask(id, name, x, y, w, h));
            } else if (element instanceof ExclusiveGateway || element instanceof ParallelGateway) {
                String type = element instanceof ExclusiveGateway ? "X" : "+";
                xml.append(createGateway(id, type, x, y, w, h));
            } else if (element instanceof IntermediateCatchEvent) {
                xml.append(createTimer(id, name.isEmpty() ? "Timer" : name, x, y, w, h));
            } else if (element instanceof StartEvent) {
                xml.append(createEvent(id, x, y, w, h, "#d5e8d4", "#82b366"));
            } else if (element instanceof EndEvent) {
                xml.append(createEvent(id, x, y, w, h, "#f8cecc", "#b85450"));
            }
        }

        for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
            BaseElement element = edge.getBpmnElement();
            if (element instanceof SequenceFlow) {
                SequenceFlow flow = (SequenceFlow) element;
                String sourceId = flow.getSource().getId();
                String targetId = flow.getTarget().getId();
                String edgeLabel = flow.getName() != null ? flow.getName() : "";
                xml.append(createEdge(flow.getId(), sourceId, targetId, edgeLabel, false));
            }
        }

        xml.append("  </root>\n</mxGraphModel></diagram></mxfile>");
        Files.writeString(Path.of(filePath), xml.toString(), StandardCharsets.UTF_8);
    }

    private void generateUnifiedDrawio(MissionContext context, String filePath) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<mxfile><diagram name=\"Unified_Mission\"><mxGraphModel dx=\"1000\" dy=\"1000\" grid=\"1\" gridSize=\"10\" guides=\"1\" tooltips=\"1\" connect=\"1\" arrows=\"1\" fold=\"1\" page=\"1\" pageScale=\"1\" pageWidth=\"1600\" pageHeight=\"1200\" math=\"0\" shadow=\"0\">\n");
        xml.append("  <root>\n");
        xml.append("    <mxCell id=\"0\" />\n");
        xml.append("    <mxCell id=\"1\" parent=\"0\" />\n");

        // 100% Dynamique: jbd smyat dyal robots
        List<String> dynamicRobotOrder = new ArrayList<>();
        for (String key : context.getAllRobotModels().keySet()) {
            dynamicRobotOrder.add(key.replace(".bpmn", "").replace("_enhanced", "").replace("Part_", ""));
        }

        Map<String, Integer> poolYMap = new HashMap<>();
        int poolHeight = 220;
        int currentY = 50;

        for (String robot : dynamicRobotOrder) {
            String matchKey = getRobotKey(context, robot);
            if (matchKey != null) {
                poolYMap.put(matchKey, currentY);
                xml.append(createPool("pool_" + robot, robot, 20, currentY, 1400, poolHeight));
                currentY += poolHeight + 60;
            }
        }
        boolean hasBroadcast = context.getValidatedMatches().stream()
                .anyMatch(match -> "Broadcast".equalsIgnoreCase(match.getReceiverRobot()));
        if (hasBroadcast) {
            poolYMap.put("Broadcast", currentY);
            xml.append(createPool("pool_Broadcast", "Broadcast", 20, currentY, 1400, poolHeight));
            currentY += poolHeight + 60;
        }

        // Nidam dyal T-taw7id (Deduplication System)
        Map<String, String> interactionMap = new HashMap<>();
        List<String> drawnMessages = new ArrayList<>();

        for (Map.Entry<String, BpmnModelInstance> entry : context.getAllRobotModels().entrySet()) {
            String key = entry.getKey();
            String robotName = key.replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
            int baseY = poolYMap.getOrDefault(key, 50) + 60;

            BpmnModelInstance model = entry.getValue();
            for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
                BaseElement element = shape.getBpmnElement();
                double x = shape.getBounds().getX() + 50;
                double origY = shape.getBounds().getY();
                double y = baseY + (origY % 100);
                double w = shape.getBounds().getWidth();
                double h = shape.getBounds().getHeight();
                String id = robotName + "_" + element.getId();
                String name = (element instanceof FlowNode && ((FlowNode) element).getName() != null) ? ((FlowNode) element).getName() : "";

                if (element instanceof SendTask || element instanceof ReceiveTask) {
                    String cleanName = name.replace("Invia: ", "").replace("Ricevi: ", "").trim();
                    String sharedId = "Choreo_" + cleanName.replaceAll("\\s+",""); // ID mcharek binathom
                    interactionMap.put(id, sharedId);

                    // Ila mazal matrsem, nrsmoh w nmarkiwh
                    if (!drawnMessages.contains(cleanName)) {
                        String targetRobot = findTargetRobot(context, name, element instanceof SendTask);
                        String targetKey = "Broadcast".equalsIgnoreCase(targetRobot)
                                ? "Broadcast" : getRobotKey(context, targetRobot);
                        int targetY = poolYMap.getOrDefault(targetKey, baseY);
                        int myPoolY = poolYMap.getOrDefault(key, baseY);

                        y = (myPoolY + targetY) / 2 + (poolHeight / 2) + 30; // Centre bin les 2 pools

                        String sender = element instanceof SendTask ? robotName : targetRobot;
                        String receiver = element instanceof ReceiveTask ? robotName : targetRobot;

                        xml.append(createChoreographyTask(sharedId, sender, cleanName, receiver, x, y, 140, 80));
                        drawnMessages.add(cleanName);
                    }
                } else if (element instanceof ServiceTask) {
                    xml.append(createNormalTask(id, name, x, y, w, h));
                } else if (element instanceof ExclusiveGateway || element instanceof ParallelGateway) {
                    String type = element instanceof ExclusiveGateway ? "X" : "+";
                    xml.append(createGateway(id, type, x, y, w, h));
                } else if (element instanceof IntermediateCatchEvent) {
                    xml.append(createTimer(id, name.isEmpty() ? "Timer" : name, x, y, w, h));
                } else if (element instanceof StartEvent) {
                    xml.append(createEvent(id, x, y, w, h, "#d5e8d4", "#82b366"));
                } else if (element instanceof EndEvent) {
                    xml.append(createEvent(id, x, y, w, h, "#f8cecc", "#b85450"));
                }
            }

            for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
                BaseElement element = edge.getBpmnElement();
                if (element instanceof SequenceFlow) {
                    SequenceFlow flow = (SequenceFlow) element;
                    String sourceId = robotName + "_" + flow.getSource().getId();
                    String targetId = robotName + "_" + flow.getTarget().getId();
                    String edgeLabel = flow.getName() != null ? flow.getName() : "";

                    // Rbt les fleches b l'mrbe3 l'mcharek
                    if (interactionMap.containsKey(sourceId)) sourceId = interactionMap.get(sourceId);
                    if (interactionMap.containsKey(targetId)) targetId = interactionMap.get(targetId);

                    boolean isDashed = sourceId.startsWith("Choreo_") || targetId.startsWith("Choreo_");

                    xml.append(createEdge(robotName + "_" + flow.getId(), sourceId, targetId, edgeLabel, isDashed));
                }
            }
        }

        xml.append("  </root>\n</mxGraphModel></diagram></mxfile>");
        Files.writeString(Path.of(filePath), xml.toString(), StandardCharsets.UTF_8);
    }

    private String getRobotKey(MissionContext context, String name) {
        if (name == null || name.equals("?")) return null;
        for (String key : context.getAllRobotModels().keySet()) {
            if (key.toLowerCase().contains(name.toLowerCase())) return key;
        }
        return null;
    }

    private String findTargetRobot(MissionContext context, String taskName, boolean isSender) {
        if (taskName == null) return "?";
        String cleanMsg = taskName.replace("Invia: ", "").replace("Ricevi: ", "").trim();
        for (InteractionMatch m : context.getValidatedMatches()) {
            if (m.getMessageName().equalsIgnoreCase(cleanMsg)) {
                String target = isSender ? m.getReceiverRobot() : m.getSenderRobot();
                return target.replace(".bpmn", "").replace("Part_", "").replace("_enhanced", "");
            }
        }
        return "?";
    }

    // --- Helper Methods (Alwan w l'achkal) ---
    private String createPool(String id, String name, double x, double y, double w, double h) {
        return "<mxCell id=\"" + id + "\" value=\"&lt;b&gt;" + name + "&lt;/b&gt;\" style=\"swimlane;horizontal=0;whiteSpace=wrap;html=1;fillColor=#f5f5f5;fontColor=#333333;strokeColor=#666666;startSize=40;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + w + "\" height=\"" + h + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createNormalTask(String id, String name, double x, double y, double w, double h) {
        return "<mxCell id=\"" + id + "\" value=\"" + name + "\" style=\"rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;fontColor=#000000;strokeColor=#6c8ebf;fontStyle=1;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + Math.max(120, w) + "\" height=\"" + Math.max(60, h) + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createChoreographyTask(String id, String top, String mid, String bot, double x, double y, double w, double h) {
        return "<mxCell id=\"" + id + "\" value=\"&lt;b&gt;" + top + "&lt;/b&gt;&lt;hr&gt;" + mid + "&lt;hr&gt;&lt;b&gt;" + bot + "&lt;/b&gt;\" style=\"rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;fontColor=#000000;strokeColor=#82b366;align=center;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + (x - 20) + "\" y=\"" + (y - 10) + "\" width=\"" + w + "\" height=\"" + h + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createGateway(String id, String type, double x, double y, double w, double h) {
        return "<mxCell id=\"" + id + "\" value=\"&lt;b&gt;" + type + "&lt;/b&gt;\" style=\"rhombus;whiteSpace=wrap;html=1;fillColor=#ffe6cc;fontColor=#000000;strokeColor=#d79b00;fontStyle=1;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + Math.max(50, w) + "\" height=\"" + Math.max(50, h) + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createTimer(String id, String label, double x, double y, double w, double h) {
        return "<mxCell id=\"" + id + "\" value=\"" + label + "\" style=\"ellipse;whiteSpace=wrap;html=1;fillColor=#e1d5e7;fontColor=#000000;strokeColor=#9673a6;fontStyle=1;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + Math.max(50, w) + "\" height=\"" + Math.max(50, h) + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createEvent(String id, double x, double y, double w, double h, String fillColor, String strokeColor) {
        return "<mxCell id=\"" + id + "\" value=\"\" style=\"ellipse;whiteSpace=wrap;html=1;fillColor=" + fillColor + ";strokeColor=" + strokeColor + ";strokeWidth=2;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + w + "\" height=\"" + h + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createEdge(String id, String source, String target, String label, boolean isDashed) {
        String dashStyle = isDashed ? "dashed=1;dashPattern=8 8;" : "";
        String valueAttr = !label.isEmpty() ? "value=\"" + label + "\" " : "";
        return "<mxCell id=\"" + id + "\" " + valueAttr + "style=\"edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;strokeColor=#333333;fontColor=#000000;strokeWidth=2;" + dashStyle + "\" edge=\"1\" parent=\"1\" source=\"" + source + "\" target=\"" + target + "\">\n" +
               "  <mxGeometry relative=\"1\" as=\"geometry\" />\n</mxCell>\n";
    }
}