package it.univaq.disim.mrs.synthesis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MainInputDiscoveryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversGlobalAndLocalModelsByBpmnStructureNotFilenames() throws Exception {
        Path source = Path.of("src/main/resources/bpmn");
        Files.copy(source.resolve("GlobalMission.bpmn"), temporaryDirectory.resolve("mission-map.bpmn"));
        Files.copy(source.resolve("Drone.bpmn"), temporaryDirectory.resolve("unit-a.bpmn"));
        Files.copy(source.resolve("Tagliatore.bpmn"), temporaryDirectory.resolve("unit-b.bpmn"));
        Files.copy(source.resolve("Scavatore.bpmn"), temporaryDirectory.resolve("unit-c.bpmn"));
        Files.copy(source.resolve("Seminatore.bpmn"), temporaryDirectory.resolve("unit-d.bpmn"));

        File global = Main.discoverGlobalModel(temporaryDirectory.toFile());
        List<File> locals = Main.discoverLocalModels(temporaryDirectory.toFile(), global);

        assertEquals("mission-map.bpmn", global.getName());
        assertEquals(4, locals.size());
        assertTrue(locals.stream().allMatch(Main::isExecutableLocalProcess));
        assertFalse(locals.stream().anyMatch(Main::isGlobalChoreography));
    }
}
