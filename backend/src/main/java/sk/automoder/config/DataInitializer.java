package sk.automoder.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import sk.automoder.model.AiModel;
import sk.automoder.model.ModelType;
import sk.automoder.repository.AiModelRepository;

import java.util.List;

/**
 * Seeds reference AI models (OpenRouter slugs) on first startup.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AiModelRepository repository;

    public DataInitializer(AiModelRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            log.info("DataInitializer: AiModel already has records, seed skipped.");
            return;
        }
        List<AiModel> seeds = List.of(
                seed("google/gemini-2.0-flash", "Gemini 2.0 Flash", ModelType.VISION),
                seed("openai/gpt-4o-mini", "GPT-4o mini", ModelType.TEXT),
                seed("openai/gpt-4o", "GPT-4o", ModelType.VISION),
                seed("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", ModelType.VISION)
        );
        repository.saveAll(seeds);
        log.info("DataInitializer: {} models seeded.", seeds.size());
    }

    private AiModel seed(String modelId, String name, ModelType type) {
        AiModel model = new AiModel();
        model.setModelId(modelId);
        model.setName(name);
        model.setType(type);
        model.setEnabled(true);
        return model;
    }
}