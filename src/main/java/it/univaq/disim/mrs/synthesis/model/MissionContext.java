package it.univaq.disim.mrs.synthesis.model;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MissionContext {
    private BpmnModelInstance globalChoreography;
    private final Map<String, BpmnModelInstance> robotModels = new LinkedHashMap<>();
    private final List<RobotTaskMetadata> extractedMetadata = new ArrayList<>();
    private final List<InteractionMatch> validatedMatches = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private final List<String> behavioralNotes = new ArrayList<>();

    public void setGlobalChoreography(BpmnModelInstance model) { this.globalChoreography = model; }
    public BpmnModelInstance getGlobalChoreography() { return globalChoreography; }
    public BpmnModelInstance getRobotModel(String id) { return robotModels.get(id); }
    public void addRobotModel(String id, BpmnModelInstance model) { robotModels.put(id, model); }
    public Map<String, BpmnModelInstance> getAllRobotModels() { return robotModels; }
    public List<RobotTaskMetadata> getExtractedMetadata() { return extractedMetadata; }
    public List<InteractionMatch> getValidatedMatches() { return validatedMatches; }
    public List<String> getDiagnostics() { return diagnostics; }
    public void addDiagnostic(String diagnostic) { if (diagnostic != null && !diagnostic.trim().isEmpty()) diagnostics.add(diagnostic); }
    /** Non-blocking analysis notes, including behavioral proof gaps that do not invalidate structural synthesis. */
    public List<String> getBehavioralNotes() { return behavioralNotes; }
    public void addBehavioralNote(String note) { if (note != null && !note.trim().isEmpty()) behavioralNotes.add(note); }
}
