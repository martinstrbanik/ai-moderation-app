package sk.automoder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.automoder.ai.AiProviderException;
import sk.automoder.ai.AiResult;
import sk.automoder.ai.OpenRouterClient;
import sk.automoder.dto.ModerationRequest.ModerationItem;
import sk.automoder.dto.ModerationResponse;
import sk.automoder.model.AiModel;
import sk.automoder.model.ModelType;
import sk.automoder.model.Policy;
import sk.automoder.model.PolicyAction;
import sk.automoder.repository.ModerationLogRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ModerationService} with mocked dependencies (no DB / no HTTP).
 * Focuses on batching, the batch-relative id mapping and the failure -> FLAG rule.
 */
class ModerationServiceTest {

    private static final String PRIMARY_ID = "openai/gpt-4o-mini";
    private static final String FALLBACK_ID = "openai/gpt-4o";

    private PolicyService policyService;
    private AiModelService modelService;
    private ApiKeyService apiKeyService;
    private OpenRouterClient client;
    private ModerationService service;

    private Policy policy;
    private AiModel primaryModel;

    @BeforeEach
    void setUp() {
        policyService = mock(PolicyService.class);
        modelService = mock(AiModelService.class);
        apiKeyService = mock(ApiKeyService.class);
        client = mock(OpenRouterClient.class);
        ModerationLogRepository logRepository = mock(ModerationLogRepository.class);
        service = new ModerationService(policyService, modelService, apiKeyService,
                client, new ObjectMapper(), logRepository);

        policy = new Policy();
        policy.setId(2L);
        policy.setName("Hate detection");
        policy.setCategories("[\"hate_speech\"]");
        policy.setThreshold(0.5);
        policy.setAction(PolicyAction.BLOCK);
        policy.setModelId(3L);
        policy.setActive(true);

        primaryModel = model(3L, PRIMARY_ID);

        when(policyService.requirePolicy(2L)).thenReturn(policy);
        when(apiKeyService.resolveDefaultPlainKey()).thenReturn("test-key");
        when(modelService.requireModel(3L)).thenReturn(primaryModel);
    }

    private static AiModel model(Long id, String modelId) {
        AiModel m = new AiModel();
        m.setId(id);
        m.setModelId(modelId);
        m.setName(modelId);
        m.setType(ModelType.TEXT);
        return m;
    }

    private static AiResult ai(String content) {
        return new AiResult(content, 0.001, 12L);
    }

    @Test
    void mapsBatchRelativeIdsAcrossMultipleBatches() {
        // batch 1 (2 items): relative ids 1 and 2 ; batch 2 (1 item): single object
        when(client.call(eq("test-key"), eq(PRIMARY_ID), anyString(), anyString()))
                .thenReturn(ai("[{\"id\":1,\"severity\":\"NONE\",\"categories\":[],\"reason\":\"ok\"},"
                                + "{\"id\":2,\"severity\":\"HIGH\",\"categories\":[\"violence\"],\"reason\":\"bad\"}]"),
                        ai("{\"severity\":\"MODERATE\",\"categories\":[\"hate_speech\"],\"reason\":\"meh\"}"));

        ModerationResponse r = service.moderate(2L, List.of(
                new ModerationItem("a", "text one"),
                new ModerationItem("b", "text two"),
                new ModerationItem("c", "text three")), 2);

        assertEquals(List.of("a"),
                r.allow().stream().map(ModerationResponse.ModerationResultItem::externalId).toList());
        assertEquals(List.of("b", "c"),
                r.block().stream().map(ModerationResponse.ModerationResultItem::externalId).toList());
        assertTrue(r.flag().isEmpty());
        assertEquals(1, r.verdictCounts().get("ALLOW"));
        assertEquals(2, r.verdictCounts().get("BLOCK"));
        assertEquals(2, r.batchCount());
        assertEquals(3, r.totalItems());
        assertEquals(2, r.batchSize());
        verify(client, times(2)).call(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unusableBatchResponseFallsBackToIndividualCalls() {
        when(client.call(anyString(), anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String user = inv.getArgument(3);
            if (user.contains("\n")) {
                return ai("not valid json"); // batch -> unusable
            }
            return ai("{\"severity\":\"HIGH\",\"categories\":[\"violence\"],\"reason\":\"bad\"}");
        });

        ModerationResponse r = service.moderate(2L, List.of(
                new ModerationItem("a", "text one"),
                new ModerationItem("b", "text two")), 2);

        assertEquals(2, r.block().size());
        assertTrue(r.flag().isEmpty());
    }

    @Test
    void usesFallbackModelWhenPrimaryFails() {
        policy.setFallbackModelId(4L);
        when(modelService.requireModel(4L)).thenReturn(model(4L, FALLBACK_ID));
        when(client.call(anyString(), eq(PRIMARY_ID), anyString(), anyString()))
                .thenThrow(new AiProviderException(500, "primary down"));
        when(client.call(anyString(), eq(FALLBACK_ID), anyString(), anyString()))
                .thenReturn(ai("{\"severity\":\"NONE\",\"categories\":[],\"reason\":\"ok\"}"));

        ModerationResponse r = service.moderate(2L, List.of(new ModerationItem("a", "hi")), null);

        assertTrue(r.usedFallback());
        assertEquals(FALLBACK_ID, r.modelName());
        assertEquals(1, r.allow().size());
    }

    @Test
    void fullyFailedBatchIsFlaggedAsUnknown() {
        when(client.call(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new AiProviderException(400, "Bad Request"));

        ModerationResponse r = service.moderate(2L, List.of(
                new ModerationItem("a", "text one"),
                new ModerationItem("b", "text two")), 2);

        assertEquals(2, r.flag().size());
        assertTrue(r.allow().isEmpty());
        assertTrue(r.block().isEmpty());
        assertEquals(2, r.severityCounts().get("UNKNOWN"));
        assertTrue(r.flag().get(0).reason().contains("Classification failed"));
    }
}
