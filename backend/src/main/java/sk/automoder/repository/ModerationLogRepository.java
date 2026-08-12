package sk.automoder.repository;

import sk.automoder.model.ModerationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationLogRepository extends JpaRepository<ModerationLog, Long> {

    List<ModerationLog> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}