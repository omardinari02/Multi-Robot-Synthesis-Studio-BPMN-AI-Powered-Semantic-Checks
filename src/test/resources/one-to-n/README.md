# Extended 1-to-N test fixture

This fixture is intentionally separate from `src/main/resources/bpmn/` so the base thesis case study is not overwritten. It models `Drone.Task_Coordinate` producing `MapData`, then broadcasts that same information to `Tagliatore`, `Scavatore`, and `Seminatore` through a common parallel gateway. `semantics_1_to_N.json` supplies the explicit message-to-consumer mappings used by the integration test.
