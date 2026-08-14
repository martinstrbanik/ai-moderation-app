package sk.automoder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.automoder.ai.AiProviderException;
import sk.automoder.ai.AiResult;
import sk.automoder.ai.OpenRouterClient;
import sk.automoder.ai.PromptFactory;
import sk.automoder.dto.ModerationResponse;
import sk.automoder.exception.BadRequestException;
import sk.automoder.exception.NotFoundException;
import sk.automoder.model.AiModel;
import sk.automoder.model.ContentType;
import sk.automoder.model.ModerationLog;
import sk.automoder.model.Policy;
import sk.automoder.model.PolicyAction;
import sk.automoder.repository.ModerationLogRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ModerationService {

    private static final Map<String, Integer> SEVERITY_ORDER = Map.of(
            "NONE", 0, "LOW", 1, "MODERATE", 2, "HIGH", 3
    );

    private final PolicyService policyService;
    private final AiModelService modelService;
    private final ApiKeyService apiKeyService;
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;
    private final ModerationLogRepository logRepository;

    public ModerationService(PolicyService policyService, AiModelService modelService,
                             ApiKeyService apiKeyService, OpenRouterClient openRouterClient,
                             ObjectMapper objectMapper, ModerationLogRepository logRepository) {
        this.policyService = policyService;
        this.modelService = modelService;
        this.apiKeyService = apiKeyService;
        this.openRouterClient = openRouterClient;
        this.objectMapper = objectMapper;
        this.logRepository = logRepository;
    }

    @Transactional
    public ModerationResponse moderate(Long policyId, String text) {
        Policy policy = policyService.requirePolicy(policyId);
        if (!policy.isActive()) {
            throw new BadRequestException("Policy with id " + policyId + " is not active.");
        }

        String apiKey = apiKeyService.resolveDefaultPlainKey();
        AiModel primaryModel = modelService.requireModel(policy.getModelId());
        List<String> catLabels = parseCategoriesArray(policy.getCategories());

        String systemPrompt = PromptFactory.severitySystemPrompt(
                policy.getName(), catLabels, policy.getAction().name(), policy.getThreshold());

        long start = System.currentTimeMillis();
        AiResult ai;
        AiModel resolvedModel = primaryModel;
        try {
            ai = openRouterClient.call(apiKey, resolvedModel.getModelId(), systemPrompt, text);
        } catch (AiProviderException e) {
            if (policy.getFallbackModelId() != null) {
                resolvedModel = modelService.requireModel(policy.getFallbackModelId());
                ai = openRouterClient.call(apiKey, resolvedModel.getModelId(), systemPrompt, text);
            } else {
                throw e;
            }
        } catch (Exception e) {
            throw new AiProviderException(0, "Moderation call failed: " + e.getMessage());
        }
        long latencyMs = System.currentTimeMillis() - start;

        String severity;
        List<String> categories;
        String reason;
        try {
            JsonNode node = objectMapper.readTree(ai.content());
            severity = node.path("severity").asText("NONE").toUpperCase();
            categories = parseCategoriesArray(node.path("categories"));
            reason = node.path("reason").asText("");
        } catch (Exception e) {
            throw new AiProviderException(0, "Invalid model response: " + ai.content());
        }

        // map severity + threshold to verdict
        PolicyAction verdict = mapVerdict(policy, severity);
        double risk = ModerationResponse.riskFromSeverity(severity);

        // log
        ModerationLog logEntry = new ModerationLog();
        logEntry.setTenantId(PolicyService.DEFAULT_TENANT);
        logEntry.setPolicy(policy);
        logEntry.setModel(resolvedModel);
        logEntry.setContentType(ContentType.TEXT);
        logEntry.setVerdict(verdict.name());
        logEntry.setSeverity(severity);   // severity v moderation_log? need column... hmm
        logEntry.setCategories(categories.toString());
        logEntry.setConfidence(risk);
        logEntry.setLatencyMs(latencyMs);
        logRepository.save(logEntry);

        return new ModerationResponse(
                verdict.name(), severity, risk, categories, reason,
                resolvedModel.getId(), resolvedModel.getModelId(), latencyMs, policy.getId()
        );
    }

    private PolicyAction mapVerdict(Policy policy, String severity) {
        int sevOrd = SEVERITY_ORDER.getOrDefault(severity, 0);
        int threshOrd = (int) Math.floor(policy.getThreshold() * 4);
        if (threshOrd > 3) threshOrd = 3;
        return sevOrd >= threshOrd ? policy.getAction() : PolicyAction.ALLOW;
    }

    private List<String> parseCategoriesArray(JsonNode categoriesNode) {
        List<String> result = new ArrayList<>();
        if (categoriesNode != null && categoriesNode.isArray()) {
            for (JsonNode c : categoriesNode) {
                if (c.isTextual()) result.add(c.asText());
            }
        }
        return result;
    }

    private List<String> parseCategoriesArray(String categoriesJson) {
        if (categoriesJson == null || categoriesJson.isBlank()) return List.of();
        try {
            JsonNode arr = objectMapper.readTree(categoriesJson);
            return parseCategoriesArray(arr);
        } catch (Exception e) {
            return List.of();
        }
    }
}