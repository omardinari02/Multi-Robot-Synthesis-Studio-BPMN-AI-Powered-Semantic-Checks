package it.univaq.disim.mrs.synthesis.model;

/**
 * Modella una corrispondenza validata (sintatticamente e semanticamente)
 * tra un task produttore di un robot mittente e un task consumatore di un robot destinatario.
 */
public class InteractionMatch {
    private String senderRobot;
    private String senderTaskId;
    private String receiverRobot;
    private String receiverTaskId;
    private String messageName;
    private String dataType;
    private String semanticDescription;
    private String generatedSendTaskId;
    private String generatedReceiveTaskId;
    private String branchConditionExpression;

    public InteractionMatch(String sender, String senderTask, String receiver, String receiverTask, String msg) {
        this(sender, senderTask, receiver, receiverTask, msg, "Object", "");
    }

    public InteractionMatch(String sender, String senderTask, String receiver, String receiverTask,
                            String msg, String dataType, String semanticDescription) {
        this.senderRobot = sender;
        this.senderTaskId = senderTask;
        this.receiverRobot = receiver;
        this.receiverTaskId = receiverTask;
        this.messageName = msg;
        this.dataType = dataType;
        this.semanticDescription = semanticDescription;
    }

    public String getSenderRobot() {
        return senderRobot;
    }

    public void setSenderRobot(String senderRobot) {
        this.senderRobot = senderRobot;
    }

    public String getSenderTaskId() {
        return senderTaskId;
    }

    public void setSenderTaskId(String senderTaskId) {
        this.senderTaskId = senderTaskId;
    }

    public String getReceiverRobot() {
        return receiverRobot;
    }

    public void setReceiverRobot(String receiverRobot) {
        this.receiverRobot = receiverRobot;
    }

    public String getReceiverTaskId() {
        return receiverTaskId;
    }

    public void setReceiverTaskId(String receiverTaskId) {
        this.receiverTaskId = receiverTaskId;
    }

    public String getMessageName() {
        return messageName;
    }

    public void setMessageName(String messageName) {
        this.messageName = messageName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getSemanticDescription() {
        return semanticDescription;
    }

    public void setSemanticDescription(String semanticDescription) {
        this.semanticDescription = semanticDescription;
    }

    public String getGeneratedSendTaskId() { return generatedSendTaskId; }
    public void setGeneratedSendTaskId(String generatedSendTaskId) { this.generatedSendTaskId = generatedSendTaskId; }
    public String getGeneratedReceiveTaskId() { return generatedReceiveTaskId; }
    public void setGeneratedReceiveTaskId(String generatedReceiveTaskId) { this.generatedReceiveTaskId = generatedReceiveTaskId; }
    public String getBranchConditionExpression() { return branchConditionExpression; }
    public void setBranchConditionExpression(String branchConditionExpression) { this.branchConditionExpression = branchConditionExpression; }

    @Override
    public String toString() {
        return String.format("%s (%s) --[%s : %s]--> %s (%s) | Semantica: %s",
                senderRobot, senderTaskId, messageName, dataType, receiverRobot, receiverTaskId, semanticDescription);
    }
}