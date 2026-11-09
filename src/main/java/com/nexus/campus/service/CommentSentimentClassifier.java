package com.nexus.campus.service;

import com.nexus.campus.enums.CommentSentiment;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic three-way comment classification: a fixed keyword heuristic,
 * not a model's judgement.
 *
 * <p>Pure by construction: no network, no LLM, no repository, no Spring bean,
 * no mutable state. It reads one string and returns one code, so the label can
 * never be the thing that goes dark when the model does.
 *
 * <p>Precedence, highest first — the first rule that matches wins:
 * <ol>
 *   <li>null or blank text → {@link CommentSentiment#OTHER};</li>
 *   <li>question — the text ends with {@code ?}/{@code ？}, or contains an
 *       interrogative cue → {@link CommentSentiment#QUESTION}. A question
 *       outranks praise and complaint cues: "Great post! Which model did you
 *       use?" needs an answer, and the answer is what the thread is for;</li>
 *   <li>more distinct complaint cues than praise cues → COMPLAINT; the
 *       reverse → PRAISE;</li>
 *   <li>equal counts, including both zero → {@link CommentSentiment#OTHER}.
 *       A tie is not a coin flip — calling it "other" keeps the label
 *       honest.</li>
 * </ol>
 *
 * <p>Counting is over <em>distinct cue families</em>: inflections of one word
 * ({@code bug}/{@code bugs}, {@code fail}/{@code failed}) are one family and
 * count once, so repeating a word cannot outweigh a single mention of its
 * opposite. The split is approximate by design and is labelled as a keyword
 * heuristic wherever it is shown.
 */
public final class CommentSentimentClassifier {

    /** Trailing question mark, allowing whitespace after it. */
    private static final Pattern QUESTION_END = Pattern.compile("[?？]\\s*$");

    private static final List<List<Pattern>> QUESTION_CUES = List.of(
            family("how", "why", "what", "when", "where", "which", "who"),
            family("can you", "could you", "would you", "do you", "did you", "have you", "are you"),
            family("is it", "any idea", "anyone"),
            family("怎么", "为什么", "如何", "什么", "是否", "能否", "请问", "有没有", "吗"));

    private static final List<List<Pattern>> COMPLAINT_CUES = List.of(
            family("broken"),
            family("bug", "bugs", "buggy"),
            family("crash", "crashes", "crashed"),
            family("error", "errors"),
            family("fail", "fails", "failed", "failing", "failure"),
            family("wrong", "bad", "terrible", "awful", "poor", "worst", "ugly"),
            family("hate"),
            family("disappointed", "disappointing"),
            family("confusing", "confused", "unclear", "messy"),
            family("frustrating", "frustrated", "annoying", "annoyed"),
            family("useless", "sucks", "waste"),
            family("slow"),
            family("doesn't work", "does not work", "not working", "won't work"),
            family("problem", "problems"),
            family("issue", "issues"),
            family("missing", "outdated"),
            family("错误", "失败", "崩溃", "垃圾", "糟糕", "问题", "不行", "不能用", "太慢", "很差", "难受"));

    private static final List<List<Pattern>> PRAISE_CUES = List.of(
            family("great", "good", "nice", "cool", "solid"),
            family("thanks", "thank you", "appreciate"),
            family("awesome", "amazing", "excellent", "fantastic", "wonderful", "brilliant", "perfect"),
            family("helpful", "useful", "insightful", "impressive", "beautiful"),
            family("love", "loves", "loved"),
            family("well done"),
            family("recommend"),
            family("棒", "很好", "不错", "感谢", "谢谢", "有用", "优秀", "精彩", "厉害", "赞", "清晰", "推荐"));

    private CommentSentimentClassifier() {
    }

    /**
     * @param text the comment text as it will be stored
     * @return the code of the matching {@link CommentSentiment}; never null
     */
    public static int classify(String text) {
        if (text == null || text.isBlank()) {
            return CommentSentiment.OTHER.getCode();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (QUESTION_END.matcher(normalized).find() || matchesAny(QUESTION_CUES, normalized)) {
            return CommentSentiment.QUESTION.getCode();
        }
        int complaints = countMatches(COMPLAINT_CUES, normalized);
        int praise = countMatches(PRAISE_CUES, normalized);
        if (complaints > praise) {
            return CommentSentiment.COMPLAINT.getCode();
        }
        if (praise > complaints) {
            return CommentSentiment.PRAISE.getCode();
        }
        return CommentSentiment.OTHER.getCode();
    }

    private static boolean matchesAny(List<List<Pattern>> families, String text) {
        return families.stream().anyMatch(family -> matches(family, text));
    }

    private static int countMatches(List<List<Pattern>> families, String text) {
        int count = 0;
        for (List<Pattern> family : families) {
            if (matches(family, text)) {
                count++;
            }
        }
        return count;
    }

    private static boolean matches(List<Pattern> family, String text) {
        return family.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    private static List<Pattern> family(String... forms) {
        return Arrays.stream(forms).map(CommentSentimentClassifier::cue).toList();
    }

    /**
     * ASCII cues match on word boundaries ("good" must not fire inside
     * "goodness"); Chinese cues have no such boundary, so they match as
     * substrings.
     */
    private static Pattern cue(String cue) {
        boolean ascii = cue.chars().allMatch(c -> c < 128);
        String quoted = Pattern.quote(cue);
        return Pattern.compile(ascii ? "\\b" + quoted + "\\b" : quoted);
    }
}
