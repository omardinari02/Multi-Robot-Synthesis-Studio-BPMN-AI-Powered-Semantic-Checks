# Patch notes — 2026-10-28: BPMN message binding

## Changes

- Generated local `SendTask` and `ReceiveTask` nodes now reference a declared BPMN `Message` by `messageRef`.
- The unified collaboration now declares each exchanged message once and binds the corresponding send task, receive task, and `MessageFlow` to that same message.
- Added regression assertions to the end-to-end pipeline test for local task message references and unified collaboration message references.

## Scope

This fixes the concrete structural defect where the generated unified BPMN contained named `MessageFlow` elements but no BPMN `Message` declarations or message references. It does not, by itself, prove complete equivalence with every detail in thesis figures 5.10–5.14. In particular, the thesis figures include acknowledgement labels and choreography-specific presentation that require separate comparison against the thesis source model.

## Validation

The uploaded ZIP's pre-existing generated artifacts were inspected. This environment does not have Maven available, so `mvn clean test` could not be run after the patch. Run it from the project root before treating the patch as verified.
