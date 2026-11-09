package it.univaq.disim.mrs.synthesis;

import it.univaq.disim.mrs.synthesis.core.MultiRobotSynthesisEngine;
import it.univaq.disim.mrs.synthesis.model.MissionContext;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Collaboration;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** CLI entry point. Optional arguments:
 * 0 = global BPMN (if omitted, discovered structurally in the local BPMN directory),
 * 1 = directory containing local BPMNs, 2 = taxonomy JSON,
 * 3 = semantic specifications JSON, 4 = --strict.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (Arrays.stream(args).anyMatch(arg -> "--web".equalsIgnoreCase(arg) || "--gui".equalsIgnoreCase(arg) || "--server".equalsIgnoreCase(arg))) {
            it.univaq.disim.mrs.synthesis.web.SynthesisWebServer.main(args);
            return;
        }

        File localDirectory = new File(args.length > 1 ? args[1] : "src/main/resources/bpmn");
        File ontology = new File(args.length > 2 ? args[2] : "src/main/resources/taxonomy.json");
        File semantics = new File(args.length > 3 ? args[3] : "src/main/resources/semantics.json");
        boolean strict = Arrays.stream(args).anyMatch(arg -> "--strict".equalsIgnoreCase(arg));

        if (!localDirectory.isDirectory()) {
            throw new IllegalArgumentException("Local BPMN directory does not exist: " + localDirectory.getAbsolutePath());
        }
        File global = args.length > 0 ? new File(args[0]) : discoverGlobalModel(localDirectory);
        if (global == null || !global.isFile()) {
            throw new IllegalArgumentException("Could not identify a global choreography BPMN in "
                    + localDirectory.getAbsolutePath() + ". Pass its path as the first argument.");
        }

        List<File> localProcesses = discoverLocalModels(localDirectory, global);
        if (localProcesses.isEmpty()) {
            throw new IllegalArgumentException("No executable local BPMN processes found in " + localDirectory);
        }

        logger.info("Starting synthesis with {} local process(es), global model {}, strict={}",
                localProcesses.size(), global.getAbsolutePath(), strict);
        MissionContext context = new MultiRobotSynthesisEngine().runSynthesisPipeline(
                localProcesses, global, ontology, semantics, strict);
        logger.info("Synthesis result: {} validated interactions and {} diagnostics.",
                context.getValidatedMatches().size(), context.getDiagnostics().size());
    }

    static File discoverGlobalModel(File directory) {
        List<File> candidates = bpmnFiles(directory).filter(Main::isGlobalChoreography).collect(Collectors.toList());
        if (candidates.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one global choreography BPMN by structure in "
                    + directory.getAbsolutePath() + ", found " + candidates.size()
                    + ". Pass the global BPMN path explicitly if multiple candidates exist.");
        }
        return candidates.get(0);
    }

    static List<File> discoverLocalModels(File directory, File globalModel) {
        String globalPath = canonicalPath(globalModel);
        return bpmnFiles(directory)
                .filter(file -> !canonicalPath(file).equals(globalPath))
                .filter(Main::isExecutableLocalProcess)
                .sorted(Comparator.comparing(File::getName))
                .collect(Collectors.toList());
    }

    private static Stream<File> bpmnFiles(File directory) {
        File[] files = directory.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".bpmn"));
        return files == null ? Stream.empty() : Arrays.stream(files);
    }

    static boolean isGlobalChoreography(File file) {
        try {
            BpmnModelInstance model = Bpmn.readModelFromFile(file);
            boolean hasCollaboration = !model.getModelElementsByType(Collaboration.class).isEmpty();
            boolean hasMessageFlows = !model.getModelElementsByType(MessageFlow.class).isEmpty();
            boolean hasNonExecutableProcess = model.getModelElementsByType(Process.class).stream()
                    .anyMatch(process -> !Boolean.TRUE.equals(process.isExecutable()));
            return hasCollaboration && hasMessageFlows && hasNonExecutableProcess;
        } catch (RuntimeException exception) {
            logger.debug("Skipping BPMN that cannot be classified as a global choreography: {} ({})",
                    file, exception.getMessage());
            return false;
        }
    }

    static boolean isExecutableLocalProcess(File file) {
        try {
            BpmnModelInstance model = Bpmn.readModelFromFile(file);
            if (isGlobalChoreography(file)) return false;
            return model.getModelElementsByType(Process.class).stream()
                    .anyMatch(process -> Boolean.TRUE.equals(process.isExecutable()));
        } catch (RuntimeException exception) {
            logger.warn("Skipping invalid BPMN input {}: {}", file, exception.getMessage());
            return false;
        }
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException exception) {
            return file.getAbsolutePath();
        }
    }
}
