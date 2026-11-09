# Corrective review — fixed10

## Changes in this pass

1. **No longer infer parallelism from missing evidence.** A multi-recipient plan is `PARALLEL` only when the global choreography provides a common parallel gateway. A common exclusive gateway remains `EXCLUSIVE`; when there is neither a strict global order nor gateway evidence, the plan is `UNRESOLVED` and synthesis stops with a diagnostic instead of inventing semantics.
2. **Loop/cycle-aware sequence inference.** Two global tasks are considered strictly ordered only when exactly one precedence direction is reachable. If both directions are reachable (for example, because of a loop), the planner does not treat the pair as a total sequence.
3. **Preflight before rewriting.** The weaver validates activation modes and communication anchors before changing any BPMN model. An unsupported fan-out or non-linear anchor therefore fails before the first model is mutated.
4. **Multiple start events.** Generated-task validation now accepts a path from any start event, consistent with the behavioral check, rather than assuming the first enumerated start event is the only relevant one.
5. **Regression test.** Added a planner test asserting that missing global order/gateway evidence is classified as `UNRESOLVED`, not `PARALLEL`.

## Validation status

- XML and JSON static validation is performed on the packaged input resources.
- Java sources are syntax-parsed with the JDK compiler parser.
- A fresh Maven/JUnit run was not possible in this environment because the Maven executable is unavailable. The uploaded archive included prior `target/` outputs and reports, but those are stale build artifacts and are intentionally excluded from this clean source archive. Run `mvn clean test` locally to verify the newly added regression test and all source changes.

## Remaining thesis-fidelity gaps

- The behavioral checker still verifies data compatibility and graph reachability; it does not perform full symbolic simulation of domain preconditions/effects and all subsequent task behavior from thesis §5.6. The current BPMN inputs do not declare sufficient machine-readable preconditions/effects to derive that behavior reliably.
- Exclusive fan-out is still rejected because the planner/weaver does not yet project per-branch executable conditions into the generated local process. Conditions must be derived from the actual global outgoing sequence flows, not fabricated.
- Non-linear producer/consumer anchors with multiple incoming/outgoing flows are rejected safely. General projection across arbitrary gateways, joins, loops, boundary events, and multiple process starts/ends remains incomplete.
- The unified collaboration BPMN is a structural/visualization artifact and is not a deployed executable multi-robot runtime.

These changes improve conservative correctness; they do not establish complete or 100% fidelity to the thesis.
