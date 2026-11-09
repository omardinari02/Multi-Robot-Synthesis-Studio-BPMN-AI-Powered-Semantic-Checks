# Hardcoding audit — structural BPMN discovery

## Changes in this pass
- Replaced filename keyword filtering (`global`, `enhanced`, `unified`) with BPMN structural classification in the CLI entry point.
- If no global model path is supplied, the CLI searches the input directory for exactly one BPMN model that has a collaboration, message flows, and a non-executable process. Ambiguous discovery fails clearly and asks for an explicit path rather than guessing.
- Local candidates are selected as executable BPMN processes and the structurally identified global model is excluded by canonical path.
- Added a regression test that copies the case-study models to a temporary directory under unrelated filenames and checks that discovery still finds the global model and four local processes.

## Deliberately retained
- BPMN, taxonomy, semantics, and behavioral-state files remain explicit input/configuration. They describe the mission domain and contracts; removing them would require a different semantic-inference approach and could make the system guess unsupported facts.
- Case-study names remain in input models and regression fixtures, not as special-case decision rules in the new discovery logic.
- Default paths are convenience defaults. The CLI still permits explicit paths for the global BPMN, local model directory, taxonomy, and semantic specification.

## Scope and limitations
- This improves filename independence; it does not make the engine support every BPMN construct or infer every domain fact automatically.
- The global-model structural signature is intentionally narrow for the supported collaboration/choreography use case. If zero or multiple candidates match, the user must provide the global BPMN path explicitly.
- Generated output should remain outside the input directory to avoid treating generated executable BPMNs as new input local processes.
- Maven is not installed in the editing environment, so the new Java test has not been executed here. Run `mvn clean test` on this archive before treating it as validated.
