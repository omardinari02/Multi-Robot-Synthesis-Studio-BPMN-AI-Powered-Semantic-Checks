# BPMN layout correction — 2026-10-29

## Changes

- Repositioned the projected local availability-collection and decision gateways relative to the actual availability-check task, so they appear after that task in left-to-right BPMNDI layout.
- Shifted the success-path subtree far enough right to reserve space for the two gateways and the timer retry branch.
- Expanded each unified robot pool to contain its cloned BPMN shapes, instead of keeping a fixed 1800px width that could leave downstream nodes outside the pool.
- Kept the existing retry duration and retry target unchanged; the timer still loops back to the generated availability ReceiveTask.

## Verification status

- Source edits and whitespace/static inspection performed.
- Maven tests were **not run in this environment** because the Maven executable/wrapper is unavailable. Run `mvn clean test` locally before treating the change as verified.
