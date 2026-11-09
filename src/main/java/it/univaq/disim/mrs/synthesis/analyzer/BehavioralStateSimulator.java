package it.univaq.disim.mrs.synthesis.analyzer;

import it.univaq.disim.mrs.synthesis.model.RobotTaskMetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small, deterministic state simulator for explicit task contracts.
 * Contract literals are key=value; multiple literals are separate entries.
 * It deliberately does not guess missing preconditions/effects.
 */
public final class BehavioralStateSimulator {
    public enum Status { PASS, FAIL, UNRESOLVED }

    public static final class Result {
        private final Status status;
        private final String explanation;
        private Result(Status status, String explanation) {
            this.status = status;
            this.explanation = explanation;
        }
        public Status getStatus() { return status; }
        public String getExplanation() { return explanation; }
        @Override public String toString() { return status + ": " + explanation; }
    }

    /** Simulates a declared task path from an explicit initial state. */
    public Result simulate(List<RobotTaskMetadata> path, Map<String, String> initialState) {
        if (path == null || path.isEmpty()) return new Result(Status.UNRESOLVED, "No task path was supplied.");
        Map<String, String> state = new LinkedHashMap<>();
        if (initialState != null) {
            for (Map.Entry<String, String> entry : initialState.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null || key.trim().isEmpty() || value.trim().isEmpty()) {
                    return new Result(Status.UNRESOLVED,
                            "The initial state contains a null or blank key/value; state cannot be interpreted safely.");
                }
                String normalizedKey = normalize(key);
                String normalizedValue = normalize(value);
                String previous = state.putIfAbsent(normalizedKey, normalizedValue);
                if (previous != null && !previous.equals(normalizedValue)) {
                    return new Result(Status.FAIL, "Contradictory initial values are declared for state key '"
                            + normalizedKey + "'.");
                }
            }
        }
        for (RobotTaskMetadata task : path) {
            if (task == null) return new Result(Status.UNRESOLVED, "The task path contains missing metadata.");
        }
        boolean hasAnyContract = path.stream().anyMatch(t -> !t.getPreconditions().isEmpty() || !t.getEffects().isEmpty());
        if (!hasAnyContract) return new Result(Status.UNRESOLVED,
                "No explicit precondition/effect contracts are declared in this path; behavior cannot be proven.");

        for (RobotTaskMetadata task : path) {
            Result contractValidation = validateTaskContract(task);
            if (contractValidation != null) return contractValidation;
            for (String literal : task.getPreconditions()) {
                Literal parsed = parse(literal);
                if (parsed == null) return new Result(Status.UNRESOLVED,
                        "Unsupported precondition at task " + task.getTaskId() + ": " + literal);
                String actual = state.get(parsed.key);
                if (actual == null) return new Result(Status.UNRESOLVED,
                        "No state value is available for precondition '" + literal + "' at task " + task.getTaskId() + ".");
                if (!actual.equals(parsed.value)) return new Result(Status.FAIL,
                        "Precondition '" + literal + "' is violated at task " + task.getTaskId() + " (actual=" + actual + ").");
            }
            for (String effect : task.getEffects()) {
                Literal parsed = parse(effect);
                if (parsed == null) return new Result(Status.UNRESOLVED,
                        "Unsupported effect at task " + task.getTaskId() + ": " + effect);
                state.put(parsed.key, parsed.value);
            }
        }
        return new Result(Status.PASS, "All declared preconditions hold and effects were applied along the supplied path.");
    }

    /** Rejects malformed or internally contradictory declarations before applying them. */
    private Result validateTaskContract(RobotTaskMetadata task) {
        Map<String, String> preconditions = new LinkedHashMap<>();
        for (String literal : task.getPreconditions()) {
            Literal parsed = parse(literal);
            if (parsed == null) {
                return new Result(Status.UNRESOLVED, "Unsupported precondition at task "
                        + task.getTaskId() + ": " + literal);
            }
            String previous = preconditions.putIfAbsent(parsed.key, parsed.value);
            if (previous != null && !previous.equals(parsed.value)) {
                return new Result(Status.FAIL, "Contradictory preconditions for '" + parsed.key
                        + "' are declared at task " + task.getTaskId() + ".");
            }
        }

        Map<String, String> effects = new LinkedHashMap<>();
        for (String literal : task.getEffects()) {
            Literal parsed = parse(literal);
            if (parsed == null) {
                return new Result(Status.UNRESOLVED, "Unsupported effect at task "
                        + task.getTaskId() + ": " + literal);
            }
            String previous = effects.putIfAbsent(parsed.key, parsed.value);
            if (previous != null && !previous.equals(parsed.value)) {
                return new Result(Status.FAIL, "Contradictory effects for '" + parsed.key
                        + "' are declared at task " + task.getTaskId() + ".");
            }
        }
        return null;
    }

    private static Literal parse(String text) {
        if (text == null) return null;
        int equals = text.indexOf('=');
        if (equals <= 0 || equals == text.length() - 1) return null;
        String key = normalize(text.substring(0, equals));
        String value = normalize(text.substring(equals + 1));
        if (key.isEmpty() || value.isEmpty()) return null;
        return new Literal(key, value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Literal {
        private final String key;
        private final String value;
        private Literal(String key, String value) { this.key = key; this.value = value; }
    }
}
