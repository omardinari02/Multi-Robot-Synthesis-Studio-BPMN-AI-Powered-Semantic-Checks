package it.univaq.disim.mrs.synthesis.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class OntologyManagerTest {
    private OntologyManager ontologyManager;

    @BeforeEach
    void setUp() {
        ontologyManager = new OntologyManager();
        ontologyManager.loadOntology(new File("src/main/resources/taxonomy.json"));
    }

    @Test
    void exactTypesAreCompatible() {
        assertTrue(ontologyManager.isCompatible("Coordinate", "Coordinate"));
        assertTrue(ontologyManager.isCompatible("Boolean", "Boolean"));
    }

    @Test
    void strongAndWeakCompatibilityAreDistinct() {
        assertTrue(ontologyManager.isStronglyCompatible("Coordinate", "Coordinate"));
        assertFalse(ontologyManager.isStronglyCompatible("CoordinateTrattoreVicino", "Coordinate"));
        assertTrue(ontologyManager.isWeaklyCompatible("CoordinateTrattoreVicino", "Coordinate"));
        assertFalse(ontologyManager.isWeaklyCompatible("Coordinate", "CoordinateTrattoreVicino"));
    }

    @Test
    void subsumptionIsDirectional() {
        assertTrue(ontologyManager.isCompatible("CoordinateTrattoreVicino", "Coordinate"));
        assertFalse(ontologyManager.isCompatible("Coordinate", "CoordinateTrattoreVicino"));
    }

    @Test
    void subsumptionIsTransitive() throws Exception {
        File ontology = File.createTempFile("taxonomy", ".json");
        Files.writeString(ontology.toPath(), "{\"coordinatezonadettagliata\":[\"coordinatezona\"],\"coordinatezona\":[\"coordinate\"]}");
        OntologyManager manager = new OntologyManager();
        manager.loadOntology(ontology);
        assertTrue(manager.isCompatible("coordinatezonadettagliata", "coordinate"));
        assertFalse(manager.isCompatible("coordinate", "coordinatezonadettagliata"));
        assertTrue(ontology.delete());
    }

    @Test
    void unrelatedTypesAreNotCompatible() {
        assertFalse(ontologyManager.isCompatible("Coordinate", "MapData"));
        assertFalse(ontologyManager.isCompatible("Boolean", "Coordinate"));
        assertFalse(ontologyManager.isCompatible(null, "Coordinate"));
    }

    @Test
    void semanticConceptSubsumptionIsDirectionalAndSeparateFromDataTypes() throws Exception {
        File ontology = File.createTempFile("structured-ontology", ".json");
        Files.writeString(ontology.toPath(), "{\"dataTypes\":{\"DetailedCoordinate\":[\"Coordinate\"]},\"semanticConcepts\":{\"work_area_location\":[\"physical_location\"],\"selected_tractor_location\":[\"physical_location\"]}}");
        OntologyManager manager = new OntologyManager();
        manager.loadOntology(ontology);
        assertTrue(manager.isWeaklyCompatible("DetailedCoordinate", "Coordinate"));
        assertFalse(manager.isWeaklyCompatible("work_area_location", "physical_location"));
        assertTrue(manager.isSemanticConceptCompatible("work_area_location", "physical_location"));
        assertTrue(manager.isSemanticConceptCompatible("selected_tractor_location", "physical_location"));
        assertFalse(manager.isSemanticConceptCompatible("physical_location", "work_area_location"));
        assertFalse(manager.isSemanticConceptCompatible("work_area_location", "selected_tractor_location"));
        assertTrue(ontology.delete());
    }

    @Test
    void semanticConceptCyclesAreRejected() throws Exception {
        File ontology = File.createTempFile("semantic-cycle", ".json");
        Files.writeString(ontology.toPath(), "{\"dataTypes\":{},\"semanticConcepts\":{\"a\":[\"b\"],\"b\":[\"a\"]}}");
        assertThrows(IllegalArgumentException.class, () -> new OntologyManager().loadOntology(ontology));
        assertTrue(ontology.delete());
    }

    @Test
    void emptyParentReferenceIsRejected() throws Exception {
        File ontology = File.createTempFile("ontology-empty-parent", ".json");
        Files.writeString(ontology.toPath(), "{\"dataTypes\":{\"Coordinate\":[\" \" ]},\"semanticConcepts\":{}}");
        assertThrows(IllegalArgumentException.class, () -> new OntologyManager().loadOntology(ontology));
        assertTrue(ontology.delete());
    }

    @Test
    void cyclicTaxonomyIsRejected() throws Exception {
        File ontology = File.createTempFile("taxonomy-cycle", ".json");
        Files.writeString(ontology.toPath(), "{\"a\":[\"b\"],\"b\":[\"a\"]}");
        assertThrows(IllegalArgumentException.class, () -> new OntologyManager().loadOntology(ontology));
        assertTrue(ontology.delete());
    }
}
