# Behavioral testing — implementation note

## Implemented in this revision

- Task metadata includes explicit preconditions and effects in addition to input/output data types.
- `LocalProcessAnalyzer` extracts `precondition`/`preconditions` from Camunda input parameters and `effect`/`effects` from Camunda output parameters, including parameters nested under `camunda:inputOutput`.
- Contract values use a deliberately small syntax: `key=value`; semicolon-separated literals are accepted.
- `BehavioralStateSimulator` uses explicit state only. Producer effects are applied before consumer preconditions are checked. It reports `PASS`, `FAIL`, or `UNRESOLVED`.
- Unknown state, unsupported predicates, or absent contracts are never treated as a successful behavioral proof.
- The synthesis pipeline rejects a candidate when explicit contracts fail or cannot be resolved.
- Ontology type checks now expose explicit strong equality and weak directional subsumption methods; the existing `isCompatible` API remains an alias for weak compatibility.

## Scope boundary

The supplied robot BPMNs mostly declare `producedData` and `requiredData`; they do not encode complete task contracts. Consequently the simulator cannot prove the thesis's full §5.6 requirements for those models. It currently simulates the explicit producer-to-consumer contract handoff only; it does not yet simulate every subsequent task, all alternative BPMN branches/loops, or the complete global choreography. No semantics are inferred from task names or Java delegate class names.

## Verification

New unit tests cover a producer effect satisfying a consumer precondition, a violated precondition, missing state, and missing contracts. Maven was unavailable in the editing environment, so these tests must be run with `mvn clean test` in a Maven-enabled environment before the revision is considered build-verified.
