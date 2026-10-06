package sk.automoder.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.automoder.dto.PolicyRequest;
import sk.automoder.dto.PolicyResponse;
import sk.automoder.exception.BadRequestException;
import sk.automoder.exception.NotFoundException;
import sk.automoder.model.Category;
import sk.automoder.model.Policy;
import sk.automoder.model.Severity;
import sk.automoder.repository.PolicyRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        if (request.thresholdSeverity() == Severity.UNKNOWN) {
            throw new BadRequestException(
                    "thresholdSeverity must be one of NONE, LOW, MODERATE, HIGH.");
        }
        policy.setName(request.name());
        policy.setDescription(request.description());
        policy.setCategories(validateCategories(request.categories()));
        policy.setThresholdSeverity(request.thresholdSeverity());
        policy.setModelId(request.modelId());
        policy.setFallbackModelId(request.fallbackModelId());
        policy.setActive(request.active());
    }

    /**
     * Validates the policy's categories: each must have a non-blank {@code id} (unique)
     * and a non-blank {@code prompt}. A {@code null} list is normalised to an empty one.
     */
    private List<Category> validateCategories(List<Category> categories) {
        if (categories == null) {
            return new ArrayList<>();
        }
        Set<String> seenIds = new HashSet<>();
        List<Category> out = new ArrayList<>(categories.size());
        for (Category category : categories) {
            if (category == null
                    || category.id() == null || category.id().isBlank()
                    || category.prompt() == null || category.prompt().isBlank()) {
                throw new BadRequestException(
                        "Each category must have a non-blank 'id' and 'prompt'.");
            }
            String id = category.id().trim();
            if (!seenIds.add(id)) {
                throw new BadRequestException("Duplicate category id: " + id + ".");
            }
            out.add(new Category(id, category.prompt().trim()));
        }
        return out;
    }

    public Policy requirePolicy(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Policy", id));
    }
}