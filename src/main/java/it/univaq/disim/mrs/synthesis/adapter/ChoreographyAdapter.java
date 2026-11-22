package it.univaq.disim.mrs.synthesis.adapter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Collaboration;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.camunda.bpm.model.bpmn.instance.Participant;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter che traduce i Choreography Task interattivi (prodotti dal plugin Camunda Modeler)
 * nel formato BPMN Collaboration standard atteso dal MultiRobotSynthesisEngine.
 *
 * Estrae:
 * - Sender e Receiver dalla notazione "(Sender -> Receiver)"
 * - Messaggio iniziale e risposta da "[msg: ... | ack: ...]"
 * - Marcatore parallelo "|||"
 *
 * Crea o normalizza in modo trasparente i Participant, i Message e i MessageFlow necessari,
 * garantendo la retrocompatibilità totale senza toccare la logica interna dell'engine.
 */
public class ChoreographyAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ChoreographyAdapter.class);

    private static final Pattern MSG_PATTERN = Pattern.compile(
            "\\[(?:msg:\\s*([^|\\]]+))?(?:\\s*\\|\\s*)?(?:ack:\\s*([^\\]]+))?\\]",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ARROW_PATTERN = Pattern.compile("\\((.+?)\\s*->\\s*(.+?)\\)");

    /**
     * DTO per i metadati estratti da un Task di coreografia.
     */
    public static class ChoreoMeta {
        private final String action;
        private final String sender;
        private final String receiver;
        private final String topMsg;
        private final String bottomMsg;
        private final boolean parallel;

        public ChoreoMeta(String action, String sender, String receiver, String topMsg, String bottomMsg, boolean parallel) {
            this.action = action;
            this.sender = sender;
            this.receiver = receiver;
            this.topMsg = topMsg;
            this.bottomMsg = bottomMsg;
            this.parallel = parallel;
        }

        public String getAction() { return action; }
        public String getSender() { return sender; }
        public String getReceiver() { return receiver; }
        public String getTopMsg() { return topMsg; }
        public String getBottomMsg() { return bottomMsg; }
        public boolean isParallel() { return parallel; }
    }

    /**
     * Esegue l'adattamento del file BPMN in input.
     * Se il modello contiene task di coreografia da arricchire, genera un file temporaneo
     * normalizzato e lo restituisce. Altrimenti, ritorna il file originale intatto.
     */
    public File adapt(File inputFile) {
        if (inputFile == null || !inputFile.exists()) {
            throw new IllegalArgumentException("File di coreografia inesistente: " + inputFile);
        }

        BpmnModelInstance model = Bpmn.readModelFromFile(inputFile);
        boolean modified = adaptModel(model);

        if (!modified) {
            logger.info("[ChoreographyAdapter] Nessuna trasformazione necessaria per: {}", inputFile.getName());
            return inputFile;
        }

        try {
            Path tempDir = Path.of("target", "adapted");
            Files.createDirectories(tempDir);
            File adaptedFile = tempDir.resolve("Adapted_" + inputFile.getName()).toFile();
            Bpmn.writeModelToFile(adaptedFile, model);
            logger.info("[ChoreographyAdapter] Modello BPMN adattato salvato con successo in: {}", adaptedFile.getAbsolutePath());
            return adaptedFile;
        } catch (IOException e) {
            logger.error("[ChoreographyAdapter] Impossibile salvare il modello adattato: {}", e.getMessage(), e);
            throw new RuntimeException("Errore durante l'adattamento della coreografia: " + e.getMessage(), e);
        }
    }

    /**
     * Esegue l'adattamento in-memory del BpmnModelInstance.
     * Restituisce true se sono state apportate modifiche.
     */
    public boolean adaptModel(BpmnModelInstance model) {
        if (model == null) return false;

        Definitions definitions = model.getDefinitions();
        if (definitions == null) return false;

        Collection<Task> tasks = model.getModelElementsByType(Task.class);
        if (tasks.isEmpty()) return false;

        boolean anyAdapted = false;

        // Recupera o crea l'elemento Collaboration
        Collaboration collaboration = model.getModelElementsByType(Collaboration.class).stream().findFirst().orElse(null);
        if (collaboration == null) {
            collaboration = model.newInstance(Collaboration.class);
            collaboration.setId("Collaboration_Adapted_" + System.currentTimeMillis());
            definitions.addChildElement(collaboration);
            anyAdapted = true;
        }

        // Mappe di supporto per lookup rapido di Participant, Message e MessageFlow
        Map<String, Participant> participantsByName = new HashMap<>();
        for (Participant p : model.getModelElementsByType(Participant.class)) {
            if (p.getName() != null) {
                participantsByName.put(cleanName(p.getName()), p);
            }
        }

        Map<String, Message> messagesByName = new HashMap<>();
        for (Message m : model.getModelElementsByType(Message.class)) {
            if (m.getName() != null) {
                messagesByName.put(m.getName().trim().toLowerCase(), m);
            }
        }

        Map<String, MessageFlow> existingFlowsByKey = new HashMap<>();
        for (MessageFlow mf : model.getModelElementsByType(MessageFlow.class)) {
            String srcId = mf.getSource() != null ? mf.getSource().getId() : "";
            String tgtId = mf.getTarget() != null ? mf.getTarget().getId() : "";
            String msgName = mf.getMessage() != null ? mf.getMessage().getName() : "";
            existingFlowsByKey.put(srcId + "->" + tgtId + ":" + msgName.toLowerCase(), mf);
        }

        for (Task task : tasks) {
            String rawName = task.getName();
            ChoreoMeta meta = parseChoreoMeta(rawName, task.getId());
            if (meta == null) {
                continue;
            }

            // 1. Assicura la presenza dei Participant
            Participant senderPart = getOrCreateParticipant(model, collaboration, participantsByName, meta.getSender());
            Participant receiverPart = getOrCreateParticipant(model, collaboration, participantsByName, meta.getReceiver());

            // 2. Assicura il MessageFlow di andata (topMsg)
            if (meta.getTopMsg() != null && !meta.getTopMsg().isBlank()) {
                Message topMessage = getOrCreateMessage(model, definitions, messagesByName, meta.getTopMsg().trim());
                String flowKey = senderPart.getId() + "->" + receiverPart.getId() + ":" + topMessage.getName().toLowerCase();
                if (!existingFlowsByKey.containsKey(flowKey)) {
                    MessageFlow topFlow = model.newInstance(MessageFlow.class);
                    topFlow.setId("MessageFlow_" + sanitizeId(meta.getTopMsg()) + "_" + sanitizeId(task.getId()));
                    topFlow.setSource(senderPart);
                    topFlow.setTarget(receiverPart);
                    topFlow.setMessage(topMessage);
                    collaboration.addChildElement(topFlow);
                    existingFlowsByKey.put(flowKey, topFlow);
                    anyAdapted = true;
                    logger.info("[ChoreographyAdapter] Creato MessageFlow per '{}' ({} -> {})", topMessage.getName(), meta.getSender(), meta.getReceiver());
                }
            }

            // 3. Assicura il MessageFlow di risposta/ACK (bottomMsg)
            if (meta.getBottomMsg() != null && !meta.getBottomMsg().isBlank()) {
                Message ackMessage = getOrCreateMessage(model, definitions, messagesByName, meta.getBottomMsg().trim());
                String ackFlowKey = receiverPart.getId() + "->" + senderPart.getId() + ":" + ackMessage.getName().toLowerCase();
                if (!existingFlowsByKey.containsKey(ackFlowKey)) {
                    MessageFlow ackFlow = model.newInstance(MessageFlow.class);
                    ackFlow.setId("MessageFlow_" + sanitizeId(meta.getBottomMsg()) + "_" + sanitizeId(task.getId()) + "_ack");
                    ackFlow.setSource(receiverPart);
                    ackFlow.setTarget(senderPart);
                    ackFlow.setMessage(ackMessage);
                    collaboration.addChildElement(ackFlow);
                    existingFlowsByKey.put(ackFlowKey, ackFlow);
                    anyAdapted = true;
                    logger.info("[ChoreographyAdapter] Creato MessageFlow ACK per '{}' ({} -> {})", ackMessage.getName(), meta.getReceiver(), meta.getSender());
                }
            }

            // 4. Normalizza il nome del task rimuovendo [msg: ...] e ||| per massima compatibilità con l'engine
            String cleanName = meta.getAction() + "\n(" + meta.getSender() + " -> " + meta.getReceiver() + ")";
            if (!cleanName.equals(rawName)) {
                task.setName(cleanName);
                anyAdapted = true;
            }
        }

        // 5. Normalizzazione espressioni di guardia FEEL (=var == val) in JUEL standard (${var == val})
        for (org.camunda.bpm.model.bpmn.instance.SequenceFlow sf : model.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.SequenceFlow.class)) {
            org.camunda.bpm.model.bpmn.instance.ConditionExpression cond = sf.getConditionExpression();
            if (cond != null && cond.getTextContent() != null) {
                String expr = cond.getTextContent().trim();
                if (expr.startsWith("=")) {
                    String cleanExpr = expr.substring(1).trim();
                    cond.setTextContent("${" + cleanExpr + "}");
                    anyAdapted = true;
                    logger.info("[ChoreographyAdapter] Normalizzata espressione FEEL '{}' in JUEL '${{{}}}' su SequenceFlow '{}'", expr, cleanExpr, sf.getId());
                }
            }
        }

        return anyAdapted;
    }

    /**
     * Estrae i metadati strutturati dal nome del task.
     */
    public ChoreoMeta parseChoreoMeta(String rawText, String taskId) {
        if (rawText == null || rawText.isBlank()) return null;

        String text = rawText.trim();
        boolean parallel = text.contains("|||");

        String topMsg = null;
        String bottomMsg = null;

        Matcher msgMatcher = MSG_PATTERN.matcher(text);
        if (msgMatcher.find()) {
            if (msgMatcher.group(1) != null) topMsg = msgMatcher.group(1).trim();
            if (msgMatcher.group(2) != null) bottomMsg = msgMatcher.group(2).trim();
        }

        String cleaned = text.replaceAll("\\[.*?\\]", "").replace("|||", "").trim();

        Matcher arrowMatcher = ARROW_PATTERN.matcher(cleaned);
        if (arrowMatcher.find()) {
            String sender = arrowMatcher.group(1).trim();
            String receiver = arrowMatcher.group(2).trim();
            int parenIdx = cleaned.indexOf('(');
            String action = (parenIdx > 0 ? cleaned.substring(0, parenIdx) : taskId).replace("\n", " ").trim();
            if (action.isBlank()) action = "Task_" + taskId;
            return new ChoreoMeta(action, sender, receiver, topMsg, bottomMsg, parallel);
        }

        return null;
    }

    private Participant getOrCreateParticipant(BpmnModelInstance model, Collaboration collaboration,
                                               Map<String, Participant> cache, String participantName) {
        String clean = cleanName(participantName);
        if (cache.containsKey(clean)) {
            return cache.get(clean);
        }

        Participant p = model.newInstance(Participant.class);
        p.setId("Part_" + sanitizeId(participantName));
        p.setName(participantName);
        collaboration.addChildElement(p);
        cache.put(clean, p);
        return p;
    }

    private Message getOrCreateMessage(BpmnModelInstance model, Definitions definitions,
                                       Map<String, Message> cache, String messageName) {
        String key = messageName.trim().toLowerCase();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        Message m = model.newInstance(Message.class);
        m.setId("Msg_" + sanitizeId(messageName));
        m.setName(messageName);
        definitions.addChildElement(m);
        cache.put(key, m);
        return m;
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        return name.replace("Part_", "").trim().toLowerCase();
    }

    private static String sanitizeId(String val) {
        if (val == null) return "id_" + System.currentTimeMillis();
        return val.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
