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
                String robotName = entry.getKey().replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
                String filePath = outputDir + robotName + "_Visual_Thesis.drawio";
                generateDynamicDrawio(context, entry.getValue(), robotName, filePath);
            }

            // 2. Generate the unified diagram (Unified Mission) matching Figure 5.14
            generateUnifiedDrawio(context, outputDir + "UnifiedMission_Visual_Thesis.drawio");

            logger.info("Draw.io files generated successfully in {}", outputDir);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate Draw.io files in " + outputDir, e);
        }
    }

    private void generateDynamicDrawio(MissionContext context, BpmnModelInstance model, String robotName, String filePath) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<mxfile><diagram name=\"").append(robotName).append("_Enhanced\"><mxGraphModel dx=\"1200\" dy=\"1200\" grid=\"1\" gridSize=\"10\" guides=\"1\" tooltips=\"1\" connect=\"1\" arrows=\"1\" fold=\"1\" page=\"1\" pageScale=\"1\" pageWidth=\"1800\" pageHeight=\"1000\" background=\"#000000\" math=\"0\" shadow=\"0\">\n");
        xml.append("  <root>\n");
        xml.append("    <mxCell id=\"0\" />\n");
        xml.append("    <mxCell id=\"1\" parent=\"0\" />\n");

        for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
            BaseElement element = shape.getBpmnElement();
            if (element == null) continue;
            double x = shape.getBounds().getX();
            double y = shape.getBounds().getY();
            double w = shape.getBounds().getWidth();
            double h = shape.getBounds().getHeight();
            String id = element.getId();
            String name = (element instanceof FlowNode && ((FlowNode) element).getName() != null) ? ((FlowNode) element).getName() : "";

            if (element instanceof SendTask || element instanceof ReceiveTask) {
                String cleanMsg = extractCleanMessageName(name);
                String sender = element instanceof SendTask ? robotName : findTargetRobot(context, cleanMsg, false);
                String receiver = element instanceof ReceiveTask ? robotName : findTargetRobot(context, cleanMsg, true);
                String op = UnifiedDiagramBuilder.getOperationNameFromGlobalMission(context, cleanMsg);
                boolean isParallel = "weedPosition".equalsIgnoreCase(cleanMsg) || "availability".equalsIgnoreCase(cleanMsg);
                xml.append(createChoreographyTask(id, sender, op, receiver, cleanMsg, isParallel, x, y, 150, 90));
            } else if (element instanceof ServiceTask) {
                xml.append(createNormalTask(id, name, x, y, w, h));
            } else if (element instanceof ExclusiveGateway || element instanceof ParallelGateway) {
                String type = element instanceof ExclusiveGateway ? "X" : "+";
                xml.append(createGateway(id, type, name, x, y, w, h));
            } else if (element instanceof IntermediateCatchEvent) {
                xml.append(createTimer(id, name.isEmpty() ? "Attesa 10 secondi" : name, x, y, w, h));
            } else if (element instanceof StartEvent) {
                xml.append(createStartEvent(id, robotName, x, y, w, h));
            } else if (element instanceof EndEvent) {
                xml.append(createEndEvent(id, robotName, x, y, w, h));
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
        xml.append("<mxfile><diagram name=\"Unified_Mission\"><mxGraphModel dx=\"1400\" dy=\"1400\" grid=\"1\" gridSize=\"10\" guides=\"1\" tooltips=\"1\" connect=\"1\" arrows=\"1\" fold=\"1\" page=\"1\" pageScale=\"1\" pageWidth=\"2500\" pageHeight=\"1800\" background=\"#000000\" math=\"0\" shadow=\"0\">\n");
        xml.append("  <root>\n");
        xml.append("    <mxCell id=\"0\" />\n");
        xml.append("    <mxCell id=\"1\" parent=\"0\" />\n");

        List<String> orderedKeys = UnifiedDiagramBuilder.getOrderedRobotKeys(context);
        List<String> dynamicRobotOrder = new ArrayList<>();
        for (String key : orderedKeys) {
            dynamicRobotOrder.add(key.replace(".bpmn", "").replace("_enhanced", "").replace("Part_", ""));
        }

        Map<String, Integer> poolYMap = new HashMap<>();
        int poolHeight = 220;
        int poolGap = 130;
        int currentY = 50;
        int poolWidth = 2400;

        for (String robot : dynamicRobotOrder) {
            String matchKey = getRobotKey(context, robot);
            if (matchKey != null) {
                poolYMap.put(matchKey, currentY);
                xml.append(createPool("pool_" + robot, robot, 20, currentY, poolWidth, poolHeight));
                currentY += poolHeight + poolGap;
            }
        }
        boolean hasBroadcast = context.getValidatedMatches().stream()
                .anyMatch(match -> "Broadcast".equalsIgnoreCase(match.getReceiverRobot()));
        if (hasBroadcast) {
            poolYMap.put("Broadcast", currentY);
            xml.append(createPool("pool_Broadcast", "Broadcast", 20, currentY, poolWidth, poolHeight));
            currentY += poolHeight + poolGap;
        }

        // Deduplication System (Nidam dyal T-taw7id)
        Map<String, String> interactionMap = new HashMap<>();
        List<String> drawnMessages = new ArrayList<>();

        for (String key : orderedKeys) {
            String robotName = key.replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
            int baseY = poolYMap.getOrDefault(key, 50) + 60;

            BpmnModelInstance model = context.getRobotModel(key);
            for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
                BaseElement element = shape.getBpmnElement();
                if (element == null) continue;
                double x = shape.getBounds().getX() + 50;
                double origY = shape.getBounds().getY();
                double y = baseY + (origY % 100);
                double w = shape.getBounds().getWidth();
                double h = shape.getBounds().getHeight();
                String id = robotName + "_" + element.getId();
                String name = (element instanceof FlowNode && ((FlowNode) element).getName() != null) ? ((FlowNode) element).getName() : "";

                if (element instanceof SendTask || element instanceof ReceiveTask) {
                    String cleanMsg = extractCleanMessageName(name);
                    if (cleanMsg.isEmpty()) cleanMsg = element.getId();
                    String sharedId = "Choreo_" + cleanMsg.replaceAll("[^A-Za-z0-9_]", "");
                    interactionMap.put(id, sharedId);

                    if (!drawnMessages.contains(cleanMsg.toLowerCase())) {
                        String targetRobot = findTargetRobot(context, cleanMsg, element instanceof SendTask);
                        String targetKey = "Broadcast".equalsIgnoreCase(targetRobot)
                                ? "Broadcast" : getRobotKey(context, targetRobot);
                        int targetY = poolYMap.getOrDefault(targetKey, baseY);
                        int myPoolY = poolYMap.getOrDefault(key, baseY);

                        int topPoolY = Math.min(myPoolY, targetY);
                        y = topPoolY + poolHeight + 20; // Centered in the corridor between pools

                        String sender = element instanceof SendTask ? robotName : targetRobot;
                        String receiver = element instanceof ReceiveTask ? robotName : targetRobot;
                        String operationName = UnifiedDiagramBuilder.getOperationNameFromGlobalMission(context, cleanMsg);
                        boolean isParallel = "weedPosition".equalsIgnoreCase(cleanMsg) || "availability".equalsIgnoreCase(cleanMsg);

                        xml.append(createChoreographyTask(sharedId, sender, operationName, receiver, cleanMsg, isParallel, x, y, 150, 90));
                        drawnMessages.add(cleanMsg.toLowerCase());
                    }
                } else if (element instanceof ServiceTask) {
                    xml.append(createNormalTask(id, name, x, y, w, h));
                } else if (element instanceof ExclusiveGateway || element instanceof ParallelGateway) {
                    String type = element instanceof ExclusiveGateway ? "X" : "+";
                    xml.append(createGateway(id, type, name, x, y, w, h));
                } else if (element instanceof IntermediateCatchEvent) {
                    xml.append(createTimer(id, name.isEmpty() ? "Attesa 10 secondi" : name, x, y, w, h));
                } else if (element instanceof StartEvent) {
                    xml.append(createStartEvent(id, robotName, x, y, w, h));
                } else if (element instanceof EndEvent) {
                    xml.append(createEndEvent(id, robotName, x, y, w, h));
                }
            }

            for (BpmnEdge edge : model.getModelElementsByType(BpmnEdge.class)) {
                BaseElement element = edge.getBpmnElement();
                if (element instanceof SequenceFlow) {
                    SequenceFlow flow = (SequenceFlow) element;
                    String sourceId = robotName + "_" + flow.getSource().getId();
                    String targetId = robotName + "_" + flow.getTarget().getId();
                    String edgeLabel = flow.getName() != null ? flow.getName() : "";

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
            String cleanKey = key.replace(".bpmn", "").replace("_enhanced", "").replace("Part_", "");
            if (cleanKey.equalsIgnoreCase(name)) return key;
        }
        for (String key : context.getAllRobotModels().keySet()) {
            if (key.toLowerCase().contains(name.toLowerCase())) return key;
        }
        return null;
    }

    private String extractCleanMessageName(String taskName) {
        if (taskName == null) return "";
        String clean = taskName.trim();
        if (clean.startsWith("Invia: ")) clean = clean.substring(7).trim();
        else if (clean.startsWith("Invia ")) clean = clean.substring(6).trim();
        else if (clean.startsWith("Ricevi: ")) clean = clean.substring(8).trim();
        else if (clean.startsWith("Ricevi ")) clean = clean.substring(7).trim();

        if (clean.contains("[msg:")) {
            int start = clean.indexOf("[msg:") + 5;
            int end = clean.indexOf("]", start);
            if (end != -1) {
                String sub = clean.substring(start, end).trim();
                if (sub.contains("|")) sub = sub.substring(0, sub.indexOf('|')).trim();
                return sub;
            }
        }

        if (clean.contains("(")) {
            clean = clean.substring(0, clean.indexOf('(')).trim();
        }
        clean = clean.replace("|||", "").trim();
        return clean;
    }

    private String findTargetRobot(MissionContext context, String cleanMsg, boolean isSender) {
        if (cleanMsg == null || cleanMsg.isEmpty()) return "?";
        for (InteractionMatch m : context.getValidatedMatches()) {
            if (m.getMessageName().equalsIgnoreCase(cleanMsg)) {
                String target = isSender ? m.getReceiverRobot() : m.getSenderRobot();
                return target.replace(".bpmn", "").replace("Part_", "").replace("_enhanced", "");
            }
        }
        return "?";
    }

    // --- Helper Methods (Dark Theme & Styling for Thesis Figure 5.14) ---
    private String createPool(String id, String name, double x, double y, double w, double h) {
        return "<mxCell id=\"" + id + "\" value=\"&lt;b&gt;" + name + "&lt;/b&gt;\" style=\"swimlane;horizontal=0;whiteSpace=wrap;html=1;fillColor=#000000;fontColor=#ffffff;strokeColor=#ffffff;strokeWidth=2;startSize=40;collapsible=0;fontSize=12;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + w + "\" height=\"" + h + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createNormalTask(String id, String name, double x, double y, double w, double h) {
        return "<mxCell id=\"" + id + "\" value=\"" + name + "\" style=\"rounded=1;arcSize=12;whiteSpace=wrap;html=1;fillColor=#121212;fontColor=#ffffff;strokeColor=#ffffff;strokeWidth=1.5;fontSize=11;align=center;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + Math.max(120, w) + "\" height=\"" + Math.max(60, h) + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createChoreographyTask(String id, String top, String mid, String bot, String msg, boolean parallel, double x, double y, double w, double h) {
        String parallelHtml = parallel ? "&lt;div style=&quot;font-weight:bold;letter-spacing:2px;color:#cccccc;font-size:10px;margin-top:2px;&quot;&gt;|||&lt;/div&gt;" : "";
        String msgHtml = (msg != null && !msg.isEmpty()) ? "&lt;div style=&quot;font-size:9px;color:#93c5fd;margin-top:3px;&quot;&gt;&#9993; " + msg + "&lt;/div&gt;" : "";

        String tableValue = "&lt;table style=&quot;width:100%;height:100%;border-collapse:collapse;text-align:center;font-family:Helvetica,sans-serif;&quot;&gt;"
                + "&lt;tr style=&quot;height:24px;background-color:#333333;border-bottom:1px solid #ffffff;&quot;&gt;"
                + "&lt;td style=&quot;color:#ffffff;font-weight:bold;font-size:11px;&quot;&gt;" + top + "&lt;/td&gt;&lt;/tr&gt;"
                + "&lt;tr style=&quot;height:42px;background-color:#1a1a1a;&quot;&gt;"
                + "&lt;td style=&quot;color:#ffffff;font-size:11px;padding:2px 4px;&quot;&gt;&lt;div&gt;" + mid + "&lt;/div&gt;"
                + parallelHtml + msgHtml + "&lt;/td&gt;&lt;/tr&gt;"
                + "&lt;tr style=&quot;height:24px;background-color:#444444;border-top:1px solid #ffffff;&quot;&gt;"
                + "&lt;td style=&quot;color:#ffffff;font-weight:bold;font-size:11px;&quot;&gt;" + bot + "&lt;/td&gt;&lt;/tr&gt;"
                + "&lt;/table&gt;";

        return "<mxCell id=\"" + id + "\" value=\"" + tableValue + "\" style=\"rounded=1;arcSize=10;whiteSpace=wrap;html=1;overflow=hidden;fillColor=#1a1a1a;strokeColor=#ffffff;strokeWidth=1.5;align=center;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + (x - 20) + "\" y=\"" + (y - 10) + "\" width=\"" + Math.max(150, w) + "\" height=\"" + Math.max(90, h) + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createGateway(String id, String type, String label, double x, double y, double w, double h) {
        String labelStyle = (label != null && !label.isEmpty()) ? "verticalLabelPosition=bottom;verticalAlign=top;" : "";
        String value = "&lt;b&gt;" + type + "&lt;/b&gt;";
        if (label != null && !label.isEmpty()) {
            value = "&lt;b&gt;" + type + "&lt;/b&gt;&lt;br&gt;&lt;span style=&quot;font-size:10px;font-weight:normal;&quot;&gt;" + label + "&lt;/span&gt;";
        }
        return "<mxCell id=\"" + id + "\" value=\"" + value + "\" style=\"rhombus;whiteSpace=wrap;html=1;fillColor=#121212;fontColor=#ffffff;strokeColor=#ffffff;strokeWidth=1.5;fontStyle=1;fontSize=14;align=center;" + labelStyle + "\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + Math.max(50, w) + "\" height=\"" + Math.max(50, h) + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createTimer(String id, String label, double x, double y, double w, double h) {
        String displayLabel = (label == null || label.isEmpty() || "Timer".equalsIgnoreCase(label)) ? "Attesa 10 secondi" : label;
        return "<mxCell id=\"" + id + "\" value=\"" + displayLabel + "\" style=\"ellipse;shape=doubleEllipse;whiteSpace=wrap;html=1;fillColor=#121212;fontColor=#ffffff;strokeColor=#ffffff;strokeWidth=1.5;fontSize=10;verticalLabelPosition=top;verticalAlign=bottom;align=center;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + Math.max(40, w) + "\" height=\"" + Math.max(40, h) + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createStartEvent(String id, String robotName, double x, double y, double w, double h) {
        boolean isDrone = "drone".equalsIgnoreCase(robotName);
        String fillColor = isDrone ? "#22c55e" : "#000000";
        String label = isDrone ? "Accensione e inizio missione" : "Accensione";
        return "<mxCell id=\"" + id + "\" value=\"" + label + "\" style=\"ellipse;whiteSpace=wrap;html=1;aspect=fixed;fillColor=" + fillColor + ";strokeColor=#ffffff;strokeWidth=2;verticalLabelPosition=bottom;verticalAlign=top;fontColor=#ffffff;fontSize=10;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + Math.max(36, w) + "\" height=\"" + Math.max(36, h) + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createEndEvent(String id, String robotName, double x, double y, double w, double h) {
        boolean isSeminatore = "seminatore".equalsIgnoreCase(robotName);
        String fillColor = isSeminatore ? "#ef4444" : "#000000";
        String label = isSeminatore ? "Spegnimento e fine missione" : "Spegnimento";
        String shapeStyle = isSeminatore ? "ellipse;" : "ellipse;shape=doubleEllipse;";
        return "<mxCell id=\"" + id + "\" value=\"" + label + "\" style=\"" + shapeStyle + "whiteSpace=wrap;html=1;aspect=fixed;fillColor=" + fillColor + ";strokeColor=#ffffff;strokeWidth=2;verticalLabelPosition=bottom;verticalAlign=top;fontColor=#ffffff;fontSize=10;\" vertex=\"1\" parent=\"1\">\n" +
               "  <mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + Math.max(36, w) + "\" height=\"" + Math.max(36, h) + "\" as=\"geometry\" />\n</mxCell>\n";
    }

    private String createEdge(String id, String source, String target, String label, boolean isDashed) {
        String dashStyle = isDashed ? "dashed=1;dashPattern=6 6;" : "";
        String valueAttr = !label.isEmpty() ? "value=\"" + label + "\" " : "";
        return "<mxCell id=\"" + id + "\" " + valueAttr + "style=\"edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;strokeColor=#ffffff;fontColor=#ffffff;strokeWidth=1.5;" + dashStyle + "fontSize=10;\" edge=\"1\" parent=\"1\" source=\"" + source + "\" target=\"" + target + "\">\n" +
               "  <mxGeometry relative=\"1\" as=\"geometry\" />\n</mxCell>\n";
    }
}
