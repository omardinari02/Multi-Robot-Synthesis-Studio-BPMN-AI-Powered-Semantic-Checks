package it.univaq.disim.mrs.synthesis.event;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event model representing a milestone, check, or diagnostic during the synthesis pipeline.
 * Fully serializable to JSON for live browser streaming (SSE / WebSocket).
 */
public class SynthesisEvent implements Serializable {

    public enum Type {
        PIPELINE_STARTED,
        STEP_STARTED,
        ROBOT_LOADED,
        GLOBAL_MESSAGE_DISCOVERED,
        SYNTACTIC_CHECK,
        SEMANTIC_CHECK,
        BEHAVIORAL_CHECK,
        PLAN_CREATED,
        WEAVING_TASK,
        DIAGNOSTIC,
        PIPELINE_COMPLETED
    }

    private final Type type;
    private final String timestamp;
    private final int stepNumber;
    private final String stepName;
    private final String message;
    private final String sourceRobot;
    private final String targetRobot;
    private final String interactionName;
    private final boolean success;
    private final String details;

    public SynthesisEvent(Type type, int stepNumber, String stepName, String message,
                          String sourceRobot, String targetRobot, String interactionName,
                          boolean success, String details) {
        this.type = type;
        this.timestamp = Instant.now().toString();
        this.stepNumber = stepNumber;
        this.stepName = stepName;
        this.message = message;
        this.sourceRobot = sourceRobot;
        this.targetRobot = targetRobot;
        this.interactionName = interactionName;
        this.success = success;
        this.details = details;
    }

    public static SynthesisEvent info(Type type, int stepNumber, String stepName, String message) {
        return new SynthesisEvent(type, stepNumber, stepName, message, null, null, null, true, null);
    }

    public static SynthesisEvent check(Type type, int stepNumber, String stepName, String interactionName,
                                       String sourceRobot, String targetRobot, boolean success, String details) {
        return new SynthesisEvent(type, stepNumber, stepName, null, sourceRobot, targetRobot, interactionName, success, details);
    }

    public Type getType() { return type; }
    public String getTimestamp() { return timestamp; }
    public int getStepNumber() { return stepNumber; }
    public String getStepName() { return stepName; }
    public String getMessage() { return message; }
    public String getSourceRobot() { return sourceRobot; }
    public String getTargetRobot() { return targetRobot; }
    public String getInteractionName() { return interactionName; }
    public boolean isSuccess() { return success; }
    public String getDetails() { return details; }
}
