package sk.automoder.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.automoder.dto.BenchmarkResultResponse;
import sk.automoder.dto.BenchmarkRunResponse;
import sk.automoder.dto.CreateBenchmarkRequest;
import sk.automoder.exception.BadRequestException;
import sk.automoder.exception.NotFoundException;
import sk.automoder.model.AiModel;
import sk.automoder.model.BenchmarkMode;
import sk.automoder.model.BenchmarkResult;
import sk.automoder.model.BenchmarkRun;
import sk.automoder.model.Dataset;
import sk.automoder.model.Policy;
import sk.automoder.model.RunStatus;
import sk.automoder.repository.BenchmarkResultRepository;
import sk.automoder.repository.BenchmarkRunRepository;

import java.util.List;

@Service
public class BenchmarkService {

    private final BenchmarkRunRepository runRepository;
    private final BenchmarkResultRepository resultRepository;
    private final DatasetService datasetService;
    private final PolicyService policyService;
    private final AiModelService modelService;
    private final BenchmarkExecutor benchmarkExecutor;

    public BenchmarkService(BenchmarkRunRepository runRepository,
                            BenchmarkResultRepository resultRepository,
                            DatasetService datasetService,
                            PolicyService policyService,
                            AiModelService modelService,
                            BenchmarkExecutor benchmarkExecutor) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.datasetService = datasetService;
        this.policyService = policyService;
        this.modelService = modelService;
        this.benchmarkExecutor = benchmarkExecutor;
    }

    // NOTE: intentionally not @Transactional - the async executor must be able
    // to see the persisted run immediately (each save commits its own transaction).
    public BenchmarkRunResponse create(CreateBenchmarkRequest request) {
        Dataset dataset = datasetService.requireDataset(request.datasetId());
        Policy policy = null;
        if (request.policyId() != null) {
            policy = policyService.requirePolicy(request.policyId());
        }
        BenchmarkMode mode = request.mode() == null ? BenchmarkMode.CLASSIFICATION : request.mode();
        if (mode == BenchmarkMode.MODERATION) {
            requireModerationPrerequisites(dataset);
        }
        List<AiModel> models = request.modelIds().stream().map(modelService::requireModel).toList();

        BenchmarkRun run = new BenchmarkRun();
        run.setTenantId(PolicyService.DEFAULT_TENANT);
        run.setDataset(dataset);
        run.setPolicy(policy);
        run.setMode(mode);
        run.setLevel(request.level());
        run.setBatchSize(request.batchSize());
        run.setApiKeyId(request.apiKeyId());
        run.setStatus(RunStatus.PENDING);
        run = runRepository.save(run);

        for (AiModel model : models) {
            BenchmarkResult result = new BenchmarkResult();
            result.setTenantId(PolicyService.DEFAULT_TENANT);
            result.setRun(run);
            result.setModel(model);
            result.setProcessedSamples(0);
            resultRepository.save(result);
        }

        benchmarkExecutor.execute(run.getId());
        return BenchmarkRunResponse.from(run, List.of());
    }

    /**
     * A moderation benchmark needs a complete dataset label -&gt; verdict mapping: it derives
     * both the severity prompt's categories and the expected verdicts from the dataset.
     * Fails fast with 400 otherwise. A policy is not used.
     */
    private void requireModerationPrerequisites(Dataset dataset) {
        List<String> missing = datasetService.missingVerdictLabels(dataset);
        if (!missing.isEmpty()) {
            throw new BadRequestException("Dataset '" + dataset.getName()
                    + "' is missing ALLOW/BLOCK mappings for labels: " + missing
                    + ". Set them via PUT /api/datasets/" + dataset.getId() + "/label-verdicts.");
        }
    }

    @Transactional(readOnly = true)
    public List<BenchmarkRunResponse> list() {
        return runRepository.findByTenantId(PolicyService.DEFAULT_TENANT).stream()
                .map(r -> BenchmarkRunResponse.from(r, resultRepository.findWithModelByRun(r)))
                .toList();
    }

    @Transactional(readOnly = true)
    public BenchmarkRunResponse getById(Long id) {
        BenchmarkRun run = requireRun(id);
        return BenchmarkRunResponse.from(run, resultRepository.findWithModelByRun(run));
    }

    @Transactional(readOnly = true)
    public List<BenchmarkResultResponse> results(Long id) {
        BenchmarkRun run = requireRun(id);
        return resultRepository.findWithModelByRun(run).stream()
                .map(BenchmarkResultResponse::from)
                .toList();
    }

    private BenchmarkRun requireRun(Long id) {
        return runRepository.findDetailedById(id)
                .orElseThrow(() -> NotFoundException.of("Benchmark run", id));
    }
}