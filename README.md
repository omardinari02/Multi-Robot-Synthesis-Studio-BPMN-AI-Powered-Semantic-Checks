# Multi-Robot Coordination Synthesis Engine

A Java 11 / Maven prototype for synthesizing communication tasks between local robot BPMN processes from a global collaboration model, guided by the method described in the thesis *Sintesi di comportamenti collaborativi per sistemi multi-robot*.

## What the pipeline does

1. Loads the global BPMN model and each local robot BPMN process.
2. Extracts declared `requiredData` and `producedData` from Camunda extension metadata.
3. Loads and validates `semantics.json` and the directional subtype taxonomy in `taxonomy.json`.
4. Resolves each global `MessageFlow` against its declared producer task, consumer task, data type, and semantic concept.
5. Checks consumer input compatibility and structural continuation behavior.
6. Simulates explicit `key=value` preconditions/effects along receiver continuations using `behavioral-state.json` as the declared initial state.
7. Checks global BPMN reachability, end-event reachability, sequence-flow references, exclusive-gateway conditions, and message-flow endpoints.
8. Groups interactions by their original producer task and infers a fan-out mode for multi-recipient plans.
9. Inserts generated `SendTask` / `ReceiveTask` nodes into local BPMN models without overwriting original task IDs.
10. Writes enhanced BPMN models, a unified collaboration BPMN, and Draw.io visualizations under `target/output/`.

Unresolved interactions are logged and retained as diagnostics; the engine does not silently choose an arbitrary producer or consumer.

## Requirements

- JDK 11 or newer
- Maven 3.8+

## Run

From the repository root:

```bash
mvn clean test
mvn exec:java -Dexec.mainClass=it.univaq.disim.mrs.synthesis.Main
```

The CLI accepts optional arguments:

```text
Main [global-bpmn] [local-bpmn-directory] [taxonomy-json] [semantics-json] [--strict]
```

Defaults point to `src/main/resources/bpmn/`, `GlobalMission.bpmn`, `taxonomy.json`, and `semantics.json`.

Use `--strict` to stop when any global message cannot be resolved. In normal mode, the engine may generate a partial result but logs every unresolved message.

## Semantic specification format

Each message entry in `semantics.json` declares:

- `robotId`, `taskId`: the original producer robot and task.
- `messageName`, `dataType`, `semanticDescription`: the message and its meaning.
- `semanticConcept`: a stable concept identifier for deterministic semantic matching.
- `receiverTaskId`, `receiverDataType`, `receiverSemanticDescription`, `receiverSemanticConcept`: the intended consumer metadata. These fields can be `null` for a broadcast recipient.
- `syntacticCompatibility`: optional `STRONG` (exact type equality) or `WEAK` (produced type may be a declared subtype of the required type); defaults to `WEAK` for existing inputs.
- `producedFields` and `receiverFields`: optional lists of `name:type` field declarations for structured data. If supplied, both lists are required and every receiver field declaration must occur in the producer list. This is a subset check: additional producer fields are allowed.

The ontology file supports a structured format with separate `dataTypes` and `semanticConcepts` hierarchies. Each key is a subtype/concept and its array contains direct supertypes. For example, `CoordinateTrattoreVicino -> Coordinate` means a value of type `CoordinateTrattoreVicino` may satisfy a requirement for `Coordinate`, but not the reverse. A semantic relation such as `work_area_location -> physical_location` allows a specific location concept to satisfy a general location requirement, but does not make sibling concepts interchangeable. Legacy flat data-type taxonomy files remain supported.

## Thesis §5.4 compatibility checks

The engine applies the selected strong/weak syntactic mode before semantic validation. Structured-field compatibility is checked only when both field lists are provided; without those schemas, the engine does not claim that structural equivalence was proven. Explicit semantic concept identifiers must match after normalization. If identifiers are absent, the optional LLM path may be used when configured; the local fallback accepts only identical normalized descriptions.

See `docs/THESIS_5_4_REVIEW.md` for the implementation scope and limitations. The ontology/subsumption implementation for thesis §5.5 is documented in `docs/THESIS_5_5_REVIEW.md`.

## Important limitations

- The supplied case-study BPMNs now declare explicit state contracts and an initial state. Other domains must provide and review their own contracts; the engine does not infer domain facts from task names.
- Multi-recipient planning and BPMN insertion are implemented for the represented flow structures; complex gateways, loops, or consumers with multiple incoming sequence flows may be rejected rather than rewritten unsafely.
- A dedicated `src/test/resources/one-to-n/` fixture and integration test exercise MapData fan-out to three robots. This is a focused 1-to-N case, not proof of complete behavioral equivalence with every thesis scenario.
- Semantic concept identifiers are explicit input metadata. They prevent accidental matches but do not remove the need to curate and validate those specifications.
- Maven execution must be verified in an environment with dependency access. Generated files in `target/` are not evidence of a successful current build unless produced by a fresh run.

## Behavioral contracts and initial state (§5.6)

Local BPMN task extension parameters declare state contracts:

- `camunda:inputParameter name="precondition"` or `preconditions`: `key=value` facts required before a task.
- `camunda:outputParameter name="effect"` or `effects`: `key=value` facts established by a task.
- Multiple literals can be separated with semicolons, e.g. `field_ready=true;map_available=true`.

The case-study contracts are declared directly in `src/main/resources/bpmn/*.bpmn`. `src/main/resources/behavioral-state.json` declares the initial state, currently `mission_started=true`. The engine loads this configuration instead of assuming an empty state. A custom initial-state file can be supplied through the `runSynthesisPipeline(..., behavioralStateFile, failOnUnresolved)` overload.

The simulator enumerates finite producer prefixes and receiver continuations, carrying forward prior validated producer effects in choreography order, and returns `PASS`, `FAIL`, or `UNRESOLVED`; missing facts are never guessed. The global choreography checker validates sequence-flow endpoints, start reachability, reachability to an end event, exclusive-gateway conditions, and message-flow references. The one-to-N test fixture intentionally omits state contracts and should retain `UNRESOLVED` notes rather than being presented as behaviorally proven.

**Scope boundary:** this is explicit-contract behavioral simulation plus structural global-coherence validation for the represented case study. It is not a general-purpose formal BPMN model checker: arbitrary participant interleavings, full state-space equivalence, and loop liveness are not proven. `mvn clean test` must be run on the exact archive in an environment with Maven and dependency access before treating the revision as build-verified.
