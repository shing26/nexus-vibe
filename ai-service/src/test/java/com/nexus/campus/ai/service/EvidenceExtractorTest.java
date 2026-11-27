package com.nexus.campus.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.ai.api.dto.ReviewResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void findsAQuoteAndReportsOneBasedLineNumbers() throws Exception {
        String content = "first line of the post\nsecond line has the bug\nthird line is fine";

        EvidenceExtractor.Result result = extract(
                List.of("second line has the bug"), content, List.of());

        assertEquals(1, result.accepted().size());
        ReviewResponse.Evidence evidence = result.accepted().get(0);
        assertEquals("second line has the bug", evidence.quote());
        assertEquals(2, evidence.startLine());
        assertEquals(2, evidence.endLine());
        assertEquals(0, result.rejected());
    }

    @Test
    void aReflowedMultiLineQuoteKeepsTheSourceSpan() throws Exception {
        String content = "header text here\nint value = 1;\nreturn value;\nfooter";

        EvidenceExtractor.Result result = extract(
                List.of("int value = 1;   return value;"), content, List.of());

        assertEquals(1, result.accepted().size());
        ReviewResponse.Evidence evidence = result.accepted().get(0);
        assertEquals("int value = 1;\nreturn value;", evidence.quote());
        assertEquals(2, evidence.startLine());
        assertEquals(3, evidence.endLine());
    }

    @Test
    void returnsTheSourceTextNotTheModelsWhitespace() throws Exception {
        String content = "alpha  beta gamma delta";

        EvidenceExtractor.Result result = extract(List.of("alpha beta gamma"), content, List.of());

        assertEquals("alpha  beta gamma", result.accepted().get(0).quote());
    }

    @Test
    void rejectsAFabricatedQuoteAndCountsIt() throws Exception {
        EvidenceExtractor.Result result = extract(
                List.of("this sentence never appears in the post"), "the post says something else", List.of());

        assertTrue(result.accepted().isEmpty());
        assertEquals(1, result.rejected());
    }

    @Test
    void rejectsBlankShortAndDuplicateQuotes() throws Exception {
        String content = "the real quote lives on this line";

        EvidenceExtractor.Result result = extract(
                List.of(" ", "too short", "the real quote lives on this line",
                        "the real quote lives on this line"),
                content, List.of());

        assertEquals(1, result.accepted().size());
        assertEquals(3, result.rejected());
    }

    @Test
    void matchesASnippetWhenTheContentDoesNotContainIt() throws Exception {
        String content = "prose only, the code is attached separately";
        List<String> snippets = List.of("int total = 0;\nreturn total;");

        EvidenceExtractor.Result result = extract(List.of("return total;"), content, snippets);

        assertEquals(1, result.accepted().size());
        assertEquals("return total;", result.accepted().get(0).quote());
        assertEquals(2, result.accepted().get(0).startLine());
    }

    @Test
    void keepsAtMostFiveQuotes() throws Exception {
        String content = String.join("\n",
                "quote number one here", "quote number two here", "quote number three here",
                "quote number four here", "quote number five here", "quote number six here");
        List<String> quotes = List.of(
                "quote number one here", "quote number two here", "quote number three here",
                "quote number four here", "quote number five here", "quote number six here");

        EvidenceExtractor.Result result = extract(quotes, content, List.of());

        assertEquals(5, result.accepted().size());
        assertEquals(1, result.rejected());
        assertEquals(5, result.accepted().get(4).startLine());
    }

    @Test
    void missingOrNonArrayEvidenceIsEmptyNotAnError() throws Exception {
        EvidenceExtractor.Result missing = EvidenceExtractor.extract(
                MAPPER.readTree("{\"score\":5}"), "content", List.of());
        EvidenceExtractor.Result wrongType = EvidenceExtractor.extract(
                MAPPER.readTree("{\"evidence\":\"one string\"}"), "content", List.of());

        assertTrue(missing.accepted().isEmpty());
        assertEquals(0, missing.rejected());
        assertTrue(wrongType.accepted().isEmpty());
        assertEquals(0, wrongType.rejected());
    }

    @Test
    void ignoresNonTextualEntriesWithoutCountingThemAsQuotes() throws Exception {
        JsonNode node = MAPPER.readTree("{\"evidence\":[7,\"a real quote of ten+\"]}");

        EvidenceExtractor.Result result = EvidenceExtractor.extract(node, "a real quote of ten+", List.of());

        assertEquals(1, result.accepted().size());
        assertEquals(0, result.rejected());
    }

    private static EvidenceExtractor.Result extract(
            List<String> quotes, String content, List<String> snippets) throws Exception {
        String json = MAPPER.writeValueAsString(java.util.Map.of("evidence", quotes));
        return EvidenceExtractor.extract(MAPPER.readTree(json), content, snippets);
    }
}
