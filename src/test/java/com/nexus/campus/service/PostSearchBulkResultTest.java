package com.nexus.campus.service;

import com.nexus.campus.entity.VibePost;
import com.nexus.campus.service.PostSearchService.BulkResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for what {@link PostSearchService} reports after a bulk index.
 *
 * <p>The behaviour under test is the reporting, not Elasticsearch: {@code _bulk} answers HTTP 200
 * with failed items inside the body, and the previous implementation returned the size of the list
 * it had been handed regardless. A restore runbook step that reads "reindexed: 32" over an empty
 * index is worse than no step at all, so every count here has to come from the response body.</p>
 */
class PostSearchBulkResultTest {

    private final PostSearchService service = new PostSearchService();

    private static List<VibePost> posts(int n) {
        List<VibePost> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            VibePost post = new VibePost();
            post.setId((long) i);
            list.add(post);
        }
        return list;
    }

    @Test
    @DisplayName("_bulk 200 with every item accepted counts as submitted")
    void countAccepted_allOk() {
        String body = "{\"took\":5,\"errors\":false,\"items\":["
                + "{\"index\":{\"_id\":\"1\",\"status\":201}},"
                + "{\"index\":{\"_id\":\"2\",\"status\":200}}]}";

        assertEquals(2, service.countAcceptedItems(body, 2));
    }

    @Test
    @DisplayName("_bulk 200 with errors:true counts only the items that succeeded")
    void countAccepted_partialFailureIsNotReportedAsSuccess() {
        String body = "{\"took\":5,\"errors\":true,\"items\":["
                + "{\"index\":{\"_id\":\"1\",\"status\":201}},"
                + "{\"index\":{\"_id\":\"2\",\"status\":429,\"error\":{\"type\":\"es_rejected_execution_exception\"}}},"
                + "{\"index\":{\"_id\":\"3\",\"status\":400,\"error\":{\"type\":\"mapper_parsing_exception\"}}}]}";

        assertEquals(1, service.countAcceptedItems(body, 3));
    }

    @Test
    @DisplayName("every item failing yields zero, not the submitted count")
    void countAccepted_allFailed() {
        String body = "{\"took\":5,\"errors\":true,\"items\":["
                + "{\"index\":{\"_id\":\"1\",\"status\":503}}]}";

        assertEquals(0, service.countAcceptedItems(body, 1));
    }

    @Test
    @DisplayName("An unreadable body verifies nothing and therefore indexes nothing")
    void countAccepted_failsClosed() {
        assertEquals(0, service.countAcceptedItems("not json at all", 7));
        assertEquals(0, service.countAcceptedItems("{\"acknowledged\":true}", 7));
        assertEquals(0, service.countAcceptedItems("null", 7));
    }

    @Test
    @DisplayName("A body claiming more items than were submitted cannot inflate the count")
    void countAccepted_cappedAtSubmitted() {
        String body = "{\"items\":["
                + "{\"index\":{\"status\":201}},{\"index\":{\"status\":201}},"
                + "{\"index\":{\"status\":201}}]}";

        assertEquals(2, service.countAcceptedItems(body, 2));
    }

    @Test
    @DisplayName("With ES absent the report says so instead of echoing the row count")
    void rebuildIndex_withoutEsRefusesEverything() {
        ReflectionTestUtils.setField(service, "esAvailable", false);

        BulkResult result = service.rebuildIndex(posts(32));

        assertEquals(32, result.submitted());
        assertEquals(0, result.indexed());
        assertEquals(32, result.failed());
        assertFalse(result.complete(), "an index Elasticsearch never saw is not complete");
    }

    @Test
    @DisplayName("bulkIndex with ES absent refuses instead of returning early in silence")
    void bulkIndex_withoutEsReportsRefused() {
        ReflectionTestUtils.setField(service, "esAvailable", false);

        assertEquals(BulkResult.refused(3), service.bulkIndex(posts(3)));
    }

    @Test
    @DisplayName("An empty or null batch is nothing to index, not a failure")
    void bulkIndex_emptyBatch() {
        assertEquals(BulkResult.nothingToIndex(), service.bulkIndex(Collections.emptyList()));
        assertEquals(BulkResult.nothingToIndex(), service.bulkIndex(null));
        assertTrue(service.bulkIndex(Collections.emptyList()).complete());
    }

    @Test
    @DisplayName("complete() requires every submitted document to come back accepted")
    void bulkResult_completeness() {
        assertTrue(new BulkResult(5, 5, 0).complete());
        assertFalse(new BulkResult(5, 4, 1).complete());
        assertFalse(new BulkResult(5, 0, 5).complete());
    }
}
