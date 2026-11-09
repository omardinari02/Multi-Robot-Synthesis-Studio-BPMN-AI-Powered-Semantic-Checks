package it.univaq.disim.mrs.synthesis.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Explicit semantic specification for one message in the global choreography.
 * The producer fields identify the task that actually creates the data. Optional
 * receiver fields disambiguate the consumer and its intended semantic concept.
 */
public class DataNode {
    private String robotId;
    private String taskId;
    private String messageName;
    private String dataType;
    private String semanticDescription;
    private String semanticConcept;
    private String receiverTaskId;
    private String receiverDataType;
    private String receiverSemanticDescription;
    private String receiverSemanticConcept;
    /** STRONG requires exact type equality; WEAK permits declared subtype compatibility. Defaults to WEAK for backwards compatibility. */
    private String syntacticCompatibility = "WEAK";
    /** Optional fields emitted by the producer for structural compatibility checks. */
    private List<String> producedFields = new ArrayList<>();
    /** Optional fields required by the receiver. */
    private List<String> receiverFields = new ArrayList<>();

    public DataNode() {
        // Required by Jackson.
    }

    public DataNode(String robotId, String taskId, String messageName, String dataType, String semanticDescription) {
        this.robotId = robotId;
        this.taskId = taskId;
        this.messageName = messageName;
        this.dataType = dataType;
        this.semanticDescription = semanticDescription;
    }

    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getMessageName() { return messageName; }
    public void setMessageName(String messageName) { this.messageName = messageName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getSemanticDescription() { return semanticDescription; }
    public void setSemanticDescription(String semanticDescription) { this.semanticDescription = semanticDescription; }
    public String getSemanticConcept() { return semanticConcept; }
    public void setSemanticConcept(String semanticConcept) { this.semanticConcept = semanticConcept; }
    public String getReceiverTaskId() { return receiverTaskId; }
    public void setReceiverTaskId(String receiverTaskId) { this.receiverTaskId = receiverTaskId; }
    public String getReceiverDataType() { return receiverDataType; }
    public void setReceiverDataType(String receiverDataType) { this.receiverDataType = receiverDataType; }
    public String getReceiverSemanticDescription() { return receiverSemanticDescription; }
    public void setReceiverSemanticDescription(String receiverSemanticDescription) { this.receiverSemanticDescription = receiverSemanticDescription; }
    public String getReceiverSemanticConcept() { return receiverSemanticConcept; }
    public void setReceiverSemanticConcept(String receiverSemanticConcept) { this.receiverSemanticConcept = receiverSemanticConcept; }
    public String getSyntacticCompatibility() { return syntacticCompatibility; }
    public void setSyntacticCompatibility(String syntacticCompatibility) { this.syntacticCompatibility = syntacticCompatibility; }
    public List<String> getProducedFields() { return producedFields; }
    public void setProducedFields(List<String> producedFields) { this.producedFields = producedFields == null ? new ArrayList<>() : new ArrayList<>(producedFields); }
    public List<String> getReceiverFields() { return receiverFields; }
    public void setReceiverFields(List<String> receiverFields) { this.receiverFields = receiverFields == null ? new ArrayList<>() : new ArrayList<>(receiverFields); }

    public boolean hasRequiredProducerFields() {
        return notBlank(robotId) && notBlank(taskId) && notBlank(messageName)
                && notBlank(dataType) && notBlank(semanticDescription);
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "DataNode{" + messageName + ", producer=" + robotId + "/" + taskId
                + ", type=" + dataType + ", concept=" + semanticConcept + '}';
    }
}
