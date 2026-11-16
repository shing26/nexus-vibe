# ADR-0020: The forge pivot replaces the terminal aesthetic

Date: 2026-11-15
Status: Accepted (pending the design-overhaul round's contract freeze)

## Context

Nexus-Vibe's frontend was built on a deliberate "AI terminal" aesthetic: dark vibe
palette, neon cyan-purple accents, BorderBeam / DecryptedText / ShimmerButton /
SpotlightCard effect components, mono type on non-code surfaces, a dot-grid background
(`docs/design/ui-visual-system.md`, v1). The maintainer's 2026-11-14 verdict after using
the live site: it reads as "AI 流水线味" (pipeline-flavored) — templated, louder than its
content — and the reference points are GitHub and linux.do (a Discourse deployment).
Separately, the site has no i18n at all (92 hardcoded Chinese lines across 8 files, other
pages English-only, mixed within single objects) despite a stated need for 中英文切换.

A design-overhaul round is contracted (`plans/design-overhaul-round.md`). Its How-layer
details were settled in a grilling session on 2026-11-15; the implementation-ready spec is
`docs/design/ui-visual-system-v2.md`.

## Decision

1. **The visual language pivots to "forge"**: GitHub-style chrome (top bar + left channel
   sidebar + hamburger drawer below `lg`), Discourse-density topic rows, neutral gray
   surfaces, a single GitHub-blue accent (`#0969DA` light / `#58A6FF` dark), mono type
   only on code. The four effect components are deleted. Decorative animation is removed
   site-wide; state-communicating motion stays with `motion-reduce` guards.
2. **The default theme keeps following the system** (`themeStore` behavior unchanged);
   both palettes are re-pointed at the v2 token values.
3. **AiReviewTerminal is redrawn, not retired.** The AI review pipeline is the project's
   subject (ADR-0002 onward); the terminal keeps its data contract, its four states, the
   four-step replay strip semantics, and ADR-0010's "never reads as a live claim" rule.
   What changes: it becomes a collapsed-by-default check-run card (header shows score and
   verdict; findings and the replay reveal open on demand), in the quiet v2 styling.
4. **i18n is built from zero** on `i18next` + `react-i18next`, Chinese as source locale,
   browser-language detection, persisted manual toggle. Frontend-owned strings only; the
   backend's Chinese envelope messages pass through verbatim; user content is never
   translated.

## Consequences

- The "keep the terminal motif" and "no new npm dependencies" constraints in
  `docs/product/prioritization.md` and the v1 visual system are superseded for this round.
  v1's terminal-specific rulings that were aesthetic-only (BorderBeam wrapper, DecryptedText
  header, mono headings) are retired; its data-contract, state, replay-semantics, and
  accessibility rulings carry over into v2.
- The shell change (sidebar + drawer) re-lays-out every page; this is the round's largest
  diff and the reason the round gets its own frozen contract and independent verifier.
- Tests asserting retired copy or effect behavior are rewritten to the new behavior —
  recorded as legitimate assertion rewrites (criterion-level: stricter, not looser), not
  weakenings.
- The information architecture change (channels move out of the top bar into the sidebar)
  is a product-visible pivot for a portfolio artifact whose README and screenshots
  reference the old look; screenshots in `docs/assets/` that show the old shell stay as
  history — new screenshots document the new shell at round close.

## Rejected

- **Skin-only reskin** (keep MainLayout skeleton, redraw internals): cheapest option, but
  it cannot reach the linux.do reference the maintainer named; channel navigation would
  stay buried in a top bar.
- **Retiring AiReviewTerminal** or reducing it to plain text: deletes the one surface the
  project exists to demonstrate; the collapse pattern fixes its real problem (length) more
  cheaply than removal.
- **Full Discourse clone** (categories with permission knobs, badges, reactions): the
  project is a portfolio artifact of LLM-reliability engineering, not a forum product;
  borrowing chrome and density is enough.
- **Backend i18n / Accept-Language negotiation**: backend envelope copy is an operator
  surface; touching every controller for it is out of proportion to the need (recorded in
  the round ticket's Rejected).
- **A homegrown i18n dictionary** over two dependencies: 17 pages × full copy with
  plural/interpolation/fallback needs re-implements i18next badly.

## Amendment (2026-11-16): the review voucher is visible by default

Decision 3's "collapsed-by-default check-run card" is superseded for the card itself.
The signature surface now opens with a visible voucher — verdict, score, severity, model,
call count, optional duration, and a link to the raw log — while findings and the replay
strip stay collapsed. The data contract, four states, replay semantics, and ADR-0010's
"never reads as a live claim" rule are unchanged. The reversal and its reasoning are
recorded in `docs/tickets/design-overhaul-fixes-b.md`; the visual spec's AiReviewTerminal
section carries the current wording.

