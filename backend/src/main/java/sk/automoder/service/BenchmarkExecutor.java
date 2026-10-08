package sk.automoder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import sk.automoder.ai.AiProviderException;
import sk.automoder.ai.AiResult;
import sk.automoder.ai.OpenRouterClient;
import sk.automoder.ai.PromptFactory;
import sk.automoder.exception.NotFoundException;
import sk.automoder.model.ApiKey;
import sk.automoder.model.AiModel;
import sk.automoder.model.BenchmarkLevel;
import sk.automoder.model.BenchmarkMode;
import sk.automoder.model.BenchmarkResult;
import sk.automoder.model.BenchmarkRun;
import sk.automoder.model.Category;
import sk.automoder.model.Dataset;
import sk.automoder.model.DatasetSample;
import sk.automoder.model.MetricScores;
import sk.automoder.model.PolicyAction;
import sk.automoder.model.RunStatus;
import sk.automoder.model.Severity;
import sk.automoder.repository.ApiKeyRepository;
import sk.automoder.repository.BenchmarkResultRepository;
import sk.automoder.repository.BenchmarkRunRepository;
import sk.automoder.repository.DatasetSampleRepository;
import sk.automoder.security.AesGcmEncryptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Executes a benchmark run asynchronously: classifies dataset samples with each
 * requested model and computes precision/recall/F1/accuracy, latency and cost.
 */
