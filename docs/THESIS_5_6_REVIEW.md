# Thesis review — §5.6 Behavioral testing

## Thesis requirements

Section 5.6 requires more than ontology matching: test whether the receiver can consume the message, whether inadmissible states arise, whether later-task preconditions/effects remain satisfied, and whether the global choreography stays coherent. The thesis example compares Drone `MapData` candidates for Tagliatore and Scavatore.

## Implemented in this revision

- `BehavioralCompatibilityTester` returns `PASS`, `FAIL`, or `UNRESOLVED`; it checks declared input compatibility, consumer reachability from a start, and all finite downstream continuations to end events. Dead ends fail; cycles and incomplete path enumeration remain unresolved rather than being treated as proofs.
- `BehavioralStateSimulator` validates `key=value` task contracts, checks preconditions against an explicit state, applies effects, rejects contradictory declarations, and fails closed for unknown or malformed state.
- The case-study BPMNs now declare explicit precondition/effect contracts for the mission tasks. These are an explicit, editable formalization of the case-study assumptions; they are not automatically inferred from natural-language task names.
- `behavioral-state.json` supplies the explicit initial fact `mission_started=true`. The engine loads and validates this file instead of always passing an empty state. Missing/malformed configuration is an error rather than a silent fallback.
- The engine enumerates finite start-to-producer prefixes and consumer-to-end continuations, then simulates each producer-prefix/receiver-continuation combination. Already validated earlier message producer effects seed the explicit state for later interactions in choreography order. This lets producer-side preconditions (for example, availability before tractor selection) and downstream receiver preconditions be checked along the represented case-study paths.
- `GlobalChoreographyAnalyzer.validateStructuralCoherence` checks sequence-flow endpoints, reachability from a start, ability of every flow node to reach an end, and message-flow source/target/message references. For the supported two-way Boolean exclusive-gateway form, it checks complementary true/false guards and requires the guard variable to match a declared message name; unknown guard sources are blocking diagnostics. Strongly connected cyclic regions are accepted only when they contain a timer catch event and a complementary, message-sourced Boolean gateway, which covers the case-study availability retry loop while rejecting uncharacterized cycles.
- Case-study integration tests assert explicit contracts are extracted and that the base case has no unresolved behavioral notes. The 1-to-N fixture without explicit contracts correctly retains `UNRESOLVED` notes rather than claiming a behavioral proof.

## Scope and limitations (do not overclaim)

- The state simulator is a finite, explicit-literal simulator, not a full BPMN model checker. It does not prove temporal properties or loop liveness. The structural global check establishes reachability properties, not full semantic equivalence of every global and local execution trace.
- The case-study contracts are machine-readable assumptions based on the described mission steps and are editable in BPMN Camunda extension parameters. For a new robot domain, the contracts and initial-state file must be supplied/validated for that domain; the system must not infer arbitrary facts from names.
- The current pipeline simulates each producer's local prefix and the receiver's downstream paths, carrying forward validated producer effects in global message order. It does not construct a complete interleaving/state-space across every participant for arbitrary choreography with multiple joins, exclusive alternatives, or loops. The global cycle check validates only the supported timer-plus-complementary-guard retry pattern; it does not prove retry termination or full general-purpose formal verification.

## Verification

- The user reported `mvn clean test`: 43 tests passed on the previous archive. This revision adds three focused tests (initial-state satisfaction, global structural coherence, and producer-prefix enumeration) and updates the base case to expect no behavioral proof-gap notes, so the expected suite count is now 46. This revised archive has not been run with Maven in this environment because Maven and project dependency jars are unavailable here. Run `mvn clean test` against this exact archive before accepting it.
- The user subsequently reported `mvn clean test`: 46 tests passed on the regression-fix archive, then reported `mvn clean test`: 48 tests passed with no failures/errors on `mrs-synthesis-engine-5.6-global-coherence-pass-2026-10-27.zip`. This verifies that exact 48-test revision, not later edits.
- This follow-up adds one regression test that checks structural coherence for the related parallel and sequential 1-to-N fixtures. The expected count is now 49; rerun `mvn clean test` on the newly packaged archive before accepting this follow-up. Maven is unavailable in the current editing environment, so this new test cannot be claimed as executed here.

## Thesis-source alignment

The thesis §5.6 (printed pages 51–52; PDF pages 59–60) names four checks: receiver consumption, inadmissible states, downstream preconditions/effects, and global choreography coherence. The implementation now addresses each with explicit contracts and a structural global check, while retaining the limitations above for arbitrary BPMN state-space and loop verification.

### Regression fixes after the 46-test run

- Corrected the local-analyzer contract extraction test: `Task_Taglio` is declared in `Tagliatore.bpmn`, so the test now analyzes that model rather than incorrectly searching the Drone model for a cutter task.
- Updated the missing-consumer regression expectation. If the declared `fieldReady` consumer is deliberately changed to a nonexistent task, the engine must not fall back to another task; the resulting missing `field_ready=true` state fact also means dependent digging/seeding interactions cannot be treated as behaviorally proven. The expected validated-match count is therefore 3 rather than 5 for this negative scenario.
- These are test corrections aligned with the current explicit-contract semantics; the full Maven suite must be rerun on this archive to confirm the resulting count and behavior.
