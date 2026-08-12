package sk.automoder.repository;

import sk.automoder.model.AiModel;
import sk.automoder.model.ModelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    Optional<AiModel> findByModelId(String modelId);

    boolean existsByModelId(String modelId);

    List<AiModel> findByType(ModelType type);

    List<AiModel> findByTypeAndEnabled(ModelType type, boolean enabled);
}