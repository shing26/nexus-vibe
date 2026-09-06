package com.nexus.campus.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Shared whitelist-based sanitizer for user/LLM content that may be rendered
 * as HTML later. Keeps safe formatting tags (p, code, pre, lists, tables,
 * links with http(s)/mailto, images with http(s)) while stripping scripts,
 * event handlers, javascript: URLs and other dangerous constructs. Plain
 * text (including Markdown such as {@code a < b}) passes through unchanged,
 * avoiding the double-encoding caused by blanket HTML-escaping.
 *
 * <p>Single source of truth shared by the {@code XssHttpServletRequestWrapper}
 * (inbound request bodies) and any server-side writer that bypasses the
 * filter, e.g. AI agent comments inserted straight through the mapper.</p>
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
