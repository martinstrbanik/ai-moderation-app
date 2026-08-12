package sk.automoder.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.automoder.dto.ModelRequest;
import sk.automoder.dto.ModelResponse;
import sk.automoder.exception.ConflictException;
import sk.automoder.exception.NotFoundException;
import sk.automoder.model.AiModel;
import sk.automoder.model.ModelType;
import sk.automoder.repository.AiModelRepository;

import java.util.List;

@Service
public class AiModelService {

    private final AiModelRepository repository;

    public AiModelService(AiModelRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ModelResponse> list(ModelType type, Boolean enabled) {
        List<AiModel> models;
        if (type == null && enabled == null) {
            models = repository.findAll();
        } else if (type != null && enabled != null) {
            models = repository.findByTypeAndEnabled(type, enabled);
        } else if (type != null) {
            models = repository.findByType(type);
        } else {
            models = repository.findAll().stream().filter(m -> m.isEnabled() == enabled).toList();
        }
        return models.stream().map(ModelResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ModelResponse getById(Long id) {
        return ModelResponse.from(requireModel(id));
    }

    @Transactional
    public ModelResponse create(ModelRequest request) {
        if (repository.existsByModelId(request.modelId())) {
            throw new ConflictException("Model with id " + request.modelId() + " already exists.");
        }
        AiModel model = new AiModel();
        apply(model, request);
        return ModelResponse.from(repository.save(model));
    }

    @Transactional
    public ModelResponse update(Long id, ModelRequest request) {
        AiModel model = requireModel(id);
        if (!model.getModelId().equals(request.modelId())
                && repository.existsByModelId(request.modelId())) {
            throw new ConflictException("Model with id " + request.modelId() + " already exists.");
        }
        apply(model, request);
        return ModelResponse.from(repository.save(model));
    }

    @Transactional
    public void delete(Long id) {
        AiModel model = requireModel(id);
        // models are reference data - prefer disabling them over deleting;
        // if deleted, the record is removed
        repository.delete(model);
    }

    private void apply(AiModel model, ModelRequest request) {
        model.setModelId(request.modelId());
        model.setName(request.name());
        model.setType(request.type());
        model.setEnabled(request.enabled());
    }

    public AiModel requireModel(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Model", id));
    }
}