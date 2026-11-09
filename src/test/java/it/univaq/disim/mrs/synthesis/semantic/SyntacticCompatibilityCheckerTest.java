package it.univaq.disim.mrs.synthesis.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyntacticCompatibilityCheckerTest {
    private SyntacticCompatibilityChecker checker;

    @BeforeEach
    void setUp() {
        OntologyManager ontology = new OntologyManager();
        ontology.loadOntology(new File("src/main/resources/taxonomy.json"));
        checker = new SyntacticCompatibilityChecker(ontology);
    }

    @Test
    void strongModeRequiresExactType() {
        assertTrue(checker.isTypeCompatible("Coordinate", "Coordinate", "STRONG"));
        assertFalse(checker.isTypeCompatible("CoordinateTrattoreVicino", "Coordinate", "STRONG"));
    }

    @Test
    void weakModeAcceptsOnlyProducedSubtypeInCorrectDirection() {
        assertTrue(checker.isTypeCompatible("CoordinateTrattoreVicino", "Coordinate", "WEAK"));
        assertFalse(checker.isTypeCompatible("Coordinate", "CoordinateTrattoreVicino", "WEAK"));
    }

    @Test
    void structuredProducerMayContainAdditionalFieldsButMustSupplyRequiredDeclarations() {
        assertTrue(checker.isStructurallyCompatible(
                List.of("latitude:Double", "longitude:Double", "tractorId:String"),
                List.of("latitude: double", "longitude:Double")));
        assertTrue(checker.isStructurallyCompatible(
                List.of("latitude.Double", "longitude.Double", "tractorId.String"),
                List.of("latitude:double", "longitude.Double")));
        assertFalse(checker.isStructurallyCompatible(
                List.of("latitude:Double", "longitude:Double"),
                List.of("latitude:Double", "tractorId:String")));
        assertFalse(checker.isStructurallyCompatible(
                List.of("latitude:Double"), List.of("latitude:String")));
    }

    @Test
    void structuralCompatibilityDerivedDirectlyFromTaxonomy() {
        // CoordinateTrattoreVicino has (latitude.Double, longitude.Double, tractorId.String)
        // CoordinateZona has (latitude.Double, longitude.Double)
        // Sender: CoordinateTrattoreVicino, Receiver: CoordinateZona -> PASS (all required fields present)
        assertTrue(checker.isStructurallyCompatibleTypes("CoordinateTrattoreVicino", "CoordinateZona"));

        // Reverse: Sender CoordinateZona cannot satisfy CoordinateTrattoreVicino (missing tractorId)
        assertFalse(checker.isStructurallyCompatibleTypes("CoordinateZona", "CoordinateTrattoreVicino"));
    }

    @Test
    void ontologyExtractsDeclaredStructuralFields() {
        OntologyManager ontology = new OntologyManager();
        ontology.loadOntology(new File("src/main/resources/taxonomy.json"));

        java.util.Set<String> tractorFields = ontology.getFieldsForType("CoordinateTrattoreVicino");
        java.util.Set<String> zoneFields = ontology.getFieldsForType("CoordinateZona");

        assertEquals(3, tractorFields.size());
        assertTrue(tractorFields.contains("tractorid.string"));
        assertTrue(tractorFields.contains("latitude.double"));
        assertTrue(tractorFields.contains("longitude.double"));

        assertEquals(2, zoneFields.size());
        assertFalse(zoneFields.contains("tractorid.string"));
        assertTrue(zoneFields.contains("latitude.double"));
        assertTrue(zoneFields.contains("longitude.double"));
    }

    @Test
    void missingOneOfTheSchemasDoesNotPassStructuralCheck() {
        assertFalse(checker.isStructurallyCompatible(List.of("x:Double"), List.of()));
        assertFalse(checker.isStructurallyCompatible(null, List.of("x:Double")));
        assertTrue(checker.isStructurallyCompatible(List.of(), List.of()));
    }

    @Test
    void invalidModeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> checker.isTypeCompatible("Coordinate", "Coordinate", "APPROXIMATE"));
    }
}
