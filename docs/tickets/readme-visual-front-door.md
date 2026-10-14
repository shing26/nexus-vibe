# The README gets a visual front door

Source: this round (2026-10-14). The request was a visual pass on the repository's front door:
capture the React 19 + Tailwind community surface, make the asynchronous code-review state the
subject, draw a Redis-Lua data-consistency comparison under high concurrency, and put the system
in one native Mermaid diagram. The brief also named a literal label -
`AI Code Review: Passed (Score: 92)` - and a left-card / right-status composition.

## What the brief asked for, and what the tree could prove

| Brief | Delivered | Why |
|---|---|---|
| A dark-mode capture at the score-92 pass state | A dark-mode capture of a real review: *Full-jitter retries without the thundering herd*, scored 7/10 → 70/100, severity medium, `Approved` | No run in this repository has produced a 92. The highest score anywhere in the tree is 9/10 in the H2 demo seed (`src/main/resources/data.sql`), a hand-written fixture rather than a recorded run, and its `result_json` carries no findings text, so its terminal renders three empty sections. Painting a 92 into a screenshot is the class of artifact [ADR-0010](../adr/0010-the-showcase-review-is-a-recording.md) exists to prevent |
| Code card on the left, status badge on the right | The application's actual layout: code card full-width, score badge and review terminal below it | The product is single-column. Composing a two-column image would advertise a layout the app does not have |
| A Redis-Lua high-concurrency consistency chart | A measured figure: the shipped `like_toggle.lua` against a naive control, 50 threads, 30 rounds | Delivered from a run, not an illustration - see the probe below |

The substitution rule is the repository's own: a claim without a reproducible path does not go in,
and a screenshot of a state that never happened is such a claim.

## Scope

Documentation and assets, plus one new reproducible probe. No change under `src/` or `frontend/`.
`README.md` gains a hero capture, a native Mermaid architecture diagram, and the consistency
figure; `docs/assets/readme/` holds the three images.

## The images

| Asset | Produced by | What it shows |
|---|---|---|
| `docs/assets/readme/async-review.png` | headless Chrome against the local dev stack (H2, Redis off, Ollama `qwen2.5:7b`), 2026-10-14 | A published post: code card, five real likes, the AI review terminal replaying a completed run - four steps, `Approved`, 70/100, and the three findings |
| `docs/assets/readme/community-feed.png` | the same dev stack | The dark-mode community surface: mission control, the channel rail, the hot-ranked feed |
| `docs/assets/readme/lua-consistency.png` | `benchmark/concurrency/render-lua-consistency-chart.py` over the probe JSON | The naive-vs-Lua consistency comparison |

The hero is a **local dev-stack run**, not the deployed stack, and the caption in the README says
so. The review is real output: the post went through the ordinary publish path and the pipeline
wrote the log row back; nothing in the image is hand-written model prose.

## The consistency probe

`benchmark/concurrency/lua-vs-naive-consistency.py` loads the shipped
`src/main/resources/lua/like_toggle.lua` and runs it against a faithful control that performs the
same steps as separate commands. Both paths get two bursts against a live Redis:

* **distinct** - 50 users like once each. The set is exact by construction, so both paths agree.
* **same-user** - 50 concurrent toggles for one user, i.e. a double-submit or a retry storm.

The invariant is `zscore == scard * weight`: every member of the like-set must contribute exactly
one signed weight to the hot-ranking ZSET. A round that breaks it is a round where the two
structures disagree about the same like.

Measured 2026-10-14 against a standalone redis-server 5.0.14.1, 50 threads, 30 rounds, weight 3:

| Path | Distinct users | Same user, 50 concurrent toggles |
|---|---|---|
| Lua atomic script | scard=50, zscore=150, consistent | **0/30 rounds drifted**, max abs drift 0 |
| Naive round trips | scard=50, zscore=150, consistent | **29/30 rounds drifted**, max abs drift 54 |

The distinct-user leg is the control on the control: it shows the difference is atomicity, not the
set design - when two callers touch different members both paths are correct.

### What this run does not show

- It is a Redis-only probe. It does not exercise the HTTP like path, the MySQL membership table or
  `LikeSyncTask`; `benchmark/concurrency/like-concurrency.ps1` covers the end-to-end three-view
  agreement on the full stack.
- The committed run is redis-server 5.0.14.1, not the compose stack's Redis 7. Command execution
  is single-threaded and Lua atomicity is unchanged across those versions, but the exact numbers
  are this build's, and they are not cited in the README's evidence wall for that reason.
- The naive path is a control, not code the application ships. It is the counterfactual, not a
  regression.

## Discovered while shooting the hero, not fixed

The first hero candidate was a Java snippet containing `<`, `>` and `&`. The rendered code card
showed `&lt;`, `&gt;` and `&amp;&amp;` instead of the operators. The API response for that post
confirms the entities are stored, not merely displayed: `GET /api/v1/posts/2110285844466618370`
returned `if (bytes &lt; 0) {`.

`XssFilter` wraps every request in `XssHttpServletRequestWrapper`, whose documented job is to
HTML-escape URL parameters and headers and to whitelist-sanitize JSON bodies with jsoup. A fenced
code block inside a post body is content, not markup, but it goes through the same path, so its
operators are escaped before storage and react-markdown then renders the entities literally inside
the code fence. The blog note in
[llm-code-review-structured-output-injection-defense.md](../blog/llm-code-review-structured-output-injection-defense.md)
describes escaping *query parameters and headers*; the body case is not covered there.

This is a real, reproducible display defect for any post whose code contains a comparison operator
or a generic. It is **not fixed in this round**: it is a security-sensitive change under
`src/`, outside a documentation round, and it deserves its own ticket with input/output tests. The
hero was re-shot with a snippet that renders cleanly rather than hiding the defect behind a
carefully chosen example in silence.

## Acceptance

- The three images resolve from `README.md` with repository-relative paths and render non-blank.
- The Mermaid block parses and renders (validated in a browser against mermaid 11.4.1).
- Every number printed next to the images names the run or file that produced it: the review is a
  local dev-stack run dated in the caption, the probe names its version, thread count, rounds and
  result files.
- No image claims a state that no run produced, and the brief's `92` is not printed anywhere.
- The new probe is reproducible: `python benchmark/concurrency/lua-vs-naive-consistency.py` writes
  the JSON and the Markdown summary, and `render-lua-consistency-chart.py` redraws the figure from
  the JSON alone.

**Files:** `README.md`, `docs/assets/readme/{async-review,community-feed,lua-consistency}.png`,
`benchmark/concurrency/lua-vs-naive-consistency.py`,
`benchmark/concurrency/render-lua-consistency-chart.py`,
`benchmark/concurrency/results/lua-vs-naive-20261014.{json,md}`, `docs/INDEX.md`, `CHANGELOG.md`,
`docs/tickets/readme-open-source.md` (relative-link correction only), this ticket.

## Rejected

- **A composed or mocked hero image.** The brief's layout and score describe a screen the product
  does not produce. Rendering it anyway would put a fake product state at the top of the repository
  that exists to demonstrate what happens when a model's output is trusted without a check.
- **Printing `92` next to a real review.** A score is a measurement. If the number does not come
  from a run, the caption is the only honest place for it - and a caption explaining that the
  screenshot disagree with its own label is worse than no label.
- **Using the 9/10 seed fixture as the hero.** It is a fixture, not a recorded run, and its
  `result_json` has no findings, so the terminal would show three `// No findings.` panels. It
  looks like a bug in the review pipeline even though it is a thin seed.
- **Putting the Redis probe in the evidence wall.** That table's header claims the repository's own
  stack; the probe ran against a standalone redis-server 5.0.14.1 on Windows. It is linked from the
  architecture section with its limits stated instead.
- **Fixing the body-escaping defect here.** Security-sensitive, `src/`-adjacent, and needs its own
  input/output tests and a decision about whether fenced blocks should be exempt from body
  sanitisation or unescaped at render time. Recorded above so it is not lost.

## Verification

Measured 2026-10-14, this machine:

```text
python benchmark/concurrency/lua-vs-naive-consistency.py --host localhost --port 6390 \
    --threads 50 --rounds 30
  -> [lua  ] same-user: 0/30 rounds drifted, max|drift|=0
  -> [naive] same-user: 29/30 rounds drifted, max|drift|=54

python benchmark/concurrency/render-lua-consistency-chart.py \
    benchmark/concurrency/results/lua-vs-naive-20261014.json docs/assets/readme/lua-consistency.png
  -> wrote docs/assets/readme/lua-consistency.png
```

The Mermaid block was rendered in headless Chrome against mermaid 11.4.1 and produced one `<svg>`
with no parse error. The hero was captured from `http://localhost:5173/post/2110293835995664385`
after the review completed, at 1440x1560.

## Status

Landed 2026-10-14 on this working tree, on `codex/readme-open-source`. Documentation and assets
only; the one code-adjacent finding (body HTML-escaping reaching code fences) is recorded above and
left open.

The same pass fixed eleven broken relative links in the sibling ticket
[readme-open-source.md](readme-open-source.md): its links had been written relative to `docs/`
(`../README.md`, `research/...`, `tickets/...`) and had not moved when the file went to
`docs/tickets/`. A repository-wide scan now resolves every relative link under `docs/` - the only
two reported misses are an application route (`/post/101`) and two `file.java:49` citations that are
paths with a line suffix, not links.
