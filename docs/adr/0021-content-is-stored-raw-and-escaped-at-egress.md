# 0021 — Content is stored raw and escaped at egress

For years every JSON request body passed through a jsoup
(`Safelist.relaxed`) wash on its way into the application: `XssFilter` wrapped
each request in `XssHttpServletRequestWrapper`, which parsed the body as JSON,
ran every string value through `ContentSanitizer.clean`, and re-serialized the
tree. The intent was XSS defense at the door. The effect was silent destruction
of the very content this forum exists to carry. jsoup parses values as HTML
fragments, so a Java generic was read as an unknown tag and deleted
(`Map<String, Value>` was stored as nothing), a comparison was read as markup
(`a < b && c > d` was stored as `a &lt; b &amp;&amp; c &gt; d`), and the
corruption was permanent: it poisoned the AI review input, the search index and
the summary column, and re-saving an edited post re-normalized the entities
along the same path. The live database held the proof — post id
`2096843254191030274` ("angle test") stored `a &lt; b and c &gt; d` against the
author's plain text. The 2026-11-14 external review classified this P0, and
correctly so: for a code-focused forum this is storage-layer data loss, not a
rendering quirk. (`docs/tickets/readme-visual-front-door.md` had recorded the
same symptom at lines 72-89 but filed it as a display defect; to the extent
that document classifies this as a front-door rendering problem, this ADR
supersedes that classification — the document itself is left as written,
because a record of what was believed at the time is worth keeping.)

## Decision

**Ingress is byte-faithful; egress escapes per surface.**

1. `XssHttpServletRequestWrapper` no longer rewrites JSON request bodies at
   all. The body is cached once and passed through verbatim — no JSON
   parse/re-serialize (which had also normalized whitespace and key order),
   no jsoup, no escape fallback. Markdown, code, generics and even hostile
   strings reach the controllers exactly as the client sent them. The escaped
   non-JSON body path and the `HtmlUtils.htmlEscape` on query parameters and
   headers are unchanged: those are short reflected-value surfaces where
   escape-on-input costs nothing and defends a path (log lines, redirects,
   downstream string interpolation) that has no markdown renderer.
2. The XSS defense for stored content moves to where content becomes markup:
   the SPA renders post bodies and comments through react-markdown, which by
   default parses content as markdown and escapes raw HTML — a stored
   `<script>alert(1)</script>` renders as inert text. The frontend is not
   changed in this round; it already provides the guarantee the new ingress
   stance relies on. Server-side, there is no surface that interpolates user
   content into HTML: the egress inventory below is the audit this claim rests
   on.
3. `ContentSanitizer` stays, with its contract narrowed. After the wrapper
   stopped calling it, its remaining callers are all server-side writers of
   LLM-generated content that bypass the request filter: `CodeReviewReviewer`,
   `PromptReviewReviewer` (verdict prose turned into AI review comments) and
   `AgentMentionListener` (agent replies). There the model is the untrusted
   input — the project's own thesis — and a prompt-injected verdict must not
   be able to smuggle script into a comment row. jsoup remains in `pom.xml`;
   the dependency is still doing that job. Known limitation, accepted and
   recorded: the safelist is an HTML one, so angle-bracket comparisons inside
   the model's markdown output are still escaped. That is the price of not
   trusting the model verbatim, and it damages only LLM prose, not user
   content.

## Egress inventory (the audit behind claim 2)

Every path by which stored user or agent content reaches a consumer, and how
it is made safe:

| Surface | Content | Escaping |
| --- | --- | --- |
| SPA post body / post detail | user markdown | react-markdown (raw HTML escaped by default) |
| SPA comment threads | user + AI-agent markdown | react-markdown via `CommentBody.tsx` |
| SPA edit / create forms | user markdown round-trip | react-markdown preview; values bound as React text nodes |
| REST API responses | all content | JSON serialization — data, never markup; the SPA owns rendering |
| `PostCreationService.summary()` | derived plain-text excerpt | strips `<[^>]*>` with a regex for the excerpt; the result is stored and served as JSON text, never interpolated into HTML — not an HTML output surface |
| AI review comments | LLM markdown | written server-side through `ContentSanitizer.clean` (see decision 3) |
| Agent mention replies | LLM markdown | same, via `AgentMentionListener` |
| Backend error/limit responses (`JwtAuthFilter`, `RateLimitInterceptor`) | fixed server-owned strings | JSON literals, no user content |

No backend controller or service writes user content into an HTML document;
the service produces JSON and the SPA renders it. That division is what makes
raw storage safe.

## Round-trip contract

`XssWrapperJsonBodyRoundTripTest` pins the frozen acceptance口径: the six
inputs `<`, `&`, `&&`, `Map<String, Value>`, a fenced code block and
`<script>alert(1)</script>` all traverse the wrapper byte-for-byte — the
attack string included. Storage does no HTML rewriting; `<script>` is stored
raw *on purpose*, and its safety is an egress property (react-markdown's
default escaping), not a storage property. `XssFilterTest`'s JSON cases were
rewritten from "scripts are stripped" to "bodies are stored verbatim" for the
same reason; those rewritten assertions are the ones criterion 9 of the
contract requires to be accounted for.

## Rejected

- **Keep jsoup at ingress with a per-field whitelist exemption** (content
  passes, other fields sanitized): the exemption list rots as fields grow, and
  comments and `prompt_metadata` carry code too. One defense at egress beats a
  scattered one at ingress.
- **Unescape stored artifacts in the renderer instead** (make the SPA decode
  `&lt;` back): rendering-layer compatibility with a storage defect cures the
  defect into a permanent contract, corrupts posts that legitimately contain
  entities, and belongs to a rendering round that is explicitly out of scope
  here.
- **Delete `ContentSanitizer` outright**: its remaining callers guard
  LLM-generated content inserted server-side, a real (if narrower) threat the
  class is the only guard for.
- **Fix only the display** (store escaped, unescape at read): would have left
  AI review input, search and summaries permanently poisoned and required
  every reader of the column to know the encoding.

## What this does not prove, and what remains

- The stored-artifact repair for rows already written under the old ingress is
  an operations step, not part of this ADR: the idempotent script and its
  rehearsal live in `benchmark/migrations/`, and it must run only after a
  verified backup, per its own header. The repair is deliberately narrower
  than the damage — entities alone (`&lt;`, `&gt;`, `&&`) are unescaped;
  fragments jsoup deleted outright (`Map<String, Value>` with the brackets
  consumed) are unrecoverable and stay lost.
- The terminal-notification hole this round also fixed (a post whose final
  degraded attempt returned normally never told its author that retries had
  stopped) is recorded in `docs/tickets/core-loop-corrections.md`, not here —
  it is a notification-path fix, not a content-handling decision.
- One residual, accepted: if the terminal attempt's own DB write fails, the
  reconciliation sweep may later retire the row and send the terminal
  notification a second time. Every notification path is best-effort by
  design; arbitrating this edge would cost a conditional-update protocol for a
  case that requires a write failure followed by a sweep success.
