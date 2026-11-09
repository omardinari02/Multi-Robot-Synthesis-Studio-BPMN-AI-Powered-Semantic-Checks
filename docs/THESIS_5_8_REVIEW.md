# Thesis §5.8 — Application to the farming-robot case study

## Coverage added in this increment

The end-to-end integration test now checks the exact six base interactions described in the thesis, not only the total number of resolved messages:

| Message | Producer task | Recipient task | Data type |
|---|---|---|---|
| `weedPosition` | Drone / `Task_Coordinate` | Tagliatore / `Task_Movimento` | `Coordinate` |
| `availability` | Tagliatore / `Task_Autovalutazione` | Drone / `Task_CheckAvailability` | `Boolean` |
| `closestTractor` | Drone / `Task_SelectClosestTractor` | Tagliatore / `Task_Movimento` | `CoordinateTrattoreVicino` |
| `fieldReady` | Tagliatore / `Task_Taglio` | Scavatore / `Task_Scavo` | `Boolean` |
| `digDone` | Scavatore / `Task_Scavo` | Seminatore / `Task_Semina` | `Boolean` |
| `seedingDone` | Seminatore / `Task_Semina` | Broadcast / `Broadcast` | `Boolean` |

The test asserts that the validated match set equals this mapping exactly. This detects a regression where the engine resolves six messages but assigns a wrong producer, consumer, task, or data type.

The existing suite also covers the thesis's extended `1 → N` MapData example: a parallel fan-out to Tagliatore, Scavatore, and Seminatore, plus a sequential fan-out fixture and the bounded guarded exclusive-choice case.

## Verification boundary

This change strengthens the regression assertions for §5.8; it does not add a new test method or change the expected test count. The user's previous `50 tests, BUILD SUCCESS` applies to the archive before this assertion was added. Run `mvn clean test` on this archive to verify the strengthened assertion against the current BPMN and semantic configuration.

These tests establish behavior for the supplied case study and nearby supported variants only. They do not establish support for arbitrary BPMN models.
