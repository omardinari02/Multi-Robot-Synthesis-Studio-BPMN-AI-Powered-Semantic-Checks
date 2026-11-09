package it.univaq.disim.mrs.synthesis.persistence;

import it.univaq.disim.mrs.synthesis.generator.UnifiedDiagramBuilder;
import it.univaq.disim.mrs.synthesis.model.MissionContext;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModelRepository {
    private static final Logger logger = LoggerFactory.getLogger(ModelRepository.class);
    private static final Path OUTPUT_DIR = Path.of("target", "output");
    private final UnifiedDiagramBuilder unifiedBuilder = new UnifiedDiagramBuilder();

    public BpmnModelInstance loadModel(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("BPMN file not found: " + (file == null ? "null" : file.getAbsolutePath()));
        }
        try {
            return Bpmn.readModelFromFile(file);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot parse BPMN file " + file.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    public void saveModel(BpmnModelInstance model, String fileName) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Path output = OUTPUT_DIR.resolve(fileName);
            Bpmn.writeModelToFile(output.toFile(), model);
            // Parse the written artifact again so serialization errors cannot be silently ignored.
            Bpmn.readModelFromFile(output.toFile());
            logger.info("Validated output artifact: {}", output);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write or re-parse generated BPMN '" + fileName + "'.", e);
        }
    }

    public void saveUnifiedDiagram(MissionContext context) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            BpmnModelInstance unified = unifiedBuilder.build(context);
            Path output = OUTPUT_DIR.resolve("UnifiedMission.bpmn");
            Bpmn.writeModelToFile(output.toFile(), unified);
            Bpmn.readModelFromFile(output.toFile());
            logger.info("Validated unified collaboration artifact: {}", output);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate or validate UnifiedMission.bpmn.", e);
        }
    }
}
