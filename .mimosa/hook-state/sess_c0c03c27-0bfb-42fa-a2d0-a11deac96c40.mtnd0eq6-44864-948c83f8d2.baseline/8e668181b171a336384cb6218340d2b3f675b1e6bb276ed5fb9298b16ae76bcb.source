package com.nexus.campus.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.dto.PostAuditResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * High-performance sensitive-word detection and replacement service built on a
 * <b>Deterministic Finite Automaton (DFA)</b> / trie architecture.
 *
 * <p>Two separate trie structures are maintained internally:</p>
 * <ul>
 *   <li><b>Regular sensitive words</b> &mdash; profanity, insults, etc. Matches are
 *       replaced with {@code [敏感词]} and the result status is {@code PASS}.</li>
 *   <li><b>Critical / political keywords</b> &mdash; triggers a {@code PENDING_AUDIT}
 *       verdict so the content is routed to the admin moderation queue.</li>
 * </ul>
 *
 * <p>Every call to {@link #checkText(String)} runs in <b>O(n)</b> time where
 * {@code n = text.length()}</b>, bounded by the maximum keyword length as a
 * constant factor.</p>
 */
@Service
public class SensitiveWordService implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordService.class);

    private static final String REPLACEMENT = "[敏感词]";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ===== Trie roots =====
    private final TrieNode regularRoot = new TrieNode(WordTier.REGULAR);
    private final TrieNode criticalRoot = new TrieNode(WordTier.CRITICAL);

    // ===== Config injection =====
    @Value("${campus.security.sensitive-words:}")
    private List<String> configuredSensitiveWords;

    @Value("${campus.security.critical-words:}")
    private List<String> configuredCriticalWords;

    // ===== Lifecycle =====
    @PostConstruct
    public void init() {
        loadWords(configuredSensitiveWords, regularRoot);
        loadWords(configuredCriticalWords, criticalRoot);
        loadWords(systemCriticalKeywords(), criticalRoot);

        log.info("[NEXUS-DFA] Regular word trie: {} entries", regularRoot.size());
        log.info("[NEXUS-DFA] Critical word trie: {} entries", criticalRoot.size());
    }

    // ===== Public API =====
    public PostAuditResult checkText(String text) {
        if (text == null || text.isEmpty()) {
            return PostAuditResult.pass(text);
        }

        String lowerText = text.toLowerCase();
        StringBuilder result = new StringBuilder(text.length());
        List<String> matchedKeywords = new ArrayList<>();
        boolean hasRegular = false;
        boolean hasCritical = false;

        int i = 0;
        while (i < text.length()) {
            MatchResult regMatch   = findLongestMatch(lowerText, i, regularRoot);
            MatchResult critMatch  = findLongestMatch(lowerText, i, criticalRoot);

            MatchResult best = pickBest(regMatch, critMatch);

            if (best != null) {
                result.append(REPLACEMENT);
                i = best.start + best.length;

                if (!matchedKeywords.contains(best.keyword)) {
                    matchedKeywords.add(best.keyword);
                }

                if (best.tier == WordTier.CRITICAL) {
                    hasCritical = true;
                } else {
                    hasRegular = true;
                }
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }

        String filteredContent = result.toString();

        if (hasCritical) {
            log.warn("[NEXUS-DFA] CRITICAL keywords detected: {} - content routed to audit queue.",
                    matchedKeywords);
            return PostAuditResult.pendingAudit(filteredContent, matchedKeywords.size(), matchedKeywords);
        }
        if (hasRegular) {
            log.debug("[NEXUS-DFA] Regular sensitive words detected: {} - content filtered.",
                    matchedKeywords);
            return PostAuditResult.sensitiveOnly(filteredContent, matchedKeywords.size(), matchedKeywords);
        }
        return PostAuditResult.pass(filteredContent);
    }

    // ===== Trie helpers =====
    private MatchResult findLongestMatch(String text, int start, TrieNode root) {
        TrieNode node = root;
        MatchResult best = null;

        for (int j = start; j < text.length(); j++) {
            char c = text.charAt(j);
            node = node.children.get(c);
            if (node == null) break;
            if (node.isEnd) {
                best = new MatchResult(start, j - start + 1, node.keyword, node.tier);
            }
        }
        return best;
    }

    private MatchResult pickBest(MatchResult a, MatchResult b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.length == b.length) {
            return (a.tier == WordTier.CRITICAL) ? a : b;
        }
        return (a.length > b.length) ? a : b;
    }

    // ===== Redis Pub/Sub listener =====
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            log.info("[NEXUS-DFA] Received sensitive-word update: {}", body);

            List<String> newWords = objectMapper.readValue(body, new TypeReference<List<String>>() {});
            reloadSensitiveWords(newWords);
            log.info("[NEXUS-DFA] DFA trie reloaded with {} word(s) from Redis Pub/Sub", newWords.size());
        } catch (Exception e) {
            log.error("[NEXUS-DFA] Failed to process Redis Pub/Sub message: {}", e.getMessage(), e);
        }
    }

    /**
     * Atomically replace the regular sensitive-word trie with a new word list.
     */
    public void reloadSensitiveWords(List<String> newWords) {
        synchronized (regularRoot) {
            regularRoot.children.clear();
            loadWords(newWords, regularRoot);
        }
    }

    // ===== Word-list loading =====
    private void loadWords(List<String> words, TrieNode root) {
        if (words == null) return;
        for (String raw : words) {
            String word = raw.trim().toLowerCase();
            if (word.isEmpty() || word.length() < 2) continue;
            addWord(word, root);
        }
    }

    private void addWord(String word, TrieNode root) {
        TrieNode node = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            node = node.children.computeIfAbsent(c, k -> new TrieNode(root.tier));
        }
        node.isEnd = true;
        node.keyword = word;
        root.incrementSize();
    }

    // ===== System-level critical keywords =====
    private static List<String> systemCriticalKeywords() {
        return Arrays.asList(
                "赌博",
                "毒品",
                "暴力",
                "色情",
                "诈骗",
                "枪支",
                "恐怖主义",
                "违禁品"
        );
    }

    // ===== =====
    //  Inner types
    // ===== =====
    private enum WordTier { REGULAR, CRITICAL }

    private static class MatchResult {
        final int start;
        final int length;
        final String keyword;
        final WordTier tier;

        MatchResult(int start, int length, String keyword, WordTier tier) {
            this.start = start;
            this.length = length;
            this.keyword = keyword;
            this.tier = tier;
        }
    }

    private static class TrieNode {
        final WordTier tier;
        final Map<Character, TrieNode> children = new HashMap<>();
        boolean isEnd = false;
        String keyword = null;
        private int subtreeSize = 0;

        TrieNode(WordTier tier) { this.tier = tier; }

        void incrementSize() { subtreeSize++; }
        int size() { return subtreeSize; }
    }
}
