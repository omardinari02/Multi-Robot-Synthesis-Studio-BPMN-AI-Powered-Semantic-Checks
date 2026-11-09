package it.univaq.disim.mrs.synthesis.semantic;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements the syntactic part of thesis section 5.4.
 * STRONG requires exact type equality. WEAK accepts a produced subtype of the required type.
 * For structured values, all receiver-required field declarations (name:type) must occur
 * unchanged in the producer declaration. If no schema is supplied, structural compatibility
 * is unknown and this class does not pretend that a structural proof was performed.
 */
public final class SyntacticCompatibilityChecker {
    public enum Mode { STRONG, WEAK }

    private final OntologyManager ontology;

    public SyntacticCompatibilityChecker(OntologyManager ontology) {
        if (ontology == null) throw new IllegalArgumentException("OntologyManager is required.");
        this.ontology = ontology;
    }

    public boolean isTypeCompatible(String producedType, String requiredType, String mode) {
        Mode parsedMode = parseMode(mode);
        return parsedMode == Mode.STRONG
                ? ontology.isStronglyCompatible(producedType, requiredType)
                : ontology.isWeaklyCompatible(producedType, requiredType);
    }

    public boolean isStructurallyCompatible(Collection<String> producedFields, Collection<String> requiredFields) {
        if (producedFields == null || requiredFields == null) return false;
        if (producedFields.isEmpty() && requiredFields.isEmpty()) return true;
        if (producedFields.isEmpty() || requiredFields.isEmpty()) return false;
        Set<String> produced = producedFields.stream().map(SyntacticCompatibilityChecker::normalizeField)
                .collect(Collectors.toSet());
        return requiredFields.stream().map(SyntacticCompatibilityChecker::normalizeField)
                .allMatch(produced::contains);
    }

    /**
     * Verifies structural compatibility between two types using the fields declared in the ontology.
     * All fields required by 'requiredType' must be provided by 'producedType'.
     */
    public boolean isStructurallyCompatibleTypes(String producedType, String requiredType) {
        Set<String> producedFields = ontology.getFieldsForType(producedType);
        Set<String> requiredFields = ontology.getFieldsForType(requiredType);
        if (requiredFields.isEmpty()) {
            return true;
        }
        return isStructurallyCompatible(producedFields, requiredFields);
    }

    public static Mode parseMode(String mode) {
        if (mode == null || mode.trim().isEmpty()) return Mode.WEAK;
        try {
            return Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Syntactic compatibility mode must be STRONG or WEAK: " + mode, ex);
        }
    }

    private static String normalizeField(String field) {
        return field == null ? "" : field.trim().replaceAll("\\s*[:.]\\s*", ":").toLowerCase(Locale.ROOT);
    }
}
