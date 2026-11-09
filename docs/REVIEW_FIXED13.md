# Review and remediation — fixed13

## Changes in this revision

- `InteractionPlanner` now resolves a choreography task across **all BPMN `Process` elements** instead of selecting the first non-empty process when finding task matches for ordering. This prevents valid tasks in later processes from being silently ignored.
- If task/message resolution is ambiguous across processes, the planner returns no unique match and keeps the activation decision conservative rather than guessing.
- The existing protections remain: exact task ID/name matches take precedence; substring fallback must be unique; parallel/exclusive fan-out inference requires recipients to lie on distinct branches of the candidate gateway.
- Build artifacts (`target/`), operating-system metadata, and editor noise have been excluded from the source ZIP.

## Thesis-alignment status (honest assessment)

This revision is a corrective step, **not a claim of 100% thesis fidelity**.

- Sections 5.4–5.5: syntactic and ontology-based semantic matching exist, but their quality depends on the provided metadata and ontology.
- Section 5.6: the current behavioral tester checks declared input-type compatibility and structural reachability. It does **not** yet simulate domain preconditions/effects or prove global choreography consistency. Those facts are not fully represented as machine-readable state transitions in the supplied inputs.
- Section 5.7: the planner infers some sequence/parallel patterns. Exclusive fan-out is intentionally rejected by the weaver until branch conditions can be preserved safely. This is a known unsupported case, not a completed feature.
- Complex joins, loops, multi-incoming/outgoing anchors, and executable runtime message-to-variable mapping require further explicit support and tests.

## Verification

- Source ZIP integrity and XML/JSON resource parsing were checked during packaging.
- The user-provided previous run reported `Tests run: 18, Failures: 0, Errors: 0, BUILD SUCCESS` for the previous revision.
- Maven is not installed in the current editing environment, so the source change in this revision has **not** been verified by `mvn clean test`. Run that command locally before relying on the new revision.
