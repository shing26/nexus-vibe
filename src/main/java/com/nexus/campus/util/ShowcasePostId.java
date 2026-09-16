package com.nexus.campus.util;

/**
 * Reads {@code campus.showcase.post-id}.
 *
 * <p>Blank means "no showcase", and so does unparseable: a typo in an environment variable
 * should leave the landing page exactly as it was, not point it at post id 0. Both the
 * seeder and the read endpoint go through this so they cannot disagree about whether a
 * deployment has a showcase at all — the seeder writing a post the endpoint refuses to
 * report, or the reverse, is the kind of split-brain that only shows up in production.</p>
 */
public final class ShowcasePostId {

    /** Deterministic id for the seeded post: outside the range the snowflake generator issues. */
    public static final long DEFAULT = 900000000000000001L;

    private ShowcasePostId() {
    }

    public static Long parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
