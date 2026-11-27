package com.nexus.campus.agent;

import com.nexus.campus.util.ContentSanitizer;

/**
 * The single presentation path for a verdict. Direct reviewers and the service
 * reviewer call the same renderer, so a formatting change cannot make the two
 * modes drift.
 */
public final class VerdictRenderer {

    private VerdictRenderer() {
    }

    public static String renderCode(Reviewer.Verdict verdict) {
        String quality = ContentSanitizer.clean(verdict.quality);
        String security = ContentSanitizer.clean(verdict.security);
        String suggestions = ContentSanitizer.clean(verdict.suggestions);

        StringBuilder sb = new StringBuilder();
        sb.append("**Overall Score**: ").append(verdict.score).append("/10\n\n");
        sb.append("**Severity**: ").append(verdict.severity).append("\n\n");
        sb.append("**Verdict**: ").append(verdict.approved ? "Approved" : "Needs Attention").append("\n\n");

        if (quality != null && !quality.isBlank()) {
            sb.append("### Code Quality\n").append(quality).append("\n\n");
        }
        if (security != null && !security.isBlank()) {
            sb.append("### Security\n").append(security).append("\n\n");
        }
        if (suggestions != null && !suggestions.isBlank()) {
            sb.append("### Suggestions\n").append(suggestions).append("\n");
        }
        sb.append("\n---\n*Score guide: 9-10 production-ready · 7-8 solid, minor issues · "
                  + "5-6 functional with notable gaps · 3-4 significant problems · 0-2 broken/unsafe. "
                  + "Edited posts are re-reviewed automatically; the latest score wins.*\n");
        return sb.toString();
    }

    public static String renderPrompt(Reviewer.Verdict verdict) {
        String quality = ContentSanitizer.clean(verdict.quality);
        String security = ContentSanitizer.clean(verdict.security);
        String suggestions = ContentSanitizer.clean(verdict.suggestions);

        StringBuilder sb = new StringBuilder();
        sb.append("**Overall Score**: ").append(verdict.score).append("/10\n\n");
        sb.append("**Severity**: ").append(verdict.severity).append("\n\n");
        sb.append("**Verdict**: ").append(verdict.approved ? "Approved" : "Needs Attention").append("\n\n");

        if (quality != null && !quality.isBlank()) {
            sb.append("### Structure & Clarity\n").append(quality).append("\n\n");
        }
        if (security != null && !security.isBlank()) {
            sb.append("### Safety & Misuse\n").append(security).append("\n\n");
        }
        if (suggestions != null && !suggestions.isBlank()) {
            sb.append("### Reproducibility\n").append(suggestions).append("\n");
        }
        sb.append("\n---\n*Score guide: 9-10 exemplary prompt craft · 7-8 solid, minor gaps · "
                  + "5-6 workable with notable weaknesses · 3-4 fragile or underspecified · 0-2 unusable. "
                  + "Edited prompts are re-reviewed automatically; the latest score wins.*\n");
        return sb.toString();
    }
}
