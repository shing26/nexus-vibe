# ADR-0023: The console palette, and dark as the default theme

Date: 2026-11-22
Status: Accepted (frozen in `plans/console-dark-slice.md`)
Supersedes: ADR-0020 decisions 1 (accent clause) and 2 (system-following default)

## Context

ADR-0020 replaced the v1 "AI terminal" aesthetic with GitHub-style forge chrome: neutral
surfaces, a single GitHub-blue accent, and a default theme that follows the OS. That
decision was made against a real problem — the v1 look read as a pipeline demo, louder
than its content.

Two years of use later the maintainer's verdict on the v2 result is the opposite failure
mode: the page "与传统的论坛项目/课程作业没区别". The specific complaints were concrete:
the white surface is hard to look at for long sessions, and the interface has no quality
that belongs to this product rather than to any forum.

An unslop audit (`docs/tickets/console-dark-slice.md`) traced this to template look: the
GitHub-blue accent, white cards and default type would survive a product-name swap
unchanged. The accent that was chosen to suppress one template had become another.

The product's own subject supplies the alternative. Nexus-Vibe's thesis is that an LLM is
an unreliable dependency, demonstrated by a review pipeline that fails closed, holds
posts for review, and records what it did. Its natural world is a night-shift console:
status lights, log timestamps, audit stamps — not a daytime document.

## Decision

1. **Dark is the default theme.** A first visit with no stored preference renders dark.
   Light stays fully supported as a switchable theme, and a reader's choice persists.
   This reverses ADR-0020 decision 2's "keep following the system"; the system
   preference is no longer consulted, because a portfolio piece should present its own
   designed surface rather than inherit the reviewer's desktop settings.
2. **The accent is the audit green (`#4ABF92` dark), not GitHub blue.** Green carries the
   product's meaning (a review completed and was recorded), so it stops being a
   decorative brand color and becomes a semantic one. Amber and red remain warning and
   danger only.
3. **A companion token, `--vibe-on-accent`, owns the text color on accent fills.** White
   on the audit green does not reach 4.5:1; dark ink does. Every primary action uses the
   pair, so contrast cannot regress per component.
4. **Depth comes from three surface steps plus a hairline, never from shadows.** Dark
   surfaces cannot carry colored shadows or glows without reading as neon; the ramp is
   the hierarchy.
5. **One hue may speak per screen.** The audit green appears on stamps, status lights and
   the primary action; everything else is the neutral ramp. Identity colors generated
   per user (the hashed avatar palette) are removed for the same reason.

The token values and the per-component mapping live in
`docs/design/ui-visual-system-v2.md`, amended alongside this ADR.

## Consequences

- `docs/design/ui-visual-system-v2.md` rows for `vibe-accent`, the dark ramp and the
  primary-button rule are superseded by the values in the slice contract; the document is
  amended rather than rewritten, so the v2 reasoning stays readable.
- Every surface that used `bg-vibe-accent` as decoration now reads green. The slice
  contract lists the surfaces in scope (home shell); pages outside it keep their current
  accent use until their own round, which is a deliberate, visible inconsistency for one
  slice rather than a silent half-migration.
- Screenshots in `docs/assets/` that show the blue/light shell stay as history.
- The light theme is not deleted. It is the fallback for readers who need it, and the
  slice's acceptance criteria require it to remain usable.

## Rejected

- **Keep system-following default, change only the palette**: the maintainer's complaint
  is about the surface they actually see; on a light-preferring machine nothing changes.
- **Keep GitHub blue and only darken the background**: blue accent is precisely what makes
  the page interchangeable with any developer forum.
- **Introduce a design-system library**: the token layer already exists; replacing it
  would rewrite every component for a change that is about direction, not components.
- **Two accents (blue for interactive, green for audit)**: two hues competing on the same
  screen is how the "everything highlighted" problem returns; one hue with a clear job is
  the point.
