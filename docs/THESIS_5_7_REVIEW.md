# Thesis §5.7 — Recipient disambiguation and activation mode

## Implemented in this increment

- The interaction planner distinguishes a strictly ordered recipient sequence, a proven parallel fan-out, an explicitly guarded exclusive choice, and an unresolved case.
- Exclusive fan-out is generated only for the supported bounded pattern: exactly two recipient choreography tasks, exactly two outgoing branches from their common exclusive gateway, each task is reachable through a different branch, both branches have complementary Boolean guards (`variable == true` / `variable == false`), and the guard variable matches a declared BPMN message name.
- The branch guard is copied to the generated local exclusive-gateway flow leading to the corresponding send task. The original successor is the implicit merge point, so exactly one guarded send branch is taken.
- If a branch is missing, duplicated, ambiguous, lacks an executable guard, uses an unsupported guard syntax, or is not represented by a recipient, the planner leaves the mode unresolved instead of inventing recipient selection.
- Added an integration regression test for condition preservation through local BPMN weaving.

## Scope and limitations

- This is a conservative supported-use-case implementation, not a general BPMN condition evaluator or full participant-projection algorithm.
- Only a two-way complementary Boolean choice is supported by this path. More than two alternatives, default flows, complex expressions, nested choices, and choices with a branch that does not map to a recipient remain unresolved.
- Declaring a BPMN message with the guard variable's name is only a source-consistency check. Runtime availability and variable mapping must still be provided by the execution environment.
- Semantic and syntactic compatibility filtering still happens in the message-resolution stage before the planner. The planner uses validated matches and global task order/gateway structure; it does not infer missing semantic concepts from task names.

## Verification status

- A focused integration test was added, so the expected Maven suite count is the previous 49 plus this test (50 total).
- The editing environment does not have Maven available. Run `mvn clean test` on the exact archive before treating this increment as build-verified.


Regression-fixture note: the exclusive fan-out integration test explicitly declares BPMN `incoming`/`outgoing` references on its local flow nodes, so the fixture provides the same flow-node connectivity metadata expected by the weaver.