@Component
public class BenchmarkExecutor {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkExecutor.class);

    /** How often (in samples) progress is logged and persisted. */
    private static final int PROGRESS_EVERY = 25;

    /** Severity thresholds swept by a moderation benchmark (in ascending severity order). */
    private static final List<Severity> THRESHOLDS =
            List.of(Severity.NONE, Severity.LOW, Severity.MODERATE, Severity.HIGH);

    /** Label set the verdict metrics are computed over. */
    private static final Set<String> VERDICT_LABELS =
            new LinkedHashSet<>(List.of(PolicyAction.ALLOW.name(), PolicyAction.BLOCK.name()));

    /** How many samples are sent to the model in a single OpenRouter call. */
    @Value("${automoder.benchmark.batch-size:10}")
    private int batchSize;

    private final BenchmarkRunRepository runRepository;
    private final BenchmarkResultRepository resultRepository;
    private final DatasetSampleRepository sampleRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AesGcmEncryptor encryptor;
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;

    public BenchmarkExecutor(BenchmarkRunRepository runRepository,
                             BenchmarkResultRepository resultRepository,
                             DatasetSampleRepository sampleRepository,
                             ApiKeyRepository apiKeyRepository,
                             AesGcmEncryptor encryptor,
                             OpenRouterClient openRouterClient,
                             ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.sampleRepository = sampleRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.encryptor = encryptor;
        this.openRouterClient = openRouterClient;
        this.objectMapper = objectMapper;
    }

    @Async("benchmarkTaskExecutor")
    public void execute(Long runId) {
        try {
            run(runId);
        } catch (Exception e) {
            log.error("Benchmark run {} failed.", runId, e);
            markFailed(runId);
        }
    }

    private void run(Long runId) {
        BenchmarkRun run = runRepository.findDetailedById(runId)
                .orElseThrow(() -> NotFoundException.of("Benchmark run", runId));

        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        runRepository.save(run);

        BenchmarkMode mode = run.getMode() == null ? BenchmarkMode.CLASSIFICATION : run.getMode();
        List<DatasetSample> all = sampleRepository.findByDataset(run.getDataset());
        List<DatasetSample> samples = selectSamples(all, run.getLevel(), runId);

        String apiKeyPlain = resolveApiKey(run);
        int effectiveBatchSize = run.getBatchSize() != null ? run.getBatchSize() : batchSize;

        List<BenchmarkResult> results = resultRepository.findWithModelByRun(run);
        for (BenchmarkResult result : results) {
            AiModel model = result.getModel();
            log.info("Benchmark run {}: starting model {} on {} samples (mode={}, batch-size={}).",
                    runId, model.getModelId(), samples.size(), mode, effectiveBatchSize);
            if (mode == BenchmarkMode.MODERATION) {
                evaluateModeration(run, samples, apiKeyPlain, model.getModelId(),
                        effectiveBatchSize, result);
            } else {
                evaluateClassification(all, samples, apiKeyPlain, model.getModelId(),
                        effectiveBatchSize, result);
            }
            result.setProcessedSamples(samples.size());
            resultRepository.save(result);
            log.info("Benchmark run {}: model {} -> P={} R={} F1={} acc={} ({} samples, {} errors)",
                    runId, model.getModelId(),
                    fmt(result.getPrecision()), fmt(result.getRecall()),
                    fmt(result.getF1()), fmt(result.getAccuracy()),
                    samples.size(), result.getErrorCount());
        }

        run.setStatus(RunStatus.COMPLETED);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);
        log.info("Benchmark run {} completed ({} models, {} samples).",
                runId, results.size(), samples.size());
    }

    /**
     * Label-classification mode: classify samples into the dataset's own label set and
     * score label accuracy against the ground-truth label. The policy is not used here.
     */
    private void evaluateClassification(List<DatasetSample> all, List<DatasetSample> samples,
                                        String apiKey, String modelId, int batchSize,
                                        BenchmarkResult result) {
        List<String> labels = all.stream()
                .map(DatasetSample::getExpectedLabel).distinct().sorted().toList();
        String singleSystemPrompt = PromptFactory.classificationSystemPrompt(labels);
        String batchSystemPrompt = PromptFactory.classificationBatchSystemPrompt(labels);
        evaluate(samples, labels, apiKey, modelId,
                singleSystemPrompt, batchSystemPrompt, batchSize, result);
    }

    /**
     * Moderation mode: run the severity prompt against the dataset's own labels, map the
     * model's severity to an ALLOW/BLOCK verdict, and score it against the dataset's
     * expected verdict (from its label -&gt; verdict mapping). No policy is involved: the
     * categories are derived from the dataset, so the prompt and the ground truth refer to
     * the same concepts (otherwise the run would not be evaluable).
     *
     * <p>The same severity ratings are reused to compute the verdict metrics for
     * <b>every</b> threshold (the threshold sweep) at no additional model cost. A moderation
     * run has no single operating point, so the result's scalar columns stay {@code null}
     * and {@link BenchmarkResult#getThresholdMetrics()} is the deliverable.</p>
     */
    private void evaluateModeration(BenchmarkRun run, List<DatasetSample> samples, String apiKey,
                                    String modelId, int batchSize, BenchmarkResult result) {
        List<Category> categories = moderationCategories(run.getDataset());
        String singleSystemPrompt = PromptFactory.severitySystemPrompt(categories);
        String batchSystemPrompt = PromptFactory.severityBatchSystemPrompt(categories);
        Map<String, PolicyAction> verdictByLabel = run.getDataset().verdictMap();

        ModAcc acc = new ModAcc();
        for (List<DatasetSample> batch : partition(samples, Math.max(1, batchSize))) {
            if (batch.size() == 1) {
                processModerationOne(acc, apiKey, modelId, verdictByLabel,
                        singleSystemPrompt, batch.get(0));
            } else {
                processModerationBatch(acc, apiKey, modelId, verdictByLabel,
                        singleSystemPrompt, batchSystemPrompt, batch);
            }
            int processed = acc.severities.size();
            if (processed % PROGRESS_EVERY == 0) {
                result.setProcessedSamples(processed);
                resultRepository.save(result);
                log.info("Benchmark model {}: {}/{} samples processed (mode=MODERATION, errors={})",
                        modelId, processed, samples.size(), acc.errors);
            }
        }

        result.setThresholdMetrics(computeThresholdSweep(acc));
        // No policy threshold: a moderation run has no single operating point, so it
        // reports the whole sweep in thresholdMetrics and leaves the scalar metric
        // columns null (classification mode fills them).
        result.setAvgLatency(samples.isEmpty() ? 0.0 : (double) acc.latencySum / samples.size());
        result.setCost(acc.costSum);
        result.setErrorCount(acc.errors);
        if (acc.errors > 0) {
            log.warn("Benchmark model {}: {} of {} samples failed (first error: {})",
                    modelId, acc.errors, samples.size(), acc.firstError);
        }
    }

    /** Verdict metrics for every severity threshold, derived from the stored severities. */
    private Map<Severity, MetricScores> computeThresholdSweep(ModAcc acc) {
        Map<Severity, MetricScores> out = new LinkedHashMap<>();
        for (Severity threshold : THRESHOLDS) {
            List<String> predicted = new ArrayList<>(acc.severities.size());
            for (Severity severity : acc.severities) {
                if (severity == null || severity == Severity.UNKNOWN) {
                    predicted.add(null); // unclassified -> counted as incorrect
                } else {
                    predicted.add(severity.atLeast(threshold)
                            ? PolicyAction.BLOCK.name() : PolicyAction.ALLOW.name());
                }
            }
            Metrics.Summary summary = Metrics.compute(acc.expectedVerdicts, predicted, VERDICT_LABELS);
            out.put(threshold, new MetricScores(
                    summary.precision(), summary.recall(), summary.f1(), summary.accuracy()));
        }
        return out;
    }

    /**
     * The severity prompt's categories: the dataset's labels that imply a {@code BLOCK}
     * verdict (the violation concepts). Severity measures how strongly a text violates
     * them, so a text matching no category is {@code NONE}. Order follows the dataset's
     * {@code labelVerdicts} mapping (deterministic).
     */
    private static List<Category> moderationCategories(Dataset dataset) {
        return dataset.verdictMap().entrySet().stream()
                .filter(e -> e.getValue() == PolicyAction.BLOCK)
                .map(e -> new Category(e.getKey(), e.getKey()))
                .toList();
    }

    /** Null-safe metric formatting (a moderation run leaves the scalar metric columns null). */
    private static String fmt(Double value) {
        return value == null ? "n/a" : String.format("%.3f", value);
    }

    private void evaluate(List<DatasetSample> samples, List<String> labels, String apiKey,
                          String modelId, String singleSystemPrompt, String batchSystemPrompt,
                          int batchSize, BenchmarkResult result) {
        Acc acc = new Acc();
        for (List<DatasetSample> batch : partition(samples, Math.max(1, batchSize))) {
            if (batch.size() == 1) {
                processOne(acc, apiKey, modelId, labels, singleSystemPrompt, batch.get(0));
            } else {
                processBatch(acc, apiKey, modelId, labels, singleSystemPrompt, batchSystemPrompt, batch);
            }
            int processed = acc.predicted.size();
            if (processed % PROGRESS_EVERY == 0) {
                result.setProcessedSamples(processed);
                resultRepository.save(result);
                log.info("Benchmark model {}: {}/{} samples processed (errors={}, avgLatency={} ms)",
                        modelId, processed, samples.size(), acc.errors,
                        processed == 0 ? 0 : acc.latencySum / processed);
            }
        }

        Metrics.Summary summary = Metrics.compute(acc.expected, acc.predicted, new LinkedHashSet<>(labels));
        result.setPrecision(summary.precision());
        result.setRecall(summary.recall());
        result.setF1(summary.f1());
        result.setAccuracy(summary.accuracy());
        result.setAvgLatency(samples.isEmpty() ? 0.0 : (double) acc.latencySum / samples.size());
        result.setCost(acc.costSum);
        result.setErrorCount(acc.errors);
        if (acc.errors > 0) {
            log.warn("Benchmark model {}: {} of {} samples failed (first error: {})",
                    modelId, acc.errors, samples.size(), acc.firstError);
        }
    }

    private void processOne(Acc acc, String apiKey, String modelId, List<String> labels,
                            String systemPrompt, DatasetSample sample) {
        acc.expected.add(sample.getExpectedLabel());
        AiResult ai = null;
        try {
            ai = openRouterClient.call(apiKey, modelId, systemPrompt, sample.getContent());
        } catch (AiProviderException e) {
            acc.errors++;
            if (acc.firstError == null) {
                acc.firstError = e.getMessage();
            }
        }
        if (ai != null) {
            acc.latencySum += ai.latencyMs();
            acc.costSum += ai.costUsd();
            String pred = parseLabel(ai.content(), labels);
            if (pred == null) {
                acc.errors++;
            }
            acc.predicted.add(pred);
        } else {
            acc.predicted.add(null);
        }
    }

    private void processBatch(Acc acc, String apiKey, String modelId, List<String> labels,
                              String singleSystemPrompt, String batchSystemPrompt,
                              List<DatasetSample> batch) {
        List<String> texts = batch.stream().map(DatasetSample::getContent).toList();
        AiResult res = null;
        try {
            res = openRouterClient.call(apiKey, modelId, batchSystemPrompt,
                    PromptFactory.batchUserContent(texts));
        } catch (AiProviderException e) {
            if (acc.firstError == null) {
                acc.firstError = e.getMessage();
            }
        }
        if (res != null) {
            Map<Integer, String> byId = parseBatch(res.content(), labels);
            if (!byId.isEmpty()) {
                acc.latencySum += res.latencyMs();
                acc.costSum += res.costUsd();
                for (int i = 0; i < batch.size(); i++) {
                    acc.expected.add(batch.get(i).getExpectedLabel());
                    String pred = byId.get(i + 1);
                    if (pred == null) {
                        acc.errors++;
                    }
                    acc.predicted.add(pred);
                }
                return;
            }
        }
        // the batch response was unusable - fall back to individual calls
        for (DatasetSample sample : batch) {
            processOne(acc, apiKey, modelId, labels, singleSystemPrompt, sample);
        }
    }

    private void processModerationOne(ModAcc acc, String apiKey, String modelId,
                                      Map<String, PolicyAction> verdictByLabel,
                                      String systemPrompt, DatasetSample sample) {
        acc.expectedVerdicts.add(expectedVerdict(verdictByLabel, sample.getExpectedLabel()));
        AiResult ai = null;
        try {
            ai = openRouterClient.call(apiKey, modelId, systemPrompt, sample.getContent());
        } catch (AiProviderException e) {
            acc.errors++;
            if (acc.firstError == null) {
                acc.firstError = e.getMessage();
            }
        }
        if (ai == null) {
            acc.severities.add(Severity.UNKNOWN);
            return;
        }
        acc.latencySum += ai.latencyMs();
        acc.costSum += ai.costUsd();
        Severity severity = SeverityResponseParser
                .parseBatchSeverities(objectMapper, ai.content(), 1).get(1);
        if (severity == null) {
            acc.errors++;
            acc.severities.add(Severity.UNKNOWN);
        } else {
            acc.severities.add(severity);
        }
    }

    private void processModerationBatch(ModAcc acc, String apiKey, String modelId,
                                        Map<String, PolicyAction> verdictByLabel,
                                        String singleSystemPrompt, String batchSystemPrompt,
                                        List<DatasetSample> batch) {
        List<String> texts = batch.stream().map(DatasetSample::getContent).toList();
        AiResult res = null;
        try {
            res = openRouterClient.call(apiKey, modelId, batchSystemPrompt,
                    PromptFactory.batchUserContent(texts));
        } catch (AiProviderException e) {
            if (acc.firstError == null) {
                acc.firstError = e.getMessage();
            }
        }
        if (res != null) {
            Map<Integer, Severity> byId = SeverityResponseParser
                    .parseBatchSeverities(objectMapper, res.content(), batch.size());
            if (!byId.isEmpty()) {
                acc.latencySum += res.latencyMs();
                acc.costSum += res.costUsd();
                for (int i = 0; i < batch.size(); i++) {
                    acc.expectedVerdicts.add(
                            expectedVerdict(verdictByLabel, batch.get(i).getExpectedLabel()));
                    Severity severity = byId.get(i + 1);
                    if (severity == null) {
                        acc.errors++;
                        acc.severities.add(Severity.UNKNOWN);
                    } else {
                        acc.severities.add(severity);
                    }
                }
                return;
            }
        }
        // the batch response was unusable - fall back to individual calls
        for (DatasetSample sample : batch) {
            processModerationOne(acc, apiKey, modelId, verdictByLabel, singleSystemPrompt, sample);
        }
    }

    /** The expected verdict ("ALLOW"/"BLOCK") for a dataset label; defaults to ALLOW if unmapped. */
    private static String expectedVerdict(Map<String, PolicyAction> verdictByLabel, String label) {
        PolicyAction action = verdictByLabel.get(label);
        return action == null ? PolicyAction.ALLOW.name() : action.name();
    }

    private Map<Integer, String> parseBatch(String content, List<String> labels) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (content == null) {
            return out;
        }
        try {
            JsonNode arr = objectMapper.readTree(content);
            if (!arr.isArray()) {
                return out;
            }
            for (JsonNode node : arr) {
                int id = node.path("id").asInt(-1);
                String label = node.path("label").asText(null);
                if (id > 0 && label != null && labels.contains(label)) {
                    out.put(id, label);
                }
            }
        } catch (Exception e) {
            return out; // empty - triggers fallback
        }
        return out;
    }

    private List<List<DatasetSample>> partition(List<DatasetSample> samples, int size) {
        List<List<DatasetSample>> out = new ArrayList<>();
        for (int i = 0; i < samples.size(); i += size) {
            out.add(new ArrayList<>(samples.subList(i, Math.min(i + size, samples.size()))));
        }
        return out;
    }

    private static final class Acc {
        final List<String> expected = new ArrayList<>();
        final List<String> predicted = new ArrayList<>();
        long latencySum;
        double costSum;
        int errors;
        String firstError;
    }

    /** Accumulator for the moderation mode: expected verdicts + the model's severities. */
    private static final class ModAcc {
        final List<String> expectedVerdicts = new ArrayList<>();
        final List<Severity> severities = new ArrayList<>();
        long latencySum;
        double costSum;
        int errors;
        String firstError;
    }

    private String parseLabel(String content, List<String> labels) {
        if (content == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(content);
            String label = node.path("label").asText(null);
            return (label != null && labels.contains(label)) ? label : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<DatasetSample> selectSamples(List<DatasetSample> all, BenchmarkLevel level, long seed) {
        int perClass = level.getSamplesPerClass();
        Map<String, List<DatasetSample>> byLabel = new LinkedHashMap<>();
        for (DatasetSample s : all) {
            byLabel.computeIfAbsent(s.getExpectedLabel(), k -> new ArrayList<>()).add(s);
        }
        Random rng = new Random(seed);
        List<DatasetSample> chosen = new ArrayList<>();
        for (Map.Entry<String, List<DatasetSample>> e : byLabel.entrySet()) {
            List<DatasetSample> list = e.getValue();
            if (list.size() <= perClass) {
                chosen.addAll(list);
            } else {
                Collections.shuffle(list, rng);
                chosen.addAll(list.subList(0, perClass));
            }
        }
        Collections.shuffle(chosen, rng);
        return chosen;
    }

    private String resolveApiKey(BenchmarkRun run) {
        ApiKey key = null;
        if (run.getApiKeyId() != null) {
            key = apiKeyRepository.findById(run.getApiKeyId()).orElse(null);
        }
        if (key == null) {
            key = apiKeyRepository.findByTenantId(PolicyService.DEFAULT_TENANT).stream()
                    .findFirst().orElse(null);
        }
        if (key == null) {
            throw new IllegalStateException(
                    "No OpenRouter API key configured. Create one via POST /api/api-keys.");
        }
        return encryptor.decrypt(key.getEncryptedKey());
    }

    private void markFailed(Long runId) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus(RunStatus.FAILED);
            run.setFinishedAt(Instant.now());
            runRepository.save(run);
        });
    }
}
