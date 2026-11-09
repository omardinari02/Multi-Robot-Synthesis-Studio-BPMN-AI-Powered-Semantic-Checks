package it.univaq.disim.mrs.synthesis.input;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.univaq.disim.mrs.synthesis.model.DataNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads and validates the explicit semantic specifications used by the pipeline. */
public class UserInputManager {
    private static final Logger logger = LoggerFactory.getLogger(UserInputManager.class);
    private final ObjectMapper mapper = new ObjectMapper();

    public List<DataNode> loadSemanticSpecifications(File semanticsFile) {
        if (semanticsFile == null || !semanticsFile.isFile()) {
            throw new IllegalArgumentException("Semantic specification file is missing: "
                    + (semanticsFile == null ? "null" : semanticsFile.getAbsolutePath()));
        }
        try {
            List<DataNode> specs = mapper.readValue(semanticsFile, new TypeReference<List<DataNode>>() {});
            if (specs == null || specs.isEmpty()) {
                throw new IllegalArgumentException("Semantic specification file is empty: " + semanticsFile.getAbsolutePath());
            }
            Set<String> messageNames = new HashSet<>();
            for (int i = 0; i < specs.size(); i++) {
                DataNode spec = specs.get(i);
                if (spec == null || !spec.hasRequiredProducerFields()) {
                    throw new IllegalArgumentException("Invalid semantic specification at index " + i
                            + ": robotId, taskId, messageName, dataType and semanticDescription are required.");
                }
                String key = spec.getMessageName().trim().toLowerCase(java.util.Locale.ROOT);
                if (!messageNames.add(key)) {
                    throw new IllegalArgumentException("Duplicate message specification: " + spec.getMessageName());
                }
                String mode = spec.getSyntacticCompatibility();
                if (mode == null || !("STRONG".equalsIgnoreCase(mode.trim()) || "WEAK".equalsIgnoreCase(mode.trim()))) {
                    throw new IllegalArgumentException("Invalid syntacticCompatibility for message '" + spec.getMessageName()
                            + "': expected STRONG or WEAK.");
                }
                spec.setSyntacticCompatibility(mode.trim().toUpperCase(java.util.Locale.ROOT));
                validateFields(spec.getProducedFields(), "producedFields", spec.getMessageName());
                validateFields(spec.getReceiverFields(), "receiverFields", spec.getMessageName());
                if (spec.getProducedFields().isEmpty() != spec.getReceiverFields().isEmpty()) {
                    throw new IllegalArgumentException("Structural schema for message '" + spec.getMessageName()
                            + "' must declare both producedFields and receiverFields, or neither.");
                }
            }
            logger.info("Loaded {} validated semantic specifications from {}.", specs.size(), semanticsFile.getName());
            return specs;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot parse semantic specifications from "
                    + semanticsFile.getAbsolutePath(), e);
        }
    }

    private void validateFields(List<String> fields, String fieldName, String messageName) {
        Set<String> seen = new HashSet<>();
        for (String field : fields) {
            if (field == null || field.trim().isEmpty()) {
                throw new IllegalArgumentException("Empty field in " + fieldName + " for message '" + messageName + "'.");
            }
            String[] declaration = field.trim().split(":", 2);
            if (declaration.length != 2 || declaration[0].trim().isEmpty() || declaration[1].trim().isEmpty()) {
                throw new IllegalArgumentException("Field declaration '" + field + "' in " + fieldName
                        + " must use the form name:type for message '" + messageName + "'.");
            }
            String normalizedName = declaration[0].trim().toLowerCase(java.util.Locale.ROOT);
            if (!seen.add(normalizedName)) {
                throw new IllegalArgumentException("Duplicate field name '" + declaration[0].trim() + "' in " + fieldName
                        + " for message '" + messageName + "'.");
            }
        }
    }
}
