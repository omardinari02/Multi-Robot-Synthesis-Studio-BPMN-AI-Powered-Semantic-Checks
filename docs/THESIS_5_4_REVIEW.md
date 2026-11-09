# Thesis review — §5.4 Syntactic and semantic compatibility

## Scope

This review is aligned to §5.4 of the thesis. It distinguishes exact type equality (strong syntax), directional subtype compatibility (weak syntax), structured-field compatibility, and semantic concept compatibility.

## Changes in this pass

- Added `SyntacticCompatibilityChecker` as a dedicated implementation of strong and weak type checks.
- Added per-message `syntacticCompatibility` in `semantics.json` entries. Supported values are `STRONG` and `WEAK`; omitted values default to `WEAK` for backward compatibility with existing examples.
- Applied the selected mode when validating the declared producer type and matching the consumer's required input type.
- Added optional `producedFields` and `receiverFields` declarations. When supplied, each receiver field declaration (`name:type`) must be present in the producer schema; additional producer fields are allowed. One-sided/malformed schemas are rejected by input validation.
- Kept semantic validation as a separate subsequent stage: explicit semantic concept IDs must agree; absent IDs use the existing conservative description/optional-LLM path. Primitive type equality alone does not establish semantic compatibility.
- Added unit tests for strong equality, directional weak compatibility, structured-field subset checks, missing schemas, malformed field declarations, and invalid modes.

## Evidence from the current project

- `OntologyManager` already provides exact strong type equality and transitive, directional weak subtype checks, with cycle detection in the taxonomy.
- `AISemanticMatcher` checks type compatibility before semantic evidence and rejects mismatched explicit concept identifiers.
- `MultiRobotSynthesisEngine` resolves producer and receiver tasks against BPMN metadata before accepting an interaction.

## Remaining limitations for §5.4

- The existing case-study BPMNs do not provide full structured data schemas. Therefore, structural field checking is active only when both field lists are supplied in a semantic entry; an empty pair means the input does not provide enough information to perform a structural comparison, not that structural equivalence has been proven.
- Semantic concept IDs are compared by normalized exact equality; ontology-based semantic-concept subsumption is part of §5.5 and is not claimed as complete here.
- The optional remote LLM is an external judgement path, not a formal proof. Without credentials, the fallback requires normalized descriptions to match exactly.
- The full Maven/JUnit suite was not run in this environment because Maven is unavailable. The new tests are included for execution with `mvn clean test` on the user's machine.

## Verification performed in this pass

- Source changes and tests were reviewed statically.
- XML/JSON syntax and package compilation still need a full Maven build to verify dependency-backed compilation and the complete test suite.
