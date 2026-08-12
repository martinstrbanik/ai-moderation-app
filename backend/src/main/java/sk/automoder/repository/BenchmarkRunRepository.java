package sk.automoder.repository;

import sk.automoder.model.BenchmarkResult;
import sk.automoder.model.BenchmarkRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {

    List<BenchmarkRun> findByTenantId(String tenantId);

    List<BenchmarkRun> findByTenantIdAndPublishedTrue(String tenantId);

    @EntityGraph(attributePaths = {"dataset", "policy"})
    Optional<BenchmarkRun> findDetailedById(Long id);
}