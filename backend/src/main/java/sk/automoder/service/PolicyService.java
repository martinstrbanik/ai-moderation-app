package sk.automoder.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.automoder.dto.PolicyRequest;
import sk.automoder.dto.PolicyResponse;
import sk.automoder.exception.NotFoundException;
import sk.automoder.model.Policy;
import sk.automoder.repository.PolicyRepository;

import java.util.List;

@Service
public class PolicyService {

    public static final String DEFAULT_TENANT = "default";

    private final PolicyRepository repository;

    public PolicyService(PolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> list(Boolean active) {
        List<Policy> policies = active == null
                ? repository.findByTenantId(DEFAULT_TENANT)
                : repository.findByTenantIdAndActive(DEFAULT_TENANT, active);
        return policies.stream().map(PolicyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PolicyResponse getById(Long id) {
        return PolicyResponse.from(requirePolicy(id));
    }

    @Transactional
    public PolicyResponse create(PolicyRequest request) {
        Policy policy = new Policy();
        policy.setTenantId(DEFAULT_TENANT);
        apply(policy, request);
        return PolicyResponse.from(repository.save(policy));
    }

    @Transactional
    public PolicyResponse update(Long id, PolicyRequest request) {
        Policy policy = requirePolicy(id);
        policy.setVersion(policy.getVersion() + 1);
        apply(policy, request);
        return PolicyResponse.from(repository.save(policy));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(requirePolicy(id));
    }

    @Transactional
    public PolicyResponse setActive(Long id, boolean active) {
        Policy policy = requirePolicy(id);
        if (policy.isActive() != active) {
            policy.setActive(active);
            policy.setVersion(policy.getVersion() + 1);
        }
        return PolicyResponse.from(repository.save(policy));
    }

    private void apply(Policy policy, PolicyRequest request) {
        policy.setName(request.name());
        policy.setDescription(request.description());
        policy.setCategories(request.categories());
        policy.setRules(request.rules());
        policy.setThreshold(request.threshold());
        policy.setAction(request.action());
        policy.setModelId(request.modelId());
        policy.setFallbackModelId(request.fallbackModelId());
        policy.setActive(request.active());
    }

    public Policy requirePolicy(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Policy", id));
    }
}