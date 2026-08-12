package sk.automoder.dto;

import sk.automoder.model.Dataset;

import java.time.Instant;

public record DatasetResponse(
        Long id,
        String name,
        String description,
        String source,
        long sampleCount,
        Instant createdAt
) {
    public static DatasetResponse of(Dataset dataset, long sampleCount) {
        return new DatasetResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                dataset.getSource(),
                sampleCount,
                dataset.getCreatedAt()
        );
    }
}