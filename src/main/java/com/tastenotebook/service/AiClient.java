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
import java.util.List;

@Component
public class AiClient {

    // ============================================================
    // Gemini
    // ============================================================

    private static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta";

    private static final String GEMINI_PRIMARY =
            "gemini-flash-latest";

    /**
     * Gemini fallbacks.
     *
     * Flash-Lite is preferred because this app mainly needs:
     * - classification
     * - taste matching
     * - simple reasoning
     * - JSON generation
     */
    private static final List<String> GEMINI_FALLBACK_MODELS = List.of(
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite"
    );


    // ============================================================
    // Groq
    // ============================================================

    private static final String GROQ_BASE_URL =
            "https://api.groq.com/openai/v1";

    /**
     * Fast model suitable for our relatively simple tasks.
     *
     * Groq currently lists GPT OSS 20B as an available model
     * and provides a Free plan with rate limits.
     */
    private static final String GROQ_MODEL =
            "openai/gpt-oss-20b";


    // ============================================================
    // OpenRouter
    // ============================================================

    private static final String OPENROUTER_BASE_URL =
            "https://openrouter.ai/api/v1";

    /**
     * OpenRouter's free router.
     *
     * It automatically selects from currently available
     * free models.
     */
    private static final String OPENROUTER_FREE_MODEL =
            "openrouter/free";


    // ============================================================
    // HTTP
    // ============================================================

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

    private final ObjectMapper mapper =
            new ObjectMapper();


    // ============================================================
    // API Keys
    // ============================================================

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${ai.openrouter.api-key:}")
    private String openRouterApiKey;


    // ============================================================
    // Optional configuration
    // ============================================================

    /**
     * If you set:
     *
     * ai.gemini.model=gemini-3.6-flash
     *
     * that model becomes Gemini's primary model.
     *
     * If empty, gemini-flash-latest is used.
     */
    @Value("${ai.gemini.model:}")
    private String configuredGeminiModel;


    // ============================================================
    // Temporary cooldown
    // ============================================================

    /**
     * When a provider returns 429/503, don't immediately hit
     * the same provider again for the next few seconds.
     *
     * IMPORTANT:
     *
     * This is NOT a retry delay.
     *
     * The current request immediately falls through to the next
     * provider.
     */
    private volatile long geminiCooldownUntil = 0;

    private volatile long groqCooldownUntil = 0;

    private volatile long openRouterCooldownUntil = 0;

    private static final long COOLDOWN_MS = 30_000L;


    // ============================================================
    // Public API
    // ============================================================

