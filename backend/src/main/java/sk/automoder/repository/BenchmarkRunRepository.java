package sk.automoder.repository;

import sk.automoder.model.BenchmarkRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {

    List<BenchmarkRun> findByTenantId(String tenantId);

    List<BenchmarkRun> findByTenantIdAndPublishedTrue(String tenantId);
}