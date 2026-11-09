# Remediations applied after review

## Changes

1. `UnifiedDiagramBuilder` now adds the global choreography control process as a dedicated participant in `UnifiedMission.bpmn`, rather than dropping its control-flow topology.
2. The global process clone preserves sequence-flow topology, flow names, branch condition expressions, and timer duration expressions.
3. Timer definitions are copied when local intermediate catch events are cloned; they are no longer replaced by empty timer definitions.
4. The supplied `GlobalMission.bpmn` now explicitly declares the availability decision conditions:
   - `Flow_YES`: `${availability == true}`
   - `Flow_NO`: `${availability == false}`
5. The integration test now checks that the unified output retains the 10-second timer, the retry loop to `Task_availability`, the unavailable branch, the availability conditions, and all six message flows.

## Important limitation

The unified model is still a synthesis/visualization artifact (`isExecutable=false`); it is not a complete deployed multi-robot runtime. The expressions assume a Boolean process variable named `availability` is supplied by the surrounding execution environment. The current local BPMN and semantic metadata do not define a full runtime adapter that maps received messages to process variables, nor do they encode all domain preconditions/effects needed for a full state-space simulation. Therefore these changes preserve and make explicit the retry control logic in the BPMN model, but do not claim end-to-end execution in a robot runtime.

## Verification status

- All five `src/main/resources/bpmn/*.bpmn` files passed an XML well-formedness check.
- Maven tests could not be executed in the editing environment because Maven (`mvn`) is not installed here. Run `mvn clean test` in an environment with Maven before treating the patch as build-verified.


## Follow-up fixes (24 October 2026)

1. `LocalProcessAnalyzer` now scans the BPMN `Task` superclass instead of only `ServiceTask`, so declared inputs/outputs on other BPMN task variants can participate in matching.
2. `BehavioralCompatibilityTester` now accepts a consumer if it is reachable from **any** start event and can reach **any** end event. It no longer depends on whichever start event the BPMN model API happens to enumerate first.
3. Added regression tests for metadata extraction from a `UserTask` and for consumer reachability in a process with multiple start events.

These fixes improve task coverage and structural checking, but they do not implement thesis section 5.6's full precondition/effect simulation. That requires explicit, machine-readable domain preconditions/effects and a defined state-transition model; those are not currently present in the supplied BPMN/semantic inputs.
