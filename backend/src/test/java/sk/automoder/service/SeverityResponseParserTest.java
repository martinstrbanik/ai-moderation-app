package sk.automoder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import sk.automoder.model.Severity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SeverityResponseParser}: severity string parsing and the
 * batch/single response parsing used by both moderation and the moderation benchmark.
 */
class SeverityResponseParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseSeverityHandlesCaseAndUnknownValues() {
        assertEquals(Severity.HIGH, SeverityResponseParser.parseSeverity("high"));
        assertEquals(Severity.MODERATE, SeverityResponseParser.parseSeverity("  MODERATE "));
        assertEquals(Severity.NONE, SeverityResponseParser.parseSeverity(null));
        assertEquals(Severity.NONE, SeverityResponseParser.parseSeverity("banana"));
    }

    @Test
    void parsesBatchArrayKeyedByPosition() {
        String json = "[{\"id\":1,\"severity\":\"HIGH\"},{\"id\":2,\"severity\":\"NONE\"}]";

        Map<Integer, Severity> out = SeverityResponseParser.parseBatchSeverities(mapper, json, 2);

        assertEquals(Severity.HIGH, out.get(1));
        assertEquals(Severity.NONE, out.get(2));
    }

    @Test
    void ignoresOutOfRangeIdsAndMissingPositions() {
        // id 5 is out of range for a 2-text batch; position 2 is absent from the response
        String json = "[{\"id\":1,\"severity\":\"LOW\"},{\"id\":5,\"severity\":\"HIGH\"}]";

        Map<Integer, Severity> out = SeverityResponseParser.parseBatchSeverities(mapper, json, 2);

        assertEquals(1, out.size());
        assertEquals(Severity.LOW, out.get(1));
        assertNull(out.get(2));
    }

    @Test
    void parsesSingleObjectForSingleTextBatch() {
        String json = "{\"severity\":\"MODERATE\",\"categories\":[],\"reason\":\"x\"}";

        Map<Integer, Severity> out = SeverityResponseParser.parseBatchSeverities(mapper, json, 1);

        assertEquals(Severity.MODERATE, out.get(1));
    }

    @Test
    void unparseableContentYieldsEmptyMap() {
        assertTrue(SeverityResponseParser.parseBatchSeverities(mapper, "not json", 3).isEmpty());
        assertTrue(SeverityResponseParser.parseBatchSeverities(mapper, null, 3).isEmpty());
        // a single object is not accepted for a multi-text batch
        assertTrue(SeverityResponseParser
                .parseBatchSeverities(mapper, "{\"severity\":\"HIGH\"}", 2).isEmpty());
    }
}
