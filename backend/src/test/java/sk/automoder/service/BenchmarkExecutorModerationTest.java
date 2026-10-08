package sk.automoder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sk.automoder.ai.AiResult;
import sk.automoder.ai.OpenRouterClient;
import sk.automoder.model.AiModel;
import sk.automoder.model.ApiKey;
import sk.automoder.model.BenchmarkLevel;
import sk.automoder.model.BenchmarkMode;
import sk.automoder.model.BenchmarkResult;
import sk.automoder.model.BenchmarkRun;
import sk.automoder.model.Dataset;
import sk.automoder.model.DatasetSample;
import sk.automoder.model.LabelRule;
import sk.automoder.model.ModelType;
import sk.automoder.model.PolicyAction;
import sk.automoder.model.RunStatus;
import sk.automoder.model.Severity;
import sk.automoder.repository.ApiKeyRepository;
import sk.automoder.repository.BenchmarkResultRepository;
import sk.automoder.repository.BenchmarkRunRepository;
import sk.automoder.repository.DatasetSampleRepository;
import sk.automoder.security.AesGcmEncryptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BenchmarkExecutor} with mocked dependencies (no DB / no HTTP).
 * Focuses on the new MODERATION mode (severity -&gt; verdict + the threshold sweep) and
 * keeps a sanity check on the CLASSIFICATION mode after the executor refactor.
 */
class BenchmarkExecutorModerationTest {

    private static final String MODEL_ID = "openai/gpt-4o-mini";
    private static final long RUN_ID = 10L;

    private BenchmarkRunRepository runRepository;
    private BenchmarkResultRepository resultRepository;
    private DatasetSampleRepository sampleRepository;
    private ApiKeyRepository apiKeyRepository;
    private AesGcmEncryptor encryptor;
    private OpenRouterClient client;
    private BenchmarkExecutor executor;

    private Dataset dataset;
    private BenchmarkRun run;
    private BenchmarkResult result;

    @BeforeEach
    void setUp() {
        runRepository = mock(BenchmarkRunRepository.class);
        resultRepository = mock(BenchmarkResultRepository.class);
        sampleRepository = mock(DatasetSampleRepository.class);
        apiKeyRepository = mock(ApiKeyRepository.class);
        encryptor = mock(AesGcmEncryptor.class);
        client = mock(OpenRouterClient.class);
        executor = new BenchmarkExecutor(runRepository, resultRepository, sampleRepository,
                apiKeyRepository, encryptor, client, new ObjectMapper());

        dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("tuke_slovak");
        dataset.setLabelVerdicts(List.of(
                new LabelRule("hate", PolicyAction.BLOCK),
                new LabelRule("not_hate", PolicyAction.ALLOW)));

        run = new BenchmarkRun();
        run.setId(RUN_ID);
        run.setTenantId(PolicyService.DEFAULT_TENANT);
        run.setDataset(dataset);
        run.setLevel(BenchmarkLevel.EXTRA_LIGHT);
        run.setBatchSize(1);
        run.setStatus(RunStatus.PENDING);

        AiModel model = new AiModel();
        model.setId(3L);
        model.setModelId(MODEL_ID);
        model.setName(MODEL_ID);
        model.setType(ModelType.TEXT);

        result = new BenchmarkResult();
        result.setId(1L);
        result.setTenantId(PolicyService.DEFAULT_TENANT);
        result.setRun(run);
        result.setModel(model);

        ApiKey key = new ApiKey();
        key.setEncryptedKey("enc");

        when(runRepository.findDetailedById(RUN_ID)).thenReturn(Optional.of(run));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(resultRepository.findWithModelByRun(run)).thenReturn(List.of(result));
        when(resultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(apiKeyRepository.findByTenantId(PolicyService.DEFAULT_TENANT)).thenReturn(List.of(key));
        when(encryptor.decrypt("enc")).thenReturn("test-key");
    }

    private static DatasetSample sample(String content, String label) {
        DatasetSample s = new DatasetSample();
        s.setContent(content);
        s.setExpectedLabel(label);
        return s;
    }

    private void stubClassifierByContent() {
        when(client.call(anyString(), anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String user = inv.getArgument(3);
            return new AiResult(user.contains("toxic")
                    ? "{\"severity\":\"HIGH\",\"categories\":[1],\"reason\":\"bad\"}"
                    : "{\"severity\":\"NONE\",\"categories\":[],\"reason\":\"ok\"}", 0.001, 15L);
        });
    }

    @Test
    void moderationModeReportsOnlyTheThresholdSweep() {
        run.setMode(BenchmarkMode.MODERATION);
        stubClassifierByContent();
        when(sampleRepository.findByDataset(dataset)).thenReturn(List.of(
                sample("toxic remark", "hate"),
                sample("friendly remark", "not_hate")));

        executor.execute(RUN_ID);

        // a moderation run has no single operating point: the scalar columns stay null
        assertNull(result.getAccuracy());
        assertNull(result.getPrecision());
        assertNull(result.getRecall());
        assertNull(result.getF1());
        assertEquals(0, result.getErrorCount());

        // the sweep (the deliverable) is present for every threshold
        assertNotNull(result.getThresholdMetrics());
        assertEquals(4, result.getThresholdMetrics().size());
        // threshold MODERATE: HIGH -> BLOCK, NONE -> ALLOW -> both correct
        assertEquals(1.0, result.getThresholdMetrics().get(Severity.MODERATE).accuracy());
        assertEquals(1.0, result.getThresholdMetrics().get(Severity.HIGH).accuracy());
        // threshold NONE: the NONE sample blocks too -> one wrong -> accuracy 0.5
        assertEquals(0.5, result.getThresholdMetrics().get(Severity.NONE).accuracy());
    }

    @Test
    void classificationModeStillClassifiesIntoDatasetLabels() {
        run.setMode(BenchmarkMode.CLASSIFICATION);
        when(client.call(anyString(), anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String user = inv.getArgument(3);
            return new AiResult(user.contains("toxic")
                    ? "{\"label\":\"hate\"}" : "{\"label\":\"not_hate\"}", 0.001, 15L);
        });
        when(sampleRepository.findByDataset(dataset)).thenReturn(List.of(
                sample("toxic remark", "hate"),
                sample("friendly remark", "not_hate")));

        executor.execute(RUN_ID);

        assertEquals(1.0, result.getAccuracy());
        assertEquals(0, result.getErrorCount());
        // classification mode does not produce a threshold sweep
        assertNull(result.getThresholdMetrics());
    }

    @Test
    void moderationCategoriesComeFromBlockLabelsOnly() {
        run.setMode(BenchmarkMode.MODERATION);
        stubClassifierByContent();
        when(sampleRepository.findByDataset(dataset)).thenReturn(List.of(
                sample("toxic remark", "hate"),
                sample("friendly remark", "not_hate")));

        executor.execute(RUN_ID);

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce())
                .call(anyString(), anyString(), systemPrompt.capture(), anyString());

        // the prompt lists only the BLOCK label as a category, never the ALLOW label
        String prompt = systemPrompt.getValue();
        assertTrue(prompt.contains("1. hate"));
        assertFalse(prompt.contains("not_hate"));
    }
}
