package sk.automoder.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.automoder.exception.BadRequestException;
import sk.automoder.model.Dataset;
import sk.automoder.model.LabelRule;
import sk.automoder.model.PolicyAction;
import sk.automoder.repository.DatasetRepository;
import sk.automoder.repository.DatasetSampleRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the label -&gt; verdict mapping methods of {@link DatasetService}
 * (validation and completeness), with mocked repositories.
 */
class DatasetServiceLabelVerdictsTest {

    private DatasetRepository datasetRepository;
    private DatasetSampleRepository sampleRepository;
    private DatasetService service;

    private Dataset dataset;

    @BeforeEach
    void setUp() {
        datasetRepository = mock(DatasetRepository.class);
        sampleRepository = mock(DatasetSampleRepository.class);
        service = new DatasetService(datasetRepository, sampleRepository);

        dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("tuke_slovak");

        when(datasetRepository.findById(1L)).thenReturn(Optional.of(dataset));
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sampleRepository.findDistinctLabels(dataset)).thenReturn(List.of("hate", "not_hate"));
    }

    @Test
    void acceptsValidCompleteMapping() {
        Dataset saved = service.setLabelVerdicts(1L, List.of(
                new LabelRule("hate", PolicyAction.BLOCK),
                new LabelRule("not_hate", PolicyAction.ALLOW)));

        assertEquals(2, saved.getLabelVerdicts().size());
        assertEquals(PolicyAction.BLOCK, saved.verdictMap().get("hate"));
    }

    @Test
    void rejectsUnknownLabel() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                service.setLabelVerdicts(1L, List.of(new LabelRule("nonsense", PolicyAction.BLOCK))));
        assertEquals(true, ex.getMessage().contains("Unknown label"));
    }

    @Test
    void rejectsNullVerdictAndBlankLabel() {
        assertThrows(BadRequestException.class, () ->
                service.setLabelVerdicts(1L, List.of(new LabelRule("hate", null))));
        assertThrows(BadRequestException.class, () ->
                service.setLabelVerdicts(1L, List.of(new LabelRule("  ", PolicyAction.BLOCK))));
    }

    @Test
    void rejectsDuplicateLabel() {
        assertThrows(BadRequestException.class, () -> service.setLabelVerdicts(1L, List.of(
                new LabelRule("hate", PolicyAction.BLOCK),
                new LabelRule("hate", PolicyAction.ALLOW))));
    }

    @Test
    void missingVerdictLabelsReportsUnmappedLabels() {
        dataset.setLabelVerdicts(List.of(new LabelRule("hate", PolicyAction.BLOCK)));

        assertEquals(List.of("not_hate"), service.missingVerdictLabels(dataset));
    }

    @Test
    void missingVerdictLabelsIsEmptyWhenComplete() {
        dataset.setLabelVerdicts(List.of(
                new LabelRule("hate", PolicyAction.BLOCK),
                new LabelRule("not_hate", PolicyAction.ALLOW)));

        assertEquals(List.of(), service.missingVerdictLabels(dataset));
    }
}
