package sk.automoder.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.automoder.dto.DatasetResponse;
import sk.automoder.dto.UpdateLabelVerdictsRequest;
import sk.automoder.model.Dataset;
import sk.automoder.service.DatasetService;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public List<DatasetResponse> list() {
        return datasetService.list().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public DatasetResponse get(@PathVariable Long id) {
        return toResponse(datasetService.getById(id));
    }

    /**
     * Replaces the dataset's label -&gt; verdict (ALLOW/BLOCK) mapping used by the
     * moderation benchmark. The dataset's labels are exposed via {@code GET /api/datasets/{id}}.
     */
    @PutMapping("/{id}/label-verdicts")
    public DatasetResponse updateLabelVerdicts(@PathVariable Long id,
                                               @Valid @RequestBody UpdateLabelVerdictsRequest request) {
        return toResponse(datasetService.setLabelVerdicts(id, request.labelVerdicts()));
    }

    private DatasetResponse toResponse(Dataset dataset) {
        return DatasetResponse.of(dataset,
                datasetService.distinctLabels(dataset),
                datasetService.sampleCount(dataset));
    }
}