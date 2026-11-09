package it.univaq.disim.mrs.synthesis.analyzer;

import it.univaq.disim.mrs.synthesis.model.RobotTaskMetadata;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaInputOutput;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaInputParameter;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaOutputParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Extracts input/output declarations from all BPMN Task variants, not only ServiceTask. */
public class LocalProcessAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(LocalProcessAnalyzer.class);

    public List<RobotTaskMetadata> analyzeLocalModels(String robotId, BpmnModelInstance model) {
        List<RobotTaskMetadata> metadataList = new ArrayList<>();
        Collection<Task> tasks = model.getModelElementsByType(Task.class);

        for (Task task : tasks) {
            RobotTaskMetadata metadata = new RobotTaskMetadata();
            metadata.setRobotId(robotId.replace(".bpmn", "").replace("_enhanced", "").trim());
            metadata.setTaskId(task.getId());
            metadata.setTaskName(task.getName());

            if (task.getExtensionElements() != null) {
                Set<String> outputs = new LinkedHashSet<>();
                Set<String> inputs = new LinkedHashSet<>();
                Set<String> preconditions = new LinkedHashSet<>();
                Set<String> effects = new LinkedHashSet<>();

                // Older project files place parameters directly under extensionElements.
                for (CamundaOutputParameter output : task.getExtensionElements().getElementsQuery()
                        .filterByType(CamundaOutputParameter.class).list()) {
                    if ("producedData".equals(output.getCamundaName()) && !blank(output.getTextContent())) {
                        outputs.add(output.getTextContent().trim());
                    }
                }
                for (CamundaInputParameter input : task.getExtensionElements().getElementsQuery()
                        .filterByType(CamundaInputParameter.class).list()) {
                    addParameter(inputs, input.getCamundaName(), "requiredData", input.getTextContent());
                    addContract(preconditions, input.getCamundaName(), input.getTextContent(), "precondition", "preconditions");
                }
                for (CamundaOutputParameter output : task.getExtensionElements().getElementsQuery()
                        .filterByType(CamundaOutputParameter.class).list()) {
                    addContract(effects, output.getCamundaName(), output.getTextContent(), "effect", "effects");
                }

                // Standard Camunda files nest input/output parameters inside camunda:inputOutput.
                for (CamundaInputOutput inputOutput : task.getExtensionElements().getElementsQuery()
                        .filterByType(CamundaInputOutput.class).list()) {
                    for (CamundaOutputParameter output : inputOutput.getCamundaOutputParameters()) {
                        if ("producedData".equals(output.getCamundaName()) && !blank(output.getTextContent())) {
                            outputs.add(output.getTextContent().trim());
                        }
                        addContract(effects, output.getCamundaName(), output.getTextContent(), "effect", "effects");
                    }
                    for (CamundaInputParameter input : inputOutput.getCamundaInputParameters()) {
                        addParameter(inputs, input.getCamundaName(), "requiredData", input.getTextContent());
                        addContract(preconditions, input.getCamundaName(), input.getTextContent(), "precondition", "preconditions");
                    }
                }

                if (outputs.size() > 1) {
                    throw new IllegalArgumentException("Task " + task.getId() + " declares multiple producedData types: " + outputs);
                }
                metadata.setProducedDataType(outputs.isEmpty() ? null : outputs.iterator().next());
                metadata.getRequiredDataTypes().addAll(inputs);
                metadata.getPreconditions().addAll(preconditions);
                metadata.getEffects().addAll(effects);
            }
            metadataList.add(metadata);
        }
        logger.debug("Analyzed {} operational tasks for robot {}.", metadataList.size(), robotId);
        return metadataList;
    }

    private void addParameter(Set<String> target, String name, String expectedName, String value) {
        if (expectedName.equals(name) && !blank(value)) target.add(value.trim());
    }

    private void addContract(Set<String> target, String name, String value, String... acceptedNames) {
        if (blank(value) || name == null) return;
        for (String accepted : acceptedNames) {
            if (accepted.equalsIgnoreCase(name.trim())) {
                // Support repeated explicit literals separated by semicolons.
                for (String literal : value.split(";")) if (!blank(literal)) target.add(literal.trim());
                return;
            }
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
