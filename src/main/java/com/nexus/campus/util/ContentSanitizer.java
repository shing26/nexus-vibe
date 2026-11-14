package com.nexus.campus.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Whitelist-based sanitizer for LLM-generated comment content that is written
 * server-side, bypassing the request filter. Keeps safe formatting tags (p,
 * code, pre, lists, tables, links with http(s)/mailto, images with http(s))
 * while stripping scripts, event handlers, javascript: URLs and other
 * dangerous constructs from the model's output before it becomes a comment
 * row — the model is an untrusted dependency (ADR-0003/ADR-0012), and a
 * prompt-injected verdict must not be able to smuggle script into stored
 * content.
 *
 * <p>Since ADR-0021 this class no longer touches inbound request bodies: user
 * content is stored raw and escaped at egress, because jsoup parsed markdown
 * and code as HTML and destroyed it (a generic like {@code Map<String, Value>}
 * was deleted outright, {@code a < b} was stored as {@code a &lt; b}). What
 * remains here guards only the writers that bypass the filter:
 * {@code CodeReviewReviewer}, {@code PromptReviewReviewer} and
 * {@code AgentMentionListener}. Known limitation, recorded in ADR-0021: the
 * safelist is an HTML one, so angle-bracket comparisons inside the model's
 * markdown output are still escaped; that is accepted for LLM prose where the
 * alternative is trusting the model verbatim.</p>
 */
public final class ContentSanitizer {

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("code", "pre", "table", "thead", "tbody", "tr", "th", "td")
            .addAttributes("a", "href", "title", "target")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addProtocols("img", "src", "http", "https");

    private static final Document.OutputSettings OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    private ContentSanitizer() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        return Jsoup.clean(value, "", SAFELIST, OUTPUT_SETTINGS);
    }
}
