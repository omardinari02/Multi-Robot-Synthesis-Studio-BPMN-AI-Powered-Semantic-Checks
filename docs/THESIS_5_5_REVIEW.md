# Thesis review — §5.5 Ontologies and subsumption

## Thesis requirement

Section 5.5 describes an ontology of information concepts and hierarchical relationships. Subsumption is directional: a produced concept `A` can satisfy a required concept `B` when `A ⊑ B`; the reverse direction is not implied. The ontology should support both data-type compatibility and semantic-concept reasoning without confusing the two hierarchies.

## Changes in this pass

- Extended `OntologyManager` to support a structured ontology file with two independent hierarchies: `dataTypes` and `semanticConcepts`.
- Preserved backward compatibility with the former flat JSON taxonomy, interpreted as a data-type hierarchy.
- Added transitive, directional semantic-concept subsumption via `isSemanticConceptCompatible(produced, required)`.
- Added cycle detection independently for both hierarchies.
- Updated `AISemanticMatcher`: when concept IDs are supplied, the producer concept must be identical to or a subtype of the receiver's required concept. Sibling concepts are not considered compatible merely because they share a parent.
- Updated `taxonomy.json` with a small explicit example hierarchy: `work_area_location` and `selected_tractor_location` are each sub-concepts of `physical_location`. These siblings are not compatible with each other.
- Added tests for directionality, separation of type and semantic hierarchies, and cycle rejection.

## Current ontology format

```json
{
  "dataTypes": {
    "CoordinateZona": ["Coordinate"],
    "CoordinateTrattoreVicino": ["Coordinate"]
  },
  "semanticConcepts": {
    "work_area_location": ["physical_location"],
    "selected_tractor_location": ["physical_location"]
  }
}
```

Each child maps to its direct parent concepts. Transitive relationships are evaluated at runtime. Legacy flat maps remain accepted as data-type taxonomies.

## Limitations

- The semantic hierarchy currently contains only the explicit, conservative location relationships shown above. It must be extended with domain-reviewed relationships for any new semantic concepts; the system does not invent ontology edges.
- Subsumption is a lightweight JSON taxonomy model, not a full OWL reasoner. It does not represent disjointness, equivalence axioms, property restrictions, or logical consistency beyond cycle detection.
- A semantic subtype relationship only supports the declared direction; it does not automatically prove that every task precondition/effect or operational role is compatible. That is addressed separately by behavioral testing (§5.6).
- Maven/JUnit tests have not been executed in this environment because Maven and dependency access are unavailable. The tests are included for a full local run.

## Verification performed

- The new hierarchy is represented separately from data-type hierarchy in `taxonomy.json`.
- The implementation preserves legacy flat taxonomy parsing by design.
- Source syntax, XML/JSON parsing, and archive integrity checks are to be rerun after this pass; the full Maven suite remains unverified here.
