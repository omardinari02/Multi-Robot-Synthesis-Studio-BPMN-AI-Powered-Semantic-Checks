package it.univaq.disim.mrs.synthesis.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class SynthesisWebServerTest {

    private static SynthesisWebServer server;
    private static final int TEST_PORT = 18080;

    @BeforeAll
    public static void setUp() {
        server = new SynthesisWebServer(TEST_PORT);
        server.start();
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testStatusEndpoint() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());

        try (InputStream is = conn.getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"status\":\"UP\""));
            assertTrue(json.contains("\"engine\":\"MultiRobotSynthesisEngine\""));
        }
    }

    @Test
    public void testModelsEndpoint() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/models");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());

        try (InputStream is = conn.getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("Drone.bpmn") || json.contains("models"));
        }
    }

    @Test
    public void testIndexUiServed() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("text/html"));

        try (InputStream is = conn.getInputStream()) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(html.contains("Multi-Robot Synthesis Studio"));
            assertTrue(html.contains("DISIM"));
        }
    }
}
