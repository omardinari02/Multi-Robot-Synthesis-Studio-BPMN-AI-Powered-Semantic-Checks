package it.univaq.disim.mrs.synthesis.ai;

import it.univaq.disim.mrs.synthesis.semantic.OntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * AI-powered semantic matcher with multiple reasoning modes.
 * Applies directional type compatibility and then checks semantic evidence.
 *
 * Supported modes:
 *  - HYBRID: Ontology concepts first, LLM fallback if no concepts
 *  - ONTOLOGY_STRICT: Only formal ontology (taxonomy.json / OWL)
 *  - LLM_REASONER: Natural language reasoning via LLM (Gemini/OpenAI/Local)
 */
public class AISemanticMatcher {
    private static final Logger logger = LoggerFactory.getLogger(AISemanticMatcher.class);
    private final LLMClient llmClient;
    private final OntologyManager ontologyManager;
    private String mode = "HYBRID";
    private String lastExplanation = "";

    public AISemanticMatcher(OntologyManager ontologyManager) {
        this(ontologyManager, new LLMClient());
    }

    public AISemanticMatcher(OntologyManager ontologyManager, LLMClient llmClient) {
        this.ontologyManager = ontologyManager;
        this.llmClient = llmClient == null ? new LLMClient() : llmClient;
    }

    public AISemanticMatcher() {
        this(new OntologyManager());
    }

    public void setMode(String mode) {
        this.mode = mode != null ? mode.toUpperCase(Locale.ROOT) : "HYBRID";
    }

    public String getMode() {
        return mode;
    }

    public void setExplicitApiKey(String apiKey) {
        if (this.llmClient != null) {
            this.llmClient.setExplicitApiKey(apiKey);
        }
    }

    public LLMClient getLLMClient() {
        return llmClient;
    }

    public String getLastExplanation() {
        return lastExplanation;
    }

    /** Returns the provider that actually answered the last LLM query (GEMINI, OPENAI, LOCAL, CUSTOM). */
    public String getLastProvider() {
        return llmClient.getLastProvider();
    }

    public String getLastApiError() {
        return llmClient != null ? llmClient.getLastApiError() : null;
    }

    /**
     * Compatibility method for callers without explicit concept identifiers.
     * When no remote model is configured, the fallback deliberately fails closed
     * unless the normalized semantic descriptions are identical.
     */
    public boolean isSemanticMatch(String producedType, String producedDesc,
                                   String requiredType, String requiredDesc) {
        if (!isTypeCompatible(producedType, requiredType)) {
            this.lastExplanation = "Tipo '" + producedType + "' incompatibile con '" + requiredType + "'";
            return false;
        }
        boolean result = llmClient.askAIForCompatibility(producedType, producedDesc, requiredType, requiredDesc);
        this.lastExplanation = llmClient.getLastExplanation();
        return result;
    }

    /**
     * Preferred deterministic path: an explicitly declared semantic concept must be
     * identical to or a subtype of the required concept, and the produced data type
     * must be identical to or a subtype of the consumer's required type.
     */
    public boolean isSemanticMatch(String producedType, String producedDesc,
                                   String requiredType, String requiredDesc,
                                   String producedConcept, String requiredConcept) {
        if (!isTypeCompatible(producedType, requiredType)) {
            logger.debug("Syntactic/ontology incompatibility: produced {} cannot satisfy {}", producedType, requiredType);
            this.lastExplanation = "Tipo '" + producedType + "' incompatibile con '" + requiredType + "'";
            return false;
        }

        // When LLM_REASONER is explicitly chosen, bypass rigid concept IDs and reason on natural language
        if ("LLM_REASONER".equalsIgnoreCase(mode)) {
            boolean llmResult = llmClient.askAIForCompatibility(producedType, producedDesc, requiredType, requiredDesc);
            this.lastExplanation = llmClient.getLastExplanation();
            return llmResult;
        }

        if ("ONTOLOGY_STRICT".equalsIgnoreCase(mode)) {
            String pConcept = normalize(producedConcept);
            String rConcept = normalize(requiredConcept);
            if (pConcept.isEmpty() || rConcept.isEmpty() || ontologyManager == null) {
                this.lastExplanation = "Concetto ontologico mancante in modalità STRICT";
                return false;
            }
            boolean compatibleConcept = ontologyManager.isSemanticConceptCompatible(pConcept, rConcept);
            this.lastExplanation = compatibleConcept
                    ? "Ontologia OWL: '" + pConcept + "' ⊑ '" + rConcept + "' (Validato)"
                    : "Incompatibilità ontologica tra '" + pConcept + "' e '" + rConcept + "'";
            return compatibleConcept;
        }

        // Default HYBRID mode
        String pConcept = normalize(producedConcept);
        String rConcept = normalize(requiredConcept);
        if (!pConcept.isEmpty() || !rConcept.isEmpty()) {
            boolean compatibleConcept = !pConcept.isEmpty() && !rConcept.isEmpty()
                    && ontologyManager != null
                    && ontologyManager.isSemanticConceptCompatible(pConcept, rConcept);
            if (!compatibleConcept) logger.debug("Semantic concept incompatibility: produced '{}' cannot satisfy required '{}'", pConcept, rConcept);
            this.lastExplanation = compatibleConcept
                    ? "Match ontologico concetti: '" + pConcept + "' ➔ '" + rConcept + "'"
                    : "Concetti ontologici non compatibili";
            return compatibleConcept;
        }

        boolean fallbackResult = llmClient.askAIForCompatibility(producedType, producedDesc, requiredType, requiredDesc);
        this.lastExplanation = llmClient.getLastExplanation();
        return fallbackResult;
    }

    private boolean isTypeCompatible(String producedType, String requiredType) {
        if (producedType == null || requiredType == null || producedType.trim().isEmpty() || requiredType.trim().isEmpty()) {
            return false;
        }
        return ontologyManager != null && ontologyManager.isCompatible(producedType, requiredType);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
