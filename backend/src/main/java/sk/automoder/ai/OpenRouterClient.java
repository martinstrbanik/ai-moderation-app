package sk.automoder.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client for the OpenRouter chat completions API.
 * Uses the BYO API key supplied by the user (decrypted on demand).
 */
@Component
public class OpenRouterClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenRouterClient(@Value("${automoder.openrouter.base-url}") String baseUrl,
                            @Value("${automoder.openrouter.timeout-seconds}") long timeoutSeconds,
                            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 30)));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Calls the chat completions endpoint with strict JSON output.
     *
     * @param apiKey plaintext BYO OpenRouter key
     * @param modelId OpenRouter model id (e.g. "openai/gpt-4o-mini")
     * @param system system prompt
     * @param user user content
     */
    public AiResult call(String apiKey, String modelId, String system, String user) {
        long start = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        ));
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_object"));

        try {
            String response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            long latencyMs = System.currentTimeMillis() - start;

            JsonNode node = objectMapper.readTree(response);
            String content = node.path("choices").path(0).path("message").path("content").asText(null);
            double cost = node.path("usage").path("cost").asDouble(0.0);
            return new AiResult(content, cost, latencyMs);
        } catch (HttpClientErrorException e) {
            throw new AiProviderException(e.getStatusCode().value(),
                    "OpenRouter HTTP " + e.getStatusCode().value() + ": " + e.getStatusText()
                            + (e.getStatusCode().value() == 401 ? " (check your API key)" : ""));
        } catch (HttpServerErrorException e) {
            throw new AiProviderException(e.getStatusCode().value(),
                    "OpenRouter server error " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new AiProviderException(0, "OpenRouter call failed: " + e.getMessage());
        }
    }
}