package sk.automoder.repository;

import sk.automoder.model.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    List<Policy> findByTenantId(String tenantId);

    List<Policy> findByTenantIdAndActive(String tenantId, boolean active);
}