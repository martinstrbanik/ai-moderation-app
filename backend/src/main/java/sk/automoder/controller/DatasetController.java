package sk.automoder.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.automoder.dto.DatasetResponse;
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
                .map(d -> DatasetResponse.of(d, datasetService.sampleCount(d)))
                .toList();
    }

    @GetMapping("/{id}")
    public DatasetResponse get(@PathVariable Long id) {
        Dataset dataset = datasetService.getById(id);
        return DatasetResponse.of(dataset, datasetService.sampleCount(dataset));
    }
}