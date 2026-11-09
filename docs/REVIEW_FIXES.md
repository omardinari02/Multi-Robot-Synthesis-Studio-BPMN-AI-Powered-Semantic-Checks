# Review fixes applied

## Change made in this revision

- Removed the second cloning pass for the global choreography from `UnifiedDiagramBuilder`.
- The builder already clones the global process in `addGlobalChoreography(...)`, including its sequence flows, conditional expressions, and timer event definition. Calling `appendGlobalControlFlow(...)` afterwards cloned the same control flow a second time with a second set of IDs. That duplicate pass has been removed.
- Added an integration-test assertion that the unified collaboration contains exactly one participant named `Global Choreography Flow` and does not contain a doubly-prefixed retry timer ID.

## Static checks performed in this environment

- Parsed all 11 `.bpmn` files under `src/` as XML successfully.
- Confirmed the duplicate method and call are absent from the source tree.
- Java 21 is available in this environment, but Maven (`mvn`) and a Maven wrapper (`mvnw`) are not installed, so the Maven test suite could not be executed here. Do not interpret this as a passing test run.

## Important remaining limitation

The unified diagram preserves the global BPMN control-flow topology and its `PT10S` timer, but this is not the same as proving executable orchestration across robot-local processes. The source global BPMN must define meaningful gateway conditions (for example `${availability == true}` and `${availability == false}`) and those variables must be set by the runtime integration. The supplied BPMN is a design/projection model; the synthesis engine does not by itself deploy and execute the whole multi-robot mission.

The behavioural checker remains conservative: it checks declared input-type compatibility and graph reachability. It does not simulate arbitrary task preconditions/effects or prove full behavioural equivalence. Therefore this revision should not be described as 100% thesis-compliant until tests against the thesis requirements and runtime semantics are completed.
