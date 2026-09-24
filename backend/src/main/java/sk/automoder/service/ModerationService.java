package sk.automoder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.automoder.ai.AiProviderException;
import sk.automoder.ai.AiResult;
import sk.automoder.ai.OpenRouterClient;
import sk.automoder.ai.PromptFactory;
import sk.automoder.dto.ModerationRequest.ModerationItem;
import sk.automoder.dto.ModerationResponse;
import sk.automoder.dto.ModerationResponse.ModerationResultItem;
import sk.automoder.exception.BadRequestException;
import sk.automoder.model.AiModel;
import sk.automoder.model.ContentType;
import sk.automoder.model.ModerationLog;
import sk.automoder.model.Policy;
import sk.automoder.model.PolicyAction;
import sk.automoder.repository.ModerationLogRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Text moderation: classifies a batch of texts against a policy using the LLM and
 * returns the texts grouped by verdict (ALLOW / FLAG / BLOCK).
 *
 * <p>Texts are sent to the model in batches of {@code batchSize} (per request or
 * {@code automoder.moderation.batch-size}). If a batch response is unusable the
 * batch is retried with individual calls. Texts that still cannot be classified are
 * placed in the {@code flag} list ({@code severity = "UNKNOWN"}), since FLAG means
 * "needs human review".</p>
 *
 * <p>Per-item {@code latencyMs}/{@code cost} are <b>attributions</b>, not per-text
 * measurements: the whole batch's latency is assigned to each item of the batch and
 * the batch cost is split evenly ({@code batchCost / batchSize}).</p>
 */
@Service
public class ModerationService {

    private final PolicyService policyService;
    private final AiModelService modelService;
    private final ApiKeyService apiKeyService;
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;
    private final ModerationLogRepository logRepository;

    /** How many texts are sent to the model in one OpenRouter call. */
    @Value("${automoder.moderation.batch-size:10}")
    private int defaultBatchSize;

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
    public ModerationResponse moderate(Long policyId, List<ModerationItem> items, Integer requestBatchSize) {
        Policy policy = policyService.requirePolicy(policyId);
        if (!policy.isActive()) {
            throw new BadRequestException("Policy with id " + policyId + " is not active.");
        }

        String apiKey = apiKeyService.resolveDefaultPlainKey();
        AiModel primaryModel = modelService.requireModel(policy.getModelId());
        List<String> catLabels = parseCategoriesArray(policy.getCategories());

        String singlePrompt = PromptFactory.severitySystemPrompt(
                policy.getName(), catLabels, policy.getAction().name(), policy.getThreshold());
        String batchPrompt = PromptFactory.severityBatchSystemPrompt(
                policy.getName(), catLabels, policy.getAction().name(), policy.getThreshold());

        int effectiveBatchSize = Math.max(1, requestBatchSize != null ? requestBatchSize : defaultBatchSize);
        String requestId = UUID.randomUUID().toString();

        // assign internal 1-based ids (external ids are echoed through)
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ModerationItem item = items.get(i);
            targets.add(new Target((long) (i + 1), item.id(), item.text()));
        }
        List<List<Target>> batches = partition(targets, effectiveBatchSize);

        long start = System.currentTimeMillis();
        double totalCost = 0;
        boolean usedFallback = false;
        AiModel lastModel = primaryModel;
        List<ModerationResultItem> results = new ArrayList<>();

        for (List<Target> batch : batches) {
            AiModel model = primaryModel;
            AiResult ai = null;
            String failure = null;
            try {
                ai = callModel(apiKey, model, batch, singlePrompt, batchPrompt);
            } catch (AiProviderException e) {
                if (policy.getFallbackModelId() != null) {
                    usedFallback = true;
                    model = modelService.requireModel(policy.getFallbackModelId());
                    try {
                        ai = callModel(apiKey, model, batch, singlePrompt, batchPrompt);
                    } catch (AiProviderException e2) {
                        failure = e2.getMessage();
                    }
                } else {
                    failure = e.getMessage();
                }
            }
            lastModel = model;

            long batchLatency = 0;
            double batchCost = 0;
            Map<Long, Parsed> parsed = new LinkedHashMap<>();
            if (ai != null) {
                batchLatency += ai.latencyMs();
                batchCost += ai.costUsd();
                parsed = parseResponse(ai.content(), batch);
                // whole-batch response unusable -> retry this batch with individual calls
                if (parsed.isEmpty() && batch.size() > 1) {
                    for (Target target : batch) {
                        try {
                            AiResult one = openRouterClient.call(
                                    apiKey, model.getModelId(), singlePrompt, target.text());
                            batchLatency += one.latencyMs();
                            batchCost += one.costUsd();
                            parsed.putAll(parseResponse(one.content(), List.of(target)));
                        } catch (AiProviderException e) {
                            // leave unclassified - handled below
                        }
                    }
                }
            }
            totalCost += batchCost;

            // per-item attribution: whole batch latency, cost split evenly
            long perItemLatency = batchLatency;
            double perItemCost = batch.isEmpty() ? 0.0 : batchCost / batch.size();

            for (Target target : batch) {
                Parsed p = parsed.get(target.internalId());
                PolicyAction verdict;
                String severity;
                List<String> categories;
                String reason;
                double risk;
                if (p != null) {
                    severity = p.severity();
                    categories = p.categories();
                    reason = p.reason();
                    verdict = ModerationMapping.mapVerdict(
                            policy.getThreshold(), policy.getAction(), severity);
                    risk = ModerationResponse.riskFromSeverity(severity);
                } else {
                    severity = ModerationMapping.SEVERITY_UNKNOWN;
                    categories = List.of();
                    verdict = PolicyAction.FLAG;
                    risk = 0.0;
                    reason = failure != null
                            ? "Classification failed: " + failure
                            : "Model returned no result for this text.";
                }

                ModerationLog logEntry = new ModerationLog();
                logEntry.setTenantId(PolicyService.DEFAULT_TENANT);
                logEntry.setRequestId(requestId);
                logEntry.setPolicy(policy);
                logEntry.setModel(model);
                logEntry.setContentType(ContentType.TEXT);
                logEntry.setVerdict(verdict.name());
                logEntry.setSeverity(severity);
                logEntry.setCategories(categories.toString());
                logEntry.setConfidence(risk);
                logEntry.setLatencyMs(perItemLatency);
                logRepository.save(logEntry);

                results.add(new ModerationResultItem(
                        target.internalId(), target.externalId(), target.text(),
                        verdict.name(), severity, risk, categories, reason, perItemLatency, perItemCost));
            }
        }

