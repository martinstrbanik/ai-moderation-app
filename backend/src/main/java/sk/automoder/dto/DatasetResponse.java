package sk.automoder.dto;

import sk.automoder.model.Dataset;
import sk.automoder.model.LabelRule;

import java.time.Instant;
import java.util.List;

public record DatasetResponse(
        Long id,
        String name,
        String description,
        String source,
        List<String> labels,
        List<LabelRule> labelVerdicts,
        long sampleCount,
        Instant createdAt
) {
    public static DatasetResponse of(Dataset dataset, List<String> labels, long sampleCount) {
        return new DatasetResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                dataset.getSource(),
                labels,
                dataset.getLabelVerdicts() == null ? List.of() : dataset.getLabelVerdicts(),
                sampleCount,
                dataset.getCreatedAt()
        );
    }
}