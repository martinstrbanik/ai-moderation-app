package sk.automoder.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sk.automoder.dto.BenchmarkResultResponse;
import sk.automoder.dto.BenchmarkRunResponse;
import sk.automoder.dto.CreateBenchmarkRequest;
import sk.automoder.service.BenchmarkService;

import java.util.List;

@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @GetMapping("/runs")
    public List<BenchmarkRunResponse> listRuns() {
        return benchmarkService.list();
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BenchmarkRunResponse createRun(@Valid @RequestBody CreateBenchmarkRequest request) {
        return benchmarkService.create(request);
    }

    @GetMapping("/runs/{id}")
    public BenchmarkRunResponse getRun(@PathVariable Long id) {
        return benchmarkService.getById(id);
    }

    @GetMapping("/runs/{id}/results")
    public List<BenchmarkResultResponse> getResults(@PathVariable Long id) {
        return benchmarkService.results(id);
    }
}