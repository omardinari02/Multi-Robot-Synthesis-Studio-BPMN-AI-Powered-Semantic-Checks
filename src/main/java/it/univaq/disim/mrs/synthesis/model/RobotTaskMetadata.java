package it.univaq.disim.mrs.synthesis.model;

import java.util.ArrayList;
import java.util.List;

/** Metadata extracted from a local BPMN task. Contract predicates use key=value literals. */
public class RobotTaskMetadata {
    private String robotId;
    private String taskId;
    private String taskName;
    private String producedDataType;
    private final List<String> requiredDataTypes = new ArrayList<>();
    private final List<String> preconditions = new ArrayList<>();
    private final List<String> effects = new ArrayList<>();

    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getProducedDataType() { return producedDataType; }
    public void setProducedDataType(String producedDataType) { this.producedDataType = producedDataType; }
    public List<String> getRequiredDataTypes() { return requiredDataTypes; }
    public List<String> getPreconditions() { return preconditions; }
    public List<String> getEffects() { return effects; }
}