    /**
     * Sends a prompt to an AI provider.
     *
     * Failover order:
     *
     * 1. Gemini primary
     * 2. Gemini Flash-Lite
     * 3. Gemini Flash-Lite older version
     * 4. Groq
     * 5. OpenRouter free router
     *
     * The first successful provider wins.
     */
    public String generate(String prompt) {

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "AI prompt must not be blank."
            );
        }

        List<ProviderAttempt> providers =
                buildProviderOrder();

        List<String> failures =
                new ArrayList<>();

        for (ProviderAttempt provider : providers) {

            if (provider.isInCooldown()) {

                System.out.println(
                        "[AI] Skipping "
                                + provider.name()
                                + " because it is in cooldown."
                );

                continue;
            }

            try {

                System.out.println(
                        "[AI] Trying "
                                + provider.name()
                );

                String result =
                        provider.call(prompt);

                System.out.println(
                        "[AI] SUCCESS: "
                                + provider.name()
                );

                return result;

            } catch (TemporaryAiException e) {

                failures.add(
                        provider.name()
                                + " -> "
                                + e.getMessage()
                );

                System.out.println(
                        "[AI] Temporary failure from "
                                + provider.name()
                                + ": "
                                + e.getMessage()
                );

                provider.cooldown();

                /*
                 * IMPORTANT:
                 *
                 * No waiting.
                 *
                 * Immediately try next provider.
                 */
                continue;

            } catch (ModelNotFoundException e) {

                failures.add(
                        provider.name()
                                + " -> model not found"
                );

                System.out.println(
                        "[AI] Model unavailable: "
                                + provider.name()
                                + " -> "
                                + e.getMessage()
                );

                continue;

            } catch (Exception e) {

                /*
                 * For this temporary testing implementation,
                 * we fail over on provider errors as well.
                 *
                 * Later, when productionizing this class,
                 * we can distinguish:
                 *
                 * 400 -> don't fallback
                 * 401/403 -> don't fallback
                 * 429/500/503/timeout -> fallback
                 */
                failures.add(
                        provider.name()
                                + " -> "
                                + e.getMessage()
                );

                System.out.println(
                        "[AI] Failure from "
                                + provider.name()
                                + ": "
                                + e.getMessage()
                );

                continue;
            }
        }

        throw new RuntimeException(
                "All AI providers failed. "
                        + "Failures: "
                        + failures
        );
    }


    // ============================================================
    // Provider order
    // ============================================================

    private List<ProviderAttempt> buildProviderOrder() {

        List<ProviderAttempt> providers =
                new ArrayList<>();

        /*
         * ========================================================
         * GEMINI
         * ========================================================
         */

        String primaryGemini =
                getPrimaryGeminiModel();

        providers.add(
                new ProviderAttempt(
                        "Gemini/" + primaryGemini,
                        () -> callGemini(
                                primaryGemini,
                                null
                        ),
                        this::isGeminiInCooldown,
                        this::cooldownGemini
                )
        );

        /*
         * Gemini fallback models.
         */
        for (String model :
                GEMINI_FALLBACK_MODELS) {

            if (model.equals(primaryGemini)) {
                continue;
            }

            providers.add(
                    new ProviderAttempt(
                            "Gemini/" + model,
                            () -> callGemini(
                                    model,
                                    null
                            ),
                            this::isGeminiInCooldown,
                            this::cooldownGemini
                    )
            );
        }


        /*
         * ========================================================
         * GROQ
         * ========================================================
         */

        if (hasText(groqApiKey)) {

            providers.add(
                    new ProviderAttempt(
                            "Groq/" + GROQ_MODEL,
                            () -> callGroq(
                                    GROQ_MODEL,
                                    null
                            ),
                            this::isGroqInCooldown,
                            this::cooldownGroq
                    )
            );
        }


        /*
         * ========================================================
         * OPENROUTER
         * ========================================================
         */

        if (hasText(openRouterApiKey)) {

            providers.add(
                    new ProviderAttempt(
                            "OpenRouter/"
                                    + OPENROUTER_FREE_MODEL,
                            () -> callOpenRouter(
                                    OPENROUTER_FREE_MODEL,
                                    null
                            ),
                            this::isOpenRouterInCooldown,
                            this::cooldownOpenRouter
                    )
            );
        }

        return providers;
    }


    // ============================================================
    // Gemini
    // ============================================================

    private String callGemini(
            String model,
            String prompt
    ) throws Exception {

        /*
         * ProviderAttempt cannot directly pass the prompt
         * because the lambda is created before generate().
         *
         * This method is overridden below through ThreadLocal.
         *
         * See callProvider() implementation.
         */
        return callGeminiInternal(
                model,
                CURRENT_PROMPT.get()
        );
    }

    private String callGeminiInternal(
            String model,
            String prompt
    ) throws Exception {

        if (!hasText(geminiApiKey)) {

            throw new IllegalStateException(
                    "Gemini API key is not configured."
            );
        }

        String normalizedModel =
                normalizeModelName(model);

        String url =
                GEMINI_BASE_URL
                        + "/models/"
                        + normalizedModel
                        + ":generateContent?key="
                        + geminiApiKey;

        var payload =
                mapper.createObjectNode();

        var contents =
                payload.putArray("contents");

        var content =
                contents.addObject();

        var parts =
                content.putArray("parts");

        parts.addObject()
                .put("text", prompt);

        /*
         * Ask Gemini for JSON.
         */
        var generationConfig =
                payload.putObject(
                        "generationConfig"
                );

        generationConfig.put(
                "response_mime_type",
                "application/json"
        );

        String body =
                mapper.writeValueAsString(
                        payload
                );

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(body)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        int status =
                response.statusCode();

        String responseBody =
                response.body();

        if (status >= 200 && status < 300) {

            return extractGeminiText(
                    responseBody
            );
        }

        if (status == 404) {

            throw new ModelNotFoundException(
                    "Gemini model '"
                            + normalizedModel
                            + "' is no longer available. "
                            + responseBody
            );
        }

        if (status == 429 || status == 503) {

            throw new TemporaryAiException(
                    "Gemini "
                            + status
                            + ": "
                            + responseBody
            );
        }

        throw new RuntimeException(
                "Gemini API error ("
                        + status
                        + "): "
                        + responseBody
        );
    }


    // ============================================================
    // Groq
    // ============================================================

    private String callGroq(
            String model,
            String ignored
    ) throws Exception {

        if (!hasText(groqApiKey)) {

            throw new IllegalStateException(
                    "Groq API key is not configured."
            );
        }

        String prompt =
                CURRENT_PROMPT.get();

        String url =
                GROQ_BASE_URL
                        + "/chat/completions";

        var payload =
                mapper.createObjectNode();

        payload.put(
                "model",
                model
        );

        var messages =
                payload.putArray(
                        "messages"
                );

        var message =
                messages.addObject();

        message.put(
                "role",
                "user"
        );

        message.put(
                "content",
                prompt
        );

        /*
         * Groq uses OpenAI-compatible Chat Completions.
         *
         * Ask for JSON.
         */
        var responseFormat =
                payload.putObject(
                        "response_format"
                );

        responseFormat.put(
                "type",
                "json_object"
        );

        String body =
                mapper.writeValueAsString(
                        payload
                );

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .header(
                                "Authorization",
                                "Bearer "
                                        + groqApiKey
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(body)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        int status =
                response.statusCode();

        String responseBody =
                response.body();

        if (status >= 200 && status < 300) {

            return extractOpenAiCompatibleText(
                    responseBody
            );
        }

        if (status == 429
                || status == 500
                || status == 502
                || status == 503
                || status == 504) {

            throw new TemporaryAiException(
                    "Groq "
                            + status
                            + ": "
                            + responseBody
            );
        }

        throw new RuntimeException(
                "Groq API error ("
                        + status
                        + "): "
                        + responseBody
        );
    }


    // ============================================================
    // OpenRouter
    // ============================================================

    private String callOpenRouter(
            String model,
            String ignored
    ) throws Exception {

        if (!hasText(openRouterApiKey)) {

            throw new IllegalStateException(
                    "OpenRouter API key is not configured."
            );
        }

        String prompt =
                CURRENT_PROMPT.get();

        String url =
                OPENROUTER_BASE_URL
                        + "/chat/completions";

        var payload =
                mapper.createObjectNode();

        payload.put(
                "model",
                model
        );

        var messages =
                payload.putArray(
                        "messages"
                );

        var message =
                messages.addObject();

        message.put(
                "role",
                "user"
        );

        message.put(
                "content",
                prompt
        );

        /*
         * Request JSON output where supported.
         *
         * openrouter/free automatically selects an available
         * free model. OpenRouter's free router supports structured
         * outputs for models that support the feature.
         */
        var responseFormat =
                payload.putObject(
                        "response_format"
                );

        responseFormat.put(
                "type",
                "json_object"
        );

        String body =
                mapper.writeValueAsString(
                        payload
                );

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .header(
                                "Authorization",
                                "Bearer "
                                        + openRouterApiKey
                        )
                        .header(
                                "HTTP-Referer",
                                "http://localhost"
                        )
                        .header(
                                "X-Title",
                                "Food Recommendation App"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(body)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        int status =
                response.statusCode();

        String responseBody =
                response.body();

        if (status >= 200 && status < 300) {

            return extractOpenAiCompatibleText(
                    responseBody
            );
        }

        if (status == 429
                || status == 500
                || status == 502
                || status == 503
                || status == 504) {

            throw new TemporaryAiException(
                    "OpenRouter "
                            + status
                            + ": "
                            + responseBody
            );
        }

        throw new RuntimeException(
                "OpenRouter API error ("
                        + status
                        + "): "
                        + responseBody
        );
    }


    // ============================================================
    // Response parsing
    // ============================================================

    private String extractGeminiText(
            String responseBody
    ) throws Exception {

        JsonNode root =
                mapper.readTree(
                        responseBody
                );

        JsonNode candidates =
                root.path("candidates");

        if (!candidates.isArray()
                || candidates.isEmpty()) {

            throw new RuntimeException(
                    "Gemini returned no candidates. "
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
                    "Gemini returned empty response. "
                            + responseBody
            );
        }

        return text.asText();
    }


    /**
     * Parses OpenAI-compatible responses from:
     *
     * Groq
     * OpenRouter
     */
    private String extractOpenAiCompatibleText(
            String responseBody
    ) throws Exception {

        JsonNode root =
                mapper.readTree(
                        responseBody
                );

        JsonNode choices =
                root.path("choices");

        if (!choices.isArray()
                || choices.isEmpty()) {

            throw new RuntimeException(
                    "AI provider returned no choices. "
                            + responseBody
            );
        }

        JsonNode message =
                choices
                        .path(0)
                        .path("message");

        JsonNode content =
                message.path("content");

        if (content.isTextual()
                && !content.asText().isBlank()) {

            return content.asText();
        }

        /*
         * Some reasoning models may put the useful output
         * into another field depending on provider/model.
         */
        JsonNode text =
                choices
                        .path(0)
                        .path("text");

        if (text.isTextual()
                && !text.asText().isBlank()) {

            return text.asText();
        }

        throw new RuntimeException(
                "AI provider returned empty content. "
                        + responseBody
        );
    }


    // ============================================================
    // Gemini model selection
    // ============================================================

    private String getPrimaryGeminiModel() {

        if (hasText(configuredGeminiModel)) {

            return normalizeModelName(
                    configuredGeminiModel
            );
        }

        return GEMINI_PRIMARY;
    }


    // ============================================================
    // Cooldown
    // ============================================================

    private boolean isGeminiInCooldown() {

        return System.currentTimeMillis()
                < geminiCooldownUntil;
    }

    private void cooldownGemini() {

        geminiCooldownUntil =
                System.currentTimeMillis()
                        + COOLDOWN_MS;
    }

    private boolean isGroqInCooldown() {

        return System.currentTimeMillis()
                < groqCooldownUntil;
    }

    private void cooldownGroq() {

        groqCooldownUntil =
                System.currentTimeMillis()
                        + COOLDOWN_MS;
    }

    private boolean isOpenRouterInCooldown() {

        return System.currentTimeMillis()
                < openRouterCooldownUntil;
    }

    private void cooldownOpenRouter() {

        openRouterCooldownUntil =
                System.currentTimeMillis()
                        + COOLDOWN_MS;
    }


    // ============================================================
    // Helpers
    // ============================================================

    private String normalizeModelName(
            String model
    ) {

        if (model == null) {
            throw new IllegalArgumentException(
                    "AI model must not be null."
            );
        }

        String result =
                model.trim();

        if (result.startsWith("models/")) {

            result =
                    result.substring(
                            "models/".length()
                    );
        }

        if (result.isBlank()) {

            throw new IllegalArgumentException(
                    "AI model must not be blank."
            );
        }

        return result;
    }

    private boolean hasText(
            String value
    ) {

        return value != null
                && !value.isBlank();
    }


    // ============================================================
    // ThreadLocal prompt
    // ============================================================

    /**
     * Used only to keep the whole temporary implementation
     * inside this single class without creating provider classes.
     */
    private static final ThreadLocal<String> CURRENT_PROMPT =
            new ThreadLocal<>();


    // ============================================================
    // Provider attempt
    // ============================================================

    private class ProviderAttempt {

        private final String name;

        private final ThrowingSupplier supplier;

        private final CooldownChecker cooldownChecker;

        private final Runnable cooldownAction;

        ProviderAttempt(
                String name,
                ThrowingSupplier supplier,
                CooldownChecker cooldownChecker,
                Runnable cooldownAction
        ) {

            this.name = name;
            this.supplier = supplier;
            this.cooldownChecker = cooldownChecker;
            this.cooldownAction = cooldownAction;
        }

        String name() {
            return name;
        }

        boolean isInCooldown() {
            return cooldownChecker.isInCooldown();
        }

        void cooldown() {
            cooldownAction.run();
        }

        String call(String prompt)
                throws Exception {

            /*
             * Set prompt so provider lambdas can access it.
             */
            CURRENT_PROMPT.set(prompt);

            try {
                return supplier.get();
            } finally {
                CURRENT_PROMPT.remove();
            }
        }
    }


    // ============================================================
    // Functional interfaces
    // ============================================================

    @FunctionalInterface
    private interface ThrowingSupplier {

        String get() throws Exception;
    }

    @FunctionalInterface
    private interface CooldownChecker {

        boolean isInCooldown();
    }


    // ============================================================
    // Exceptions
    // ============================================================

    private static class TemporaryAiException
            extends RuntimeException {

        TemporaryAiException(
                String message
        ) {
            super(message);
        }
    }

    private static class ModelNotFoundException
            extends RuntimeException {

        ModelNotFoundException(
                String message
        ) {
            super(message);
        }
    }
}