        long latencyMs = System.currentTimeMillis() - start;
        Map<String, List<ModerationResultItem>> grouped = ModerationMapping.groupByVerdict(results);

        return new ModerationResponse(
                grouped.get(PolicyAction.ALLOW.name()),
                grouped.get(PolicyAction.FLAG.name()),
                grouped.get(PolicyAction.BLOCK.name()),
                policy.getId(),
                policy.getName(),
                policy.getThreshold(),
                policy.getAction().name(),
                lastModel.getId(),
                lastModel.getModelId(),
                usedFallback,
                requestId,
                effectiveBatchSize,
                batches.size(),
                results.size(),
                ModerationMapping.verdictCounts(results),
                ModerationMapping.severityCounts(results),
                ModerationMapping.categoryCounts(results),
                latencyMs,
                totalCost,
                results.isEmpty() ? 0.0 : (double) latencyMs / results.size(),
                Instant.now());
    }

    /** Sends one batch (or a single text) to the model. */
    private AiResult callModel(String apiKey, AiModel model, List<Target> batch,
                               String singlePrompt, String batchPrompt) {
        if (batch.size() == 1) {
            return openRouterClient.call(apiKey, model.getModelId(), singlePrompt, batch.get(0).text());
        }
        List<String> texts = batch.stream().map(Target::text).toList();
        return openRouterClient.call(apiKey, model.getModelId(), batchPrompt,
                PromptFactory.batchUserContent(texts));
    }

    /**
     * Parses a model response into results keyed by internal id. Accepts a JSON array
     * (batch) or a single JSON object (single call). Invalid/unparseable content yields
     * an empty map so the caller can trigger the fallback.
     */
    private Map<Long, Parsed> parseResponse(String content, List<Target> batch) {
        Map<Long, Parsed> out = new LinkedHashMap<>();
        if (content == null) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.isArray()) {
                // batchUserContent() numbers texts 1..batchSize (relative to the batch),
                // so map the returned id back to the batch position, not a global id.
                for (JsonNode node : root) {
                    int relativeId = node.path("id").asInt(-1);
                    if (relativeId >= 1 && relativeId <= batch.size()) {
                        out.put(batch.get(relativeId - 1).internalId(), toParsed(node));
                    }
                }
            } else if (root.isObject() && batch.size() == 1) {
                out.put(batch.get(0).internalId(), toParsed(root));
            }
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
        return out;
    }

    private Parsed toParsed(JsonNode node) {
        return new Parsed(
                node.path("severity").asText("NONE").toUpperCase(),
                parseCategoriesArray(node.path("categories")),
                node.path("reason").asText(""));
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            out.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return out;
    }

    private List<String> parseCategoriesArray(JsonNode categoriesNode) {
        List<String> result = new ArrayList<>();
        if (categoriesNode != null && categoriesNode.isArray()) {
            for (JsonNode c : categoriesNode) {
                if (c.isTextual()) {
                    result.add(c.asText());
                }
            }
        }
        return result;
    }

    private List<String> parseCategoriesArray(String categoriesJson) {
        if (categoriesJson == null || categoriesJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode arr = objectMapper.readTree(categoriesJson);
            return parseCategoriesArray(arr);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** An input text plus the application-assigned internal id. */
    private record Target(Long internalId, String externalId, String text) {
    }

    /** Severity details parsed from a model response for one text. */
    private record Parsed(String severity, List<String> categories, String reason) {
    }
}
