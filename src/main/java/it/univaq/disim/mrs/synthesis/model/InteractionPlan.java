package it.univaq.disim.mrs.synthesis.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Piano di attivazione e integrazione per un insieme di interazioni (Caso generale 1 -> N).
 */
public class InteractionPlan {
    private String senderRobot;
    private String anchorTaskId;
    private String activationMode; // "SEQUENCE", "PARALLEL", "EXCLUSIVE"
    private boolean masterCoordinator;
    private List<InteractionMatch> receiverMatches = new ArrayList<>();

    public String getSenderRobot() {
        return senderRobot;
    }

    public void setSenderRobot(String senderRobot) {
        this.senderRobot = senderRobot;
    }

    public String getAnchorTaskId() {
        return anchorTaskId;
    }

    public void setAnchorTaskId(String anchorTaskId) {
        this.anchorTaskId = anchorTaskId;
    }

    public String getActivationMode() {
        return activationMode;
    }

    public void setActivationMode(String activationMode) {
        this.activationMode = activationMode;
    }

    public boolean isMasterCoordinator() {
        return masterCoordinator;
    }

    public void setMasterCoordinator(boolean masterCoordinator) {
        this.masterCoordinator = masterCoordinator;
    }

    public List<InteractionMatch> getReceiverMatches() {
        return receiverMatches;
    }

    public void addReceiverMatch(InteractionMatch match) {
        this.receiverMatches.add(match);
    }
}