package sk.automoder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import sk.automoder.model.LabelRule;

import java.util.List;

/**
 * Request body for {@code PUT /api/datasets/{id}/label-verdicts}: the dataset's full
 * label -&gt; verdict mapping. Each label must be one of the dataset's actual labels.
 */
public record UpdateLabelVerdictsRequest(
        @NotNull(message = "labelVerdicts is required.")
        @Valid List<LabelRule> labelVerdicts
) {
}
