# Escaped-content artifact repair (2026-11-14)

Container `nexus-fixartifacts-db`; mode: **APPLY**; backup precondition: `E:\nexus-backups\20261114-pre-migrate` (2 file(s)).

Repair set (ADR-0021): `&amp;&amp;` -> `&&`, `&lt;` -> `<`, `&gt;` -> `>`. Standalone `&amp;` is left alone - inside a markdown code span it renders as the literal entity, and unescaping it would be a second data loss. That choice also makes the repair idempotent: a literal `&lt;` typed by an author is stored as `&amp;lt;`, matches no artifact pattern, and is never touched.

| Metric | vibe_post | vibe_comment |
| --- | --- | --- |
| total rows | 7 | 3 |
| rows with artifacts (before) | 4 | 2 |
| rows changed (pass 1) | 4 | 2 |
| rows with artifacts (after) | 0 | 0 |

Idempotence proof: the identical second pass changed **0** rows (vibe_post) and **0** rows (vibe_comment).

## What this does not prove

- Fragments jsoup deleted outright (e.g. `Map<String, Value>` reduced to nothing) are unrecoverable; the repair only unescapes what survived as entities.
- A standalone `&amp;` in a row stays escaped on purpose; those rows still render correctly, so they are not damage.

## Sample rows that carried artifacts (before repair, first 5 per table)

- `vibe_post` id `1`: `a &lt; b and c &gt; d`
- `vibe_post` id `2`: `if (x &lt;= 0) { y = a &amp;&amp; b; }`
- `vibe_post` id `3`: `the generic Map&lt;String, Value&gt; lost its brackets`
- `vibe_post` id `7`: `arrow -&gt; and range 1 &lt; n &lt; 10`
- `vibe_comment` id `101`: `reply: use &amp;&amp; not ||`
- `vibe_comment` id `103`: `nested: a &lt; b &amp;&amp; c &gt; d &amp;&amp; e != f`

