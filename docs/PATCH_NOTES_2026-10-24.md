# Patch notes — 24 October 2026

## Implemented

- `LocalProcessAnalyzer` now extracts task metadata from the BPMN `Task` type instead of only `ServiceTask`. The existing extraction of `producedData` and `requiredData` from direct extension elements and nested `camunda:inputOutput` is retained.
- `BehavioralCompatibilityTester` now checks reachability from any start event and to any end event, avoiding false rejection when a model has multiple start/end events.
- Added two regression tests: a `UserTask` with Camunda input/output metadata, and a multi-start process where the valid path is not assumed to be the first start event.
- Updated implementation/remediation docs to distinguish completed fixes from remaining thesis gaps.

## Not claimed as solved

- Full behavioral simulation of preconditions/effects from thesis §5.6 is not possible from the current input metadata alone. It needs explicit precondition/effect declarations and a state-transition/simulation model.
- Exclusive multi-recipient fan-out still requires actual executable branch conditions; the engine must not invent them.
- Semantic concept IDs still use conservative normalized exact equality. A separate semantic-concept hierarchy would be needed to support concept subsumption safely.
- More tests are still needed for complex loops, joins, and non-linear processes.

## Verification

- Static XML/JSON checks are performed on the packaged source tree.
- A fresh Maven test run could not be performed in this environment because `mvn` is not installed. The new regression tests must be run with `mvn clean test` in an environment with Maven and dependency access. Existing `target/` outputs/reports are excluded from the patch ZIP so stale reports are not mistaken for results of this patch.
