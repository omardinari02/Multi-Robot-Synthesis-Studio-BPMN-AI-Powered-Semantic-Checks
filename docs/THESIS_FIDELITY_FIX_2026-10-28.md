# Thesis-fidelity fix — 2026-10-28

## Changes

- Restored the availability-collection `ParallelGateway` in `GlobalMission.bpmn` and connected it to the existing exclusive availability decision.
- Preserved the existing 10-second timer retry loop in the global choreography.
- Added a projection step to `BpmnFlowWeaver` that detects the global guarded retry pattern and inserts the corresponding local control structure into the receiving robot's enhanced process when the availability and assignment interactions are adjacent and can be resolved unambiguously.
- The projected local structure contains a parallel collection gateway, an exclusive availability decision, a timer carrying the source duration, and a timer path back to the generated availability `ReceiveTask`.
- Expanded the existing integration tests to check the global gateway/timer structure and the projected retry structure in the enhanced Drone model.

## Validation status

- The changed `GlobalMission.bpmn` was parsed as XML; element IDs are unique and all sequence-flow source/target references resolve.
- Maven tests were **not run in the patching environment** because Maven is not installed there. Run `mvn clean test` on the project before treating the patch as verified.

## Known limitation

The case-study BPMN has one availability interaction. Therefore the parallel gateway currently has one incoming path and acts as a structural collection point; it does not by itself prove synchronization across an arbitrary number of robots. A true 1-to-N availability collection requires the input choreography to declare the participating availability branches and their completion semantics explicitly. The implementation intentionally does not invent those branches from robot names.
