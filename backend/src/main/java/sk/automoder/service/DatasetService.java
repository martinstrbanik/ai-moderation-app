package sk.automoder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.automoder.exception.NotFoundException;
import sk.automoder.model.Dataset;
import sk.automoder.model.DatasetSample;
import sk.automoder.repository.DatasetRepository;
import sk.automoder.repository.DatasetSampleRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Imports prepared JSONL datasets (data/processed/...) into the database and
 * provides read access for the benchmark module.
 */
@Service
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);

    private final DatasetRepository datasetRepository;
    private final DatasetSampleRepository sampleRepository;

    @Value("${automoder.datasets.path}")
    private String datasetsPath;

    public DatasetService(DatasetRepository datasetRepository, DatasetSampleRepository sampleRepository) {
        this.datasetRepository = datasetRepository;
        this.sampleRepository = sampleRepository;
    }

    /**
     * Idempotent import of all prepared datasets (one subdirectory per dataset
     * containing labeled_data.jsonl). Skips datasets that are already imported.
     */
    @Transactional
    public void importIfNeeded() {
        Path root = Path.of(datasetsPath);
        if (!Files.isDirectory(root)) {
            log.warn("DatasetService: datasets path '{}' is not a directory, import skipped.", datasetsPath);
            return;
        }
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(this::importDirectory);
        } catch (IOException e) {
            log.warn("DatasetService: cannot list datasets path '{}'.", datasetsPath, e);
        }
    }

    private void importDirectory(Path dir) {
        String name = dir.getFileName().toString();
        if (datasetRepository.findByName(name).isPresent()) {
            return;
        }
        Path jsonl = dir.resolve("labeled_data.jsonl");
        if (!Files.isRegularFile(jsonl)) {
            return;
        }

        Dataset dataset = new Dataset();
        dataset.setName(name);
        dataset.setDescription("Prepared benchmark dataset: " + name);
        dataset.setSource(readMetaSource(dir, name));
        Dataset saved = datasetRepository.save(dataset);

        List<DatasetSample> samples = new ArrayList<>();
        try (Stream<String> lines = Files.lines(jsonl, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (line.isBlank()) {
                    return;
                }
                // format: {"id":..., "text":"...", "label":"...", "label_id":...}
                String text = extractText(line);
                String label = extractLabel(line);
                if (text == null || label == null) {
                    return;
                }
                DatasetSample sample = new DatasetSample();
                sample.setDataset(saved);
                sample.setContent(text);
                sample.setExpectedLabel(label);
                samples.add(sample);
            });
        } catch (IOException e) {
            log.warn("DatasetService: cannot read {}.", jsonl, e);
            return;
        }
        sampleRepository.saveAll(samples);
        log.info("DatasetService: dataset '{}' imported with {} samples.", name, samples.size());
    }

    private String readMetaSource(Path dir, String fallback) {
        Path meta = dir.resolve("meta.json");
        if (Files.isRegularFile(meta)) {
            try {
                String content = Files.readString(meta, StandardCharsets.UTF_8);
                int i = content.indexOf("\"source_url\"");
                if (i >= 0) {
                    int start = content.indexOf('"', content.indexOf(':', i) + 1);
                    int end = content.indexOf('"', start + 1);
                    if (start >= 0 && end > start) {
                        return content.substring(start + 1, end);
                    }
                }
            } catch (IOException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private String extractText(String line) {
        int s = line.indexOf("\"text\": \"");
        if (s < 0) {
            return null;
        }
        s += "\"text\": \"".length();
        int e = line.indexOf('"', s);
        return e < 0 ? null : unescape(line.substring(s, e));
    }

    private String extractLabel(String line) {
        int s = line.indexOf("\"label\": \"");
        if (s < 0) {
            return null;
        }
        s += "\"label\": \"".length();
        int e = line.indexOf('"', s);
        return e < 0 ? null : line.substring(s, e);
    }

    private String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
    }

    @Transactional(readOnly = true)
    public List<Dataset> list() {
        return datasetRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Dataset getById(Long id) {
        return datasetRepository.findById(id).orElseThrow(() -> NotFoundException.of("Dataset", id));
    }

    @Transactional(readOnly = true)
    public long sampleCount(Dataset dataset) {
        return sampleRepository.countByDataset(dataset);
    }

    public Dataset requireDataset(Long id) {
        return getById(id);
    }
}