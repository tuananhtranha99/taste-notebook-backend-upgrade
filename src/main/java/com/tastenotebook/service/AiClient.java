package com.tastenotebook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin client for Google Gemini's free API tier (generativelanguage.googleapis.com).
 * Only used for the two features that genuinely need AI reasoning:
 *  - checking whether a new dish fits a friend's taste profile
 *  - combining favorite items into one coherent suggested dish
 *
 * Get a free key at https://aistudio.google.com/apikey (no credit card needed)
 * and set it as the GEMINI_API_KEY environment variable.
 */
@Component
public class AiClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-2.0-flash}")
    private String model;

    /**
     * Sends a prompt to Gemini and returns the raw text response.
     * The prompt should instruct the model to reply with JSON only;
     * callers are responsible for parsing that JSON themselves.
     */
    public String generate(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not set. Get a free key at https://aistudio.google.com/apikey");
        }
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + apiKey;

            var payload = mapper.createObjectNode();
            var contents = payload.putArray("contents");
            var content = contents.addObject();
            var parts = content.putArray("parts");
            parts.addObject().put("text", prompt);

            // Ask Gemini to return plain JSON so we don't have to strip markdown fences.
            var generationConfig = payload.putObject("generationConfig");
            generationConfig.put("response_mime_type", "application/json");
            generationConfig.put("temperature", 0.4);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                throw new RuntimeException("Gemini API error (" + response.statusCode() + "): " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            return root.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText();

        } catch (Exception e) {
            throw new RuntimeException("Failed to call Gemini API: " + e.getMessage(), e);
        }
    }
}
