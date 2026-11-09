# Review and corrective pass — fixed9

## Changes in this pass

1. **MessageFlow references now match the diagram.** The unified builder previously drew each message edge between send/receive task coordinates while the BPMN MessageFlow referenced only the two participant pools. It now references the cloned communication tasks directly and fails with a clear error if a participant, endpoint, or endpoint shape is missing.
2. **Sequential 1-to-N is distinguished from parallel 1-to-N.** The planner now checks reachability/order between the corresponding global choreography tasks. If all matched message tasks are ordered, it sorts the interactions by that order and plans `SEQUENCE`; otherwise it uses the common upstream exclusive/parallel gateway evidence.
3. **The weaver now honours `SEQUENCE`.** A sequence plan creates a chain of send tasks. It no longer falls through to a parallel split/join. Parallel plans retain the parallel split/join. Exclusive fan-out still fails safely until executable branch conditions are available.
4. **Unified processes are marked non-executable.** The unified artifact is a visualization/structural view and does not preserve all task implementation details, so marking it executable would overstate what the generated model can run.
5. **Regression coverage added.** The integration tests now check that unified MessageFlows reference task nodes and include a sequential 1-to-N fixture alongside the existing parallel 1-to-N case.

## Validation performed here

- All BPMN/XML files parse as XML.
- The main semantic JSON, taxonomy JSON, and 1-to-N semantic JSON parse successfully.
- A Maven test run was attempted, but Maven is not installed in this environment. Attempting to install it timed out. Therefore the Java changes and JUnit tests in this pass are **not yet build-verified here**.

## Known remaining limitations

- `BehavioralCompatibilityTester` is structural: it checks declared input compatibility and graph reachability, but does not simulate domain preconditions/effects and subsequent task behavior as described in thesis section 5.6.
- The global availability retry loop exists in `GlobalMission.bpmn`, but its gateway/condition/timer logic is not projected into the enhanced local robot processes. The local enhanced Drone process therefore does not yet implement the full availability decision-and-retry behavior from the global specification.
- Exclusive 1-to-N fan-out is rejected safely because the current input model does not provide executable branch conditions for the generated local gateway.
- `UnifiedDiagramBuilder` clones a supported subset of BPMN elements and does not preserve every possible BPMN property, extension, condition, or event definition. The unified artifact is explicitly non-executable as a result.
- Complex nonlinear local processes, multiple starts/ends, boundary events, and advanced gateway semantics need additional projection/validation coverage.

## Required verification on a machine with Maven

From the project root, run:

```bash
mvn clean test
mvn exec:java -Dexec.mainClass="it.univaq.disim.mrs.synthesis.Main"
```

Inspect `target/output/UnifiedMission.bpmn` in Camunda Modeler and verify both the message-flow endpoints and the enhanced local processes. Do not treat the new changes as fully verified until the Maven tests pass on the updated archive.
