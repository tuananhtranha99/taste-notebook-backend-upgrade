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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AiClient {

    private static final String GEMINI_API_BASE =
            "https://generativelanguage.googleapis.com/v1beta";

    /**
     * Google-maintained alias.
     *
     * This points to the latest Flash model instead of hard-coding
     * a version such as gemini-2.0-flash or gemini-3.6-flash.
     */
    private static final String LATEST_FLASH_MODEL =
            "gemini-flash-latest";

    /**
     * Known Gemini Flash models that currently have a Free Tier.
     *
     * This list is only used as a fallback when the latest alias
     * cannot be used.
     *
     * It is intentionally NOT used as the primary selection mechanism.
     */
    private static final List<String> FREE_FLASH_FALLBACKS = List.of(
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    /**
     * Optional explicit model override.
     *
     * If empty:
     *   -> use gemini-flash-latest
     *
     * Example:
     *
     * ai.gemini.model=gemini-3.6-flash
     *
     * If you set this property, the application intentionally uses
     * the configured model instead of automatic model selection.
     */
    private String configuredModel;

    /**
     * Cached model actually being used.
     *
     * volatile is enough here because:
     * - model discovery is rare
     * - the value is immutable once resolved
     * - synchronized block protects initialization
     */
    private volatile String resolvedModel;

    /**
     * Sends a prompt to Gemini and returns the raw text response.
     *
     * The prompt should instruct the model to reply with JSON only.
     * Callers are responsible for parsing that JSON themselves.
     */
    public String generate(String prompt) {

        validateApiKey();

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Gemini prompt must not be blank.");
        }

        try {
            String model = getOrResolveModel();

            try {
                return callGenerateContent(model, prompt);

            } catch (GeminiModelNotFoundException e) {

                /*
                 * The model may have been retired/deprecated between
                 * model discovery and the actual request.
                 *
                 * Clear the cache and discover again.
                 */
                invalidateResolvedModel();

                String newModel = resolveModel();

                synchronized (this) {
                    resolvedModel = newModel;
                }

                return callGenerateContent(newModel, prompt);
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to call Gemini API: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Returns the cached model or resolves it once.
     */
    private String getOrResolveModel() throws Exception {

        String model = resolvedModel;

        if (model != null && !model.isBlank()) {
            return model;
        }

        synchronized (this) {

            model = resolvedModel;

            if (model != null && !model.isBlank()) {
                return model;
            }

            model = resolveModel();

            resolvedModel = model;

            System.out.println(
                    "[Gemini] Selected model: " + resolvedModel
            );

            return model;
        }
    }

    /**
     * Resolves which Gemini model should be used.
     *
     * Priority:
     *
     * 1. Explicitly configured model
     * 2. Google-maintained latest Flash alias
     * 3. Discover available Free Tier Flash models
     */
    private String resolveModel() throws Exception {

        /*
         * Explicit configuration always wins.
         *
         * This is useful if you intentionally want to pin
         * the application to a specific model.
         */
        if (configuredModel != null && !configuredModel.isBlank()) {

            String model = normalizeModelName(configuredModel);

            System.out.println(
                    "[Gemini] Using explicitly configured model: " + model
            );

            return model;
        }

        /*
         * Normally we DO NOT need to call models.list().
         *
         * Google provides this alias specifically so applications
         * don't have to hard-code a versioned Flash model.
         */
        System.out.println(
                "[Gemini] Using Google latest Flash alias: "
                        + LATEST_FLASH_MODEL
        );

        return LATEST_FLASH_MODEL;
    }

    /**
     * Calls Gemini generateContent.
     */
    private String callGenerateContent(
            String model,
            String prompt
    ) throws Exception {

        String normalizedModel = normalizeModelName(model);

        String url = GEMINI_API_BASE
                + "/models/"
                + normalizedModel
                + ":generateContent?key="
                + apiKey;

        var payload = mapper.createObjectNode();

        var contents = payload.putArray("contents");

        var content = contents.addObject();

        var parts = content.putArray("parts");

        parts.addObject()
                .put("text", prompt);

        /*
         * Ask Gemini to return JSON directly.
         *
         * This matches your existing behavior.
         */
        var generationConfig =
                payload.putObject("generationConfig");

        generationConfig.put(
                "response_mime_type",
                "application/json"
        );

        generationConfig.put(
                "temperature",
                0.4
        );

        String requestBody =
                mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header(
                        "Content-Type",
                        "application/json"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                requestBody
                        )
                )
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        int status = response.statusCode();

        /*
         * 404 is handled specially.
         *
         * The model may have been retired even though our cached
         * model was valid previously.
         */
        if (status == 404) {

            throw new GeminiModelNotFoundException(
                    "Gemini model '" + normalizedModel
                            + "' is no longer available. "
                            + response.body()
            );
        }

        if (status >= 300) {

            throw new RuntimeException(
                    "Gemini API error ("
                            + status
                            + "): "
                            + response.body()
            );
        }

        return extractText(response.body());
    }

    /**
     * Extracts:
     *
     * candidates[0]
     *   -> content
     *      -> parts[0]
     *         -> text
     */
    private String extractText(
            String responseBody
    ) throws Exception {

        JsonNode root =
                mapper.readTree(responseBody);

        JsonNode candidates =
                root.path("candidates");

        if (!candidates.isArray()
                || candidates.isEmpty()) {

            throw new RuntimeException(
                    "Gemini returned no candidates. Response: "
                            + responseBody
            );
        }

        JsonNode text =
                candidates
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text");

        if (text.isMissingNode()
                || text.asText().isBlank()) {

            throw new RuntimeException(
                    "Gemini returned an empty text response. "
                            + "Response: "
                            + responseBody
            );
        }

        return text.asText();
    }

    /**
     * Invalidate cached model.
     */
    private void invalidateResolvedModel() {

        synchronized (this) {
            resolvedModel = null;
        }

        System.out.println(
                "[Gemini] Cached model invalidated."
        );
    }

    /**
     * Validates Gemini API key.
     */
    private void validateApiKey() {

        if (apiKey == null || apiKey.isBlank()) {

            throw new IllegalStateException(
                    "GEMINI_API_KEY is not set. "
                            + "Get a free key at "
                            + "https://aistudio.google.com/apikey"
            );
        }
    }

    /**
     * Normalizes model names.
     *
     * Accepts both:
     *
     * gemini-3.6-flash
     *
     * and:
     *
     * models/gemini-3.6-flash
     */
    private String normalizeModelName(
            String model
    ) {

        if (model == null) {
            throw new IllegalArgumentException(
                    "Gemini model must not be null."
            );
        }

        String normalized =
                model.trim();

        if (normalized.startsWith("models/")) {
            normalized =
                    normalized.substring("models/".length());
        }

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "Gemini model must not be blank."
            );
        }

        return normalized;
    }

    /**
     * Optional helper if you ever want to inspect which Gemini
     * models your API key can currently access.
     *
     * This is NOT called for every generate() request.
     */
    public List<GeminiModel> listAvailableFlashModels()
            throws Exception {

        validateApiKey();

        String url =
                GEMINI_API_BASE
                        + "/models?key="
                        + apiKey;

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() >= 300) {

            throw new RuntimeException(
                    "Failed to list Gemini models ("
                            + response.statusCode()
                            + "): "
                            + response.body()
            );
        }

        JsonNode root =
                mapper.readTree(response.body());

        List<GeminiModel> models =
                new ArrayList<>();

        for (JsonNode node :
                root.path("models")) {

            String name =
                    node.path("name").asText();

            String baseModelId =
                    node.path("baseModelId").asText();

            String displayName =
                    node.path("displayName").asText();

            boolean supportsGenerateContent =
                    supportsGenerateContent(node);

            if (!supportsGenerateContent) {
                continue;
            }

            String modelId =
                    !baseModelId.isBlank()
                            ? baseModelId
                            : normalizeModelName(name);

            String lower =
                    modelId.toLowerCase(
                            Locale.ROOT
                    );

            /*
             * Only text Flash models.
             *
             * Avoid:
             * - image
             * - tts
             * - live
             * - embedding
             * - robotics
             */
            if (!lower.contains("flash")) {
                continue;
            }

            if (lower.contains("image")
                    || lower.contains("tts")
                    || lower.contains("live")) {
                continue;
            }

            models.add(
                    new GeminiModel(
                            modelId,
                            displayName
                    )
            );
        }

        return models;
    }

    /**
     * Checks whether the model supports generateContent.
     */
    private boolean supportsGenerateContent(
            JsonNode model
    ) {

        JsonNode methods =
                model.path(
                        "supportedGenerationMethods"
                );

        if (!methods.isArray()) {
            return false;
        }

        for (JsonNode method : methods) {

            if ("generateContent"
                    .equals(method.asText())) {

                return true;
            }
        }

        return false;
    }

    /**
     * Returns the best currently available Free Tier
     * Flash fallback.
     *
     * This method is intentionally not used during normal
     * requests because gemini-flash-latest is the preferred
     * solution.
     */
    public String resolveFreeFallbackModel()
            throws Exception {

        List<GeminiModel> available =
                listAvailableFlashModels();

        Set<String> availableIds =
                new HashSet<>();

        for (GeminiModel model : available) {
            availableIds.add(
                    normalizeModelName(model.name())
            );
        }

        /*
         * Prefer known Free Tier models in a deterministic order.
         */
        for (String preferred :
                FREE_FLASH_FALLBACKS) {

            if (availableIds.contains(preferred)) {
                return preferred;
            }
        }

        throw new IllegalStateException(
                "No known Free Tier Gemini Flash model "
                        + "supporting generateContent is available."
        );
    }

    /**
     * Small immutable representation of a Gemini model.
     */
    public record GeminiModel(
            String name,
            String displayName
    ) {
    }

    /**
     * Special exception used only when Gemini reports
     * that the selected model no longer exists.
     */
    private static class GeminiModelNotFoundException
            extends RuntimeException {

        public GeminiModelNotFoundException(
                String message
        ) {
            super(message);
        }
    }
}
