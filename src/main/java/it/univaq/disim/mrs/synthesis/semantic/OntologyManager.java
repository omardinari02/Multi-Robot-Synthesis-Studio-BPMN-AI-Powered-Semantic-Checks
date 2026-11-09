package it.univaq.disim.mrs.synthesis.semantic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Directional ontology reasoning for data types and semantic concepts.
 * Each hierarchy maps a child/subtype to its direct supertypes. Compatibility
 * is directional: produced ⊑ required.
 */
public class OntologyManager {
    private static final Logger logger = LoggerFactory.getLogger(OntologyManager.class);
    private Map<String, List<String>> directSupertypes = new HashMap<>();
    private Map<String, List<String>> directSemanticSupertypes = new HashMap<>();

    /**
     * Loads either the legacy flat data-type taxonomy or the structured format:
     * { "dataTypes": { ... }, "semanticConcepts": { ... } }.
     */
    public void loadOntology(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Ontology file is missing: "
                    + (file == null ? "null" : file.getAbsolutePath()));
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(file);
            if (root == null || !root.isObject() || root.isEmpty()) {
                throw new IllegalArgumentException("Ontology is empty or invalid: " + file.getAbsolutePath());
            }
            boolean structured = root.has("dataTypes") || root.has("semanticConcepts");
            if (structured) {
                directSupertypes = normalizeHierarchy(readHierarchy(mapper, root.get("dataTypes"), file, "dataTypes"));
                directSemanticSupertypes = normalizeHierarchy(readHierarchy(mapper, root.get("semanticConcepts"), file, "semanticConcepts"));
            } else {
                // Backwards compatibility for existing taxonomy files and test fixtures.
                Map<String, List<String>> legacy = mapper.convertValue(root,
                        new TypeReference<Map<String, List<String>>>() {});
                directSupertypes = normalizeHierarchy(legacy);
                directSemanticSupertypes = new HashMap<>();
            }
            detectCycles(directSupertypes, "data type");
            detectCycles(directSemanticSupertypes, "semantic concept");
            logger.info("Loaded ontology with {} data types and {} semantic concepts from {}.",
                    directSupertypes.size(), directSemanticSupertypes.size(), file.getName());
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot parse ontology: " + file.getAbsolutePath(), e);
        }
    }

    private Map<String, List<String>> readHierarchy(ObjectMapper mapper, JsonNode node, File file, String name) {
        if (node == null || node.isNull()) return new HashMap<>();
        if (!node.isObject()) throw new IllegalArgumentException("Ontology section '" + name + "' must be an object in " + file.getName());
        return mapper.convertValue(node, new TypeReference<Map<String, List<String>>>() {});
    }

    private Map<String, List<String>> normalizeHierarchy(Map<String, List<String>> loaded) {
        if (loaded == null) throw new IllegalArgumentException("Ontology hierarchy is null.");
        Map<String, List<String>> normalized = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : loaded.entrySet()) {
            String child = normalize(entry.getKey());
            if (child.isEmpty()) throw new IllegalArgumentException("Ontology contains an empty concept name.");
            if (normalized.containsKey(child)) {
                throw new IllegalArgumentException("Ontology contains duplicate concept names after normalization: " + entry.getKey());
            }
            List<String> parents = entry.getValue() == null ? Collections.emptyList() : entry.getValue();
            List<String> normalizedParents = parents.stream().map(OntologyManager::normalize).collect(Collectors.toList());
            if (normalizedParents.stream().anyMatch(String::isEmpty)) {
                throw new IllegalArgumentException("Ontology concept '" + entry.getKey() + "' has an empty parent reference.");
            }
            if (new HashSet<>(normalizedParents).size() != normalizedParents.size()) {
                throw new IllegalArgumentException("Ontology concept '" + entry.getKey() + "' has duplicate parent references.");
            }
            normalized.put(child, normalizedParents);
        }
        return normalized;
    }

    /** Strong syntactic compatibility: both declared base types must be identical. */
    public boolean isStronglyCompatible(String produced, String required) {
        String left = normalize(produced);
        String right = normalize(required);
        return !left.isEmpty() && left.equals(right);
    }

    /** Weak syntactic compatibility: produced type is identical to or a subtype of required type. */
    public boolean isWeaklyCompatible(String produced, String required) {
        return isSubsumedBy(produced, required, directSupertypes);
    }

    /** Semantic subsumption: produced concept must be identical to or narrower than the required concept. */
    public boolean isSemanticConceptCompatible(String producedConcept, String requiredConcept) {
        return isSubsumedBy(producedConcept, requiredConcept, directSemanticSupertypes);
    }

    private boolean isSubsumedBy(String childValue, String parentValue, Map<String, List<String>> hierarchy) {
        String child = normalize(childValue);
        String parent = normalize(parentValue);
        if (child.isEmpty() || parent.isEmpty()) return false;
        if (child.equals(parent)) return true;

        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(child);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            for (String directParent : hierarchy.getOrDefault(current, Collections.emptyList())) {
                if (directParent.equals(parent)) return true;
                pending.addLast(directParent);
            }
        }
        return false;
    }

    /** Backwards-compatible name for directional weak type compatibility. */
    public boolean isCompatible(String produced, String required) {
        return isWeaklyCompatible(produced, required);
    }

    /**
     * Returns the set of structural fields belonging to a given data type according to the taxonomy.
     * Fields are identified by the presence of a dot or colon (e.g. "latitude.Double", "tractorId.String")
     * and have 'typeName' as an ancestor in dataTypes.
     */
    public Set<String> getFieldsForType(String typeName) {
        String target = normalize(typeName);
        if (target.isEmpty()) return Collections.emptySet();
        Set<String> fields = new HashSet<>();
        for (String candidate : directSupertypes.keySet()) {
            if (candidate.contains(".") || candidate.contains(":")) {
                if (isSubsumedBy(candidate, target, directSupertypes)) {
                    fields.add(candidate);
                }
            }
        }
        return Collections.unmodifiableSet(fields);
    }

    public Map<String, List<String>> getDirectSupertypes() {
        return immutableCopy(directSupertypes);
    }

    public Map<String, List<String>> getDirectSemanticSupertypes() {
        return immutableCopy(directSemanticSupertypes);
    }

    private Map<String, List<String>> immutableCopy(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

    private void detectCycles(Map<String, List<String>> hierarchy, String kind) {
        for (String concept : hierarchy.keySet()) {
            if (isReachable(concept, concept, hierarchy)) {
                throw new IllegalArgumentException("Cyclic " + kind + " ontology relationship detected at concept: " + concept);
            }
        }
    }

    private boolean isReachable(String start, String target, Map<String, List<String>> hierarchy) {
        Deque<String> pending = new ArrayDeque<>(hierarchy.getOrDefault(start, Collections.emptyList()));
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (current.equals(target)) return true;
            if (visited.add(current)) pending.addAll(hierarchy.getOrDefault(current, Collections.emptyList()));
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
