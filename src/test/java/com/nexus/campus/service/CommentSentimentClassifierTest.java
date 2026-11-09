package com.nexus.campus.service;

import com.nexus.campus.enums.CommentSentiment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the keyword heuristic's documented precedence (see
 * {@link CommentSentimentClassifier}). These are the only rules the split
 * claims to follow; a change here is a change to what the UI's filter label
 * means.
 */
class CommentSentimentClassifierTest {

    private static final int OTHER = CommentSentiment.OTHER.getCode();
    private static final int QUESTION = CommentSentiment.QUESTION.getCode();
    private static final int COMPLAINT = CommentSentiment.COMPLAINT.getCode();
    private static final int PRAISE = CommentSentiment.PRAISE.getCode();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    @DisplayName("null, empty and whitespace-only text is other")
    void emptyIsOther(String text) {
        assertEquals(OTHER, CommentSentimentClassifier.classify(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Which embedding model did you use?",
            "Have you tried parent-child chunking？",
            "Is it worth the extra latency ?",
            "How do you handle retries"
    })
    @DisplayName("a trailing question mark or an interrogative cue is a question")
    void questions(String text) {
        assertEquals(QUESTION, CommentSentimentClassifier.classify(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "The build is broken on Windows.",
            "This crashed twice and threw a confusing error.",
            "Useless, slow and frustrating.",
            "部署失败了，构建崩溃。"
    })
    @DisplayName("negative cues with no stronger signal are a complaint")
    void complaints(String text) {
        assertEquals(COMPLAINT, CommentSentimentClassifier.classify(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Great walkthrough, thanks for sharing.",
            "Really useful and insightful, well done.",
            "写得很好，感谢分享。"
    })
    @DisplayName("positive cues with no stronger signal are praise")
    void praise(String text) {
        assertEquals(PRAISE, CommentSentimentClassifier.classify(text));
    }

    @Test
    @DisplayName("precedence: a question outranks praise and complaint cues")
    void questionOutranksEverything() {
        // Praise + question: the thread needs an answer more than a thank-you.
        assertEquals(QUESTION, CommentSentimentClassifier.classify("Great post! Which model did you use?"));
        // Complaint + question: still a question; the answer may resolve it.
        assertEquals(QUESTION, CommentSentimentClassifier.classify("The build failed. How do I fix it?"));
    }

    @Test
    @DisplayName("precedence: complaint and praise are counted per cue family; more families wins")
    void cueFamilyCountDecides() {
        // One complaint family (broken) vs two praise families (great, helpful).
        assertEquals(PRAISE, CommentSentimentClassifier.classify("Great and helpful, though the demo is broken."));
        // One praise family (thanks) vs two complaint families (bug, crash).
        assertEquals(COMPLAINT, CommentSentimentClassifier.classify("Thanks, but the bug makes it crash."));
    }

    @Test
    @DisplayName("a tie between complaint and praise falls to other, as does no cue at all")
    void tiesAndSilenceAreOther() {
        // One family each: great (praise) vs broken (complaint).
        assertEquals(OTHER, CommentSentimentClassifier.classify("Great idea, but broken."));
        assertEquals(OTHER, CommentSentimentClassifier.classify("I read this twice."));
    }

    @Test
    @DisplayName("repeating one cue does not outweigh a single opposite cue")
    void repetitionDoesNotBreakTies() {
        assertEquals(OTHER, CommentSentimentClassifier.classify("bug bug bug, but great"));
    }

    @Test
    @DisplayName("cues match whole words, not fragments inside longer words")
    void cuesAreWordBounded() {
        // "goodness" contains "good" and "baddest" contains "bad"; neither is a cue.
        assertEquals(OTHER, CommentSentimentClassifier.classify("The goodness of this approach is hard to judge."));
    }

    @Test
    @DisplayName("the classifier is a pure function: the same text yields the same code")
    void deterministic() {
        String text = "Thanks! This failed though. Which flag fixes it?";
        assertEquals(CommentSentimentClassifier.classify(text), CommentSentimentClassifier.classify(text));
    }
}
