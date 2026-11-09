package it.univaq.disim.mrs.synthesis.event;

/**
 * Observer interface to receive synthesis milestones in real time.
 * Implementations can log, buffer, or stream events via HTTP Server-Sent Events (SSE).
 */
@FunctionalInterface
public interface SynthesisEventListener {

    /** Handle an incoming synthesis event. */
    void onEvent(SynthesisEvent event);

    /** Convenience no-op listener for default execution and tests. */
    SynthesisEventListener NO_OP = event -> {};
}
