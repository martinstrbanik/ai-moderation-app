package sk.automoder.repository;

import sk.automoder.model.BenchmarkResult;
import sk.automoder.model.BenchmarkRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BenchmarkResultRepository extends JpaRepository<BenchmarkResult, Long> {

    List<BenchmarkResult> findByRun(BenchmarkRun run);
}