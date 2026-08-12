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
import sk.automoder.model.BenchmarkResult;
import sk.automoder.model.BenchmarkRun;
import sk.automoder.model.DatasetSample;
import sk.automoder.model.RunStatus;
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

/**
 * Executes a benchmark run asynchronously: classifies dataset samples with each
 * requested model and computes precision/recall/F1/accuracy, latency and cost.
 */
@Component
public class BenchmarkExecutor {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkExecutor.class);

    /** How often (in samples) progress is logged and persisted. */
    private static final int PROGRESS_EVERY = 25;

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

        List<DatasetSample> all = sampleRepository.findByDataset(run.getDataset());
        List<String> labels = all.stream()
                .map(DatasetSample::getExpectedLabel).distinct().sorted().toList();
        List<DatasetSample> samples = selectSamples(all, run.getLevel(), runId);

        String apiKeyPlain = resolveApiKey(run);
        String singleSystemPrompt = PromptFactory.classificationSystemPrompt(labels);
        String batchSystemPrompt = PromptFactory.classificationBatchSystemPrompt(labels);
        int effectiveBatchSize = run.getBatchSize() != null ? run.getBatchSize() : batchSize;

        List<BenchmarkResult> results = resultRepository.findWithModelByRun(run);
        for (BenchmarkResult result : results) {
            AiModel model = result.getModel();
            log.info("Benchmark run {}: starting model {} on {} samples (batch-size={}).",
                    runId, model.getModelId(), samples.size(), effectiveBatchSize);
            evaluate(samples, labels, apiKeyPlain, model.getModelId(),
                    singleSystemPrompt, batchSystemPrompt, effectiveBatchSize, result);
            result.setProcessedSamples(samples.size());
            resultRepository.save(result);
            log.info("Benchmark run {}: model {} -> P={} R={} F1={} acc={} ({} samples, {} errors)",
                    runId, model.getModelId(),
                    String.format("%.3f", result.getPrecision()),
                    String.format("%.3f", result.getRecall()),
                    String.format("%.3f", result.getF1()),
                    String.format("%.3f", result.getAccuracy()),
                    samples.size(), result.getErrorCount());
        }

        run.setStatus(RunStatus.COMPLETED);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);
        log.info("Benchmark run {} completed ({} models, {} samples).",
                runId, results.size(), samples.size());
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
        int perClass = switch (level) {
            case EXTRA_LIGHT -> 50;
            case LIGHT -> 500;
            case FULL -> Integer.MAX_VALUE;
        };
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
