package it.univaq.disim.mrs.synthesis.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LLMClientTest {

    @Test
    void testCustomJudgeTakesPrecedence() {
        LLMClient client = new LLMClient((prodType, prodDesc, reqType, reqDesc) -> true);
        boolean result = client.askAIForCompatibility("TypeA", "DescA", "TypeB", "DescB");
        assertTrue(result);
        assertEquals("CUSTOM", client.getLastProvider());
        assertTrue(client.getLastExplanation().contains("Custom Semantic Judge"));
    }

    @Test
    void testLocalReasonerWeedCoordinatesMatch() {
        LLMClient client = new LLMClient();
        boolean result = client.askAIForCompatibility(
                "Coordinate", "Coordinate GPS delle erbacce individuate dal drone",
                "Coordinate", "Zona di lavoro dove effettuare il taglio delle erbacce"
        );
        assertTrue(result);
        assertEquals("LOCAL", client.getLastProvider());
        assertTrue(client.getLastExplanation().contains("dedotta equivalenza operativa") || client.getLastExplanation().contains("Score"));
    }

    @Test
    void testLocalReasonerIncompatibleConcepts() {
        LLMClient client = new LLMClient();
        boolean result = client.askAIForCompatibility(
                "WeatherForecast", "Previsioni meteo pioggia e vento per domani",
                "BatteryStatus", "Livello di carica residua della batteria del trattore"
        );
        assertFalse(result);
        assertEquals("LOCAL", client.getLastProvider());
        assertTrue(client.getLastExplanation().contains("Decision: FALSE"));
    }

    @Test
    void testExplicitApiKeyConfiguration() {
        LLMClient client = new LLMClient();
        assertNull(client.getExplicitApiKey());
        client.setExplicitApiKey("AIzaSyTestKey123");
        assertEquals("AIzaSyTestKey123", client.getExplicitApiKey());

        // Placeholder keys are ignored
        client.setExplicitApiKey("your_api_key_here");
        assertNull(client.getExplicitApiKey());
    }

    @Test
    void testBlankInputsFailClosed() {
        LLMClient client = new LLMClient();
        assertFalse(client.askAIForCompatibility("", "Desc", "Type", "Desc"));
        assertFalse(client.askAIForCompatibility("Type", "", "Type", "Desc"));
        assertFalse(client.askAIForCompatibility("Type", "Desc", null, "Desc"));
        assertFalse(client.askAIForCompatibility("Type", "Desc", "Type", "   "));
    }
}