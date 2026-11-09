# Implementation notes — fixed7

## Changes in this revision

1. **Availability retry loop**
   - The `NO` branch still waits on the 10-second timer.
   - `Flow_Loop` now returns from `Event_Timer_10s` to `Task_availability`, so the availability request is performed again.
   - The obsolete `Gateway_Join_Availability` and `Flow_4` were removed. The initial path and retry path merge at the availability task, then continue through `Flow_3` to `Gateway_Check_Availability`.

2. **Global interaction ordering during local weaving**
   - Global message-task IDs in the main fixture now contain the corresponding semantic message names (for example, `Task_weedPosition` and `Task_availability`).
   - When the global process establishes that an incoming interaction precedes an outgoing interaction, the weaver can place the receive task before the earlier local producer anchor, but only when it can identify a unique earlier outgoing interaction. Otherwise, it keeps the configured consumer anchor rather than guessing.
   - This addresses the main fixture's Tagliatore order: receive `weedPosition`, evaluate availability, send `availability`, then receive `closestTractor` before movement.

3. **Regression tests**
   - Added assertions for the timer retry path and removed merge gateway.
   - Added end-to-end assertions for the ordering of the generated Tagliatore communication tasks.

## Validation status

- All 10 BPMN files parse as XML.
- The updated `GlobalMission.bpmn` has no missing sequence-flow endpoints or BPMNDI references in the structural check.
- Maven tests were **not run in the editing environment** because Maven and the project's dependency cache are unavailable here. Run `mvn test` from the project root before treating this revision as verified.

## Input convention for ordering inference

For the weaver to associate a global choreography task with a semantic message automatically, the global task ID or name must contain that message name after normalization (letters/digits only, case-insensitive), or the project must provide an explicit mapping. If no unique global order can be established, the implementation deliberately avoids guessing.
