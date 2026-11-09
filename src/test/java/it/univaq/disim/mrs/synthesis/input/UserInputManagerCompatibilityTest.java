package it.univaq.disim.mrs.synthesis.input;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class UserInputManagerCompatibilityTest {
    private final UserInputManager manager = new UserInputManager();

    @Test
    void rejectsUnknownSyntacticCompatibilityMode() throws Exception {
        File file = File.createTempFile("semantics-mode", ".json");
        Files.writeString(file.toPath(), "[{\"robotId\":\"R1\",\"taskId\":\"T1\",\"messageName\":\"m\",\"dataType\":\"Coordinate\",\"semanticDescription\":\"position\",\"syntacticCompatibility\":\"APPROXIMATE\"}]");
        try {
            assertThrows(IllegalArgumentException.class, () -> manager.loadSemanticSpecifications(file));
        } finally {
            assertTrue(file.delete());
        }
    }

    @Test
    void rejectsOneSidedStructuredSchema() throws Exception {
        File file = File.createTempFile("semantics-fields", ".json");
        Files.writeString(file.toPath(), "[{\"robotId\":\"R1\",\"taskId\":\"T1\",\"messageName\":\"m\",\"dataType\":\"Coordinate\",\"semanticDescription\":\"position\",\"producedFields\":[\"x:Double\"]}]");
        try {
            assertThrows(IllegalArgumentException.class, () -> manager.loadSemanticSpecifications(file));
        } finally {
            assertTrue(file.delete());
        }
    }
    @Test
    void rejectsMalformedStructuredFieldDeclaration() throws Exception {
        File file = File.createTempFile("semantics-field-format", ".json");
        Files.writeString(file.toPath(), "[{\"robotId\":\"R1\",\"taskId\":\"T1\",\"messageName\":\"m\",\"dataType\":\"Coordinate\",\"semanticDescription\":\"position\",\"producedFields\":[\"latitude\"],\"receiverFields\":[\"latitude:Double\"]}]");
        try {
            assertThrows(IllegalArgumentException.class, () -> manager.loadSemanticSpecifications(file));
        } finally {
            assertTrue(file.delete());
        }
    }

}
