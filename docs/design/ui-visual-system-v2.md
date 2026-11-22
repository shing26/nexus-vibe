# Nexus-Vibe UI Visual System v2: Forge Pivot

Supersedes the aesthetic rulings of `ui-visual-system.md` (v1) and the shell-related parts of
`ux-architecture.md`; the pivot itself is recorded in ADR-0020. What survives from v1: the
AiReviewTerminal data contract, its four states, the replay strip semantics, and every
accessibility rule not tied to the retired neon palette. Implementation target: the frozen
direction of the design-overhaul round — GitHub-style chrome, Discourse-style density,
content first, zero decorative animation.

## Design Principles

1. **Content is the interface.** Chrome is gray; only links, primary actions, and active
   states carry the accent.
2. **Border first.** Separation is a 1px border, not a shadow or a glow. Shadows exist only
   on overlays (dropdown, drawer, modal, toast).
3. **No decorative motion.** Nothing animates unless it communicates state (spinner, the
   replay reveal inside the expanded terminal, a pressed control). A restrained press
   feedback (`active:scale-[0.98]`, via the shared `.press-feedback` class) is state
   communication, not decoration; it must carry `motion-reduce:transform-none`. Every
   animation keeps `motion-reduce:animate-none`.
4. **Mono is for machine output.** `font-mono` is used inside code blocks and code spans,
   on the trace id, and on audit metadata where aligned digits matter: timestamps,
   counters, review scores and the AI-credential stamp. Headings, buttons and navigation
   labels stay `font-sans`. (Amended by ADR-0023: the console surface treats a log line as
   machine output, not as a label.)
5. **One accent.** The audit green marks the primary action, a completed review and a live
   status; amber/red stay warning/danger semantics only. (Amended by ADR-0023: the accent
   is no longer blue.)
6. **Honest AI surfaces.** Every AI-related surface (review card, live pipeline strip, AI
   profile) renders real pipeline state — failure, retry, and degradation get the same
   design care as success, and nothing ever reads as live unless it is (ADR-0010's rule,
   project-wide). Decoration never claims capability.
7. **Two dialects, two surfaces.** The public surface speaks forge: plain gray chrome, no
   terminal voice. The operational surface (`/agent-logs`) keeps the terminal dialect —
   `$`, mono, the log-console heading — because there the reader is inspecting machine
   output, not reading a community post. A playful string in a login, publish or like
   response is a dialect leak, not voice: backend envelope messages stay neutral Chinese.

## Tokens

Token names keep the `vibe-` prefix (the codebase, tests, and ADRs reference it); the values
change. Update `frontend/src/index.css` variables and `frontend/tailwind.config.js` mapping
in one commit.

| Token | Light | Dark | Role |
| --- | --- | --- | --- |
| `vibe-bg` | `#FFFFFF` | `#14171A` | App background |
| `vibe-surface` | `#F6F8FA` | `#1B1F24` | Sidebar, panels, header bars |
| `vibe-card` | `#FFFFFF` | `#171B1F` | Cards, hover rows, elevated content |
| `vibe-border` | `#D0D7DE` | `#2E343A` | All borders and dividers (a hairline, not a box) |
| `vibe-accent` | `#0969DA` | `#4ABF92` | Primary action, completed review, live status |
| `vibe-on-accent` | `#FFFFFF` | `#14171A` | Text drawn on an accent fill (dark ink in dark) |
| `vibe-success` | `#1A7F37` | `#4ABF92` | Approved, safe, done |
| `vibe-danger` | `#CF222E` | `#E2746A` | Critical severity, destructive actions |
| `vibe-warning` | `#9A6700` | `#D29A4A` | Medium severity, pending states |
| `vibe-text` | `#1F2328` | `#E8ECEF` | Primary text |
| `vibe-muted` | `#59636E` | `#9AA5B1` | Secondary text, metadata |
| `vibe-faint` | `#818B98` | `#5F6B77` | Disabled, decorative, empty copy |

Dark is the default theme (ADR-0023); light stays fully supported and switchable. The
dark column above is the console ramp: three surface steps carry depth, and the hairline
is pre-blended so `border-vibe-border` reads as a rule rather than a box.

Retired: `vibe-cyan` (emerald), `vibe-neon`, `vibe-purple`, the radial dot-grid body
background, every gradient that is not inside a code block.

Severity map (AiReviewTerminal and agent-logs): low → `vibe-success`, medium →
`vibe-warning`, high → orange (`#BC4C00` light / `#DB6D28` dark), critical → `vibe-danger`,
unknown → `vibe-muted`.

Contrast: every text/background pair in both palettes must pass WCAG AA (4.5:1 body,
3:1 large); `vibe-faint` is reserved for disabled/decorative so it never carries required
reading. Test pairs when the tokens land, not after pages are rebuilt.

## Typography

- Inter stays the only sans face; JetBrains Mono stays for code. Drop both from any
  non-code surface that v1 set in mono (buttons, toasts, stat labels, terminal header).
- Scale: page title `text-xl`/`text-2xl`, section heading `text-sm` semibold, body `text-sm`,
  metadata `text-xs`, badges/labels `text-[11px]`. No negative tracking.
- `tabular-nums` on scores, counts, timestamps (carried over from v1).

## App Shell (the IA change)

Top bar (GitHub-style, one row, `h-12`, `vibe-surface`, bottom border): logo + site name
left; center search field (opens the existing search page, focusable, also reachable via
CommandPalette); right side: language toggle, user menu (avatar dropdown). No channel
navigation in the top bar.

Sidebar (≥ `lg`, fixed left, `w-60`, `vibe-surface`, right border, own scroll):
- **社区** section: the seven channels (`/channel/:slug`, name + muted description on one
  line each, active channel gets `vibe-accent` text + accent left rail) and a Tags link
  (`/tags`).
- **我的** section (authenticated only): 我的帖子, 草稿, 消息, 设置.
- Signed-out visitors see only 社区.
- The sidebar is the single navigation surface for channels; the Footer keeps evidence
  links (ADR index, load test, drill) at every width, restyled per the ExternalLink rules
  carried over from v1.

Below `lg`: the sidebar becomes a hamburger drawer (left slide-in, overlay shadow, closes
on route change); the existing `MobileTabBar` stays as the bottom bar, restyled to the new
tokens. Drawer and bottom bar are two entrances to the same routes.

`CommandPalette` (Ctrl+K) stays, restyled; it remains the keyboard entry to channels,
search, and actions.

## Lists (Discourse density)

`PostCard` becomes a topic-row list: one row per post, full-width, separated by
`vibe-border` (no card boxes), `py-2.5 px-3`, hover `vibe-surface`. Row anatomy: status/
type icon (or avatar), title (`text-sm`, `vibe-text`, truncate), inline metadata line
(`text-xs`, `vibe-muted`: channel badge, author, relative time, views), right-aligned
counts (likes, comments, AI score where present — `tabular-nums`). Unread/read emphasis:
titles of posts the user has not opened render `font-medium` (a lighten, not a color).

Home keeps its tab row (hot / unanswered / latest) above the list, restyled as underlined
tabs (GitHub underline style, `vibe-accent` active rail). Pagination stays. Signed-in
readers meet one featured strip (the recorded showcase review) above the tabs and then the
list; signed-out visitors additionally keep the Mission Control band. There is no channel
grid on Home — the sidebar is the single channel navigation surface.

Skeletons: one shared `Skeleton` component (plain `animate-pulse` `bg-vibe-surface`
blocks); the five copied shimmer blocks die with it. `ShimmerButton`, `BorderBeam`,
`DecryptedText`, `SpotlightCard` are deleted outright (files, usages, their tests).

## Post detail

Single centered column (`max-w-3xl`), one reading order: title (`text-2xl`, sans), an
identity line (author · channel · relative time, mono `text-xs`), then a state-stamp line
that renders only values the post actually has (pending, template, role, version, AI score
as a green audit stamp), then the body **on the page background rather than in a card**,
then the single engagement/action row, then the comment stream. `CodeBlock` styling
unchanged (it already reads well). The in-flight review box and replay box live in the same
column, above comments. (Amended by Slice C2; the earlier wording kept the body in a card
and repeated the counts in the header.)

## Chaos Theatre

One reading order, top to bottom: title, the thesis line (a left accent rail, one sentence
that names the cost rather than the feature), the disclosure, the five scenario cards, then
the selected trace. Each card reads as **fault / defense / verdict** on three label-value
rows, so the five exhibits compare as a taxonomy instead of a feature list.

The selected trace opens with the code that was actually reviewed: `CodeBlock`, Java
highlighted, with the line under discussion carrying a 2px left rail in `vibe-warning`
(danger for the injection comment, success for the guard the fix adds). `fix-challenge`
shows before and after side by side above 640px, stacked below it.

There is **no per-step clock**. The only timing the page may claim is the scenario's
measured duration, labelled "本次回放耗时" (and, for `fix-challenge`, "含修复前后两次评审").
The rows are an ordered account, numbered `01`, `02`, …; a hardcoded "+400 ms" beside a
measured "4 ms" is a contradiction a reader would rightly read as fabrication.

Below 640px the actual trace and its counterfactual stack; at and above it they sit in two
columns headed "实际发生" / "如果模型正常", both visible by default. The comparison is the
lesson, so it is not behind a toggle — the toggle only collapses it. The setup rows every
scenario shares (`input`, `prompt`) stay in the list as evidence but are demoted in weight.

## AiReviewTerminal v2

A quiet check-run card, not a terminal: `rounded-lg border border-vibe-border
bg-vibe-surface`. The header is a **review voucher, visible by default**: `Bot` icon +
"AI 评审" (i18n), score `tabular-nums`, severity pill, verdict badge (approved
`vibe-success` / needs review `vibe-warning`), and — on a second always-visible row — the
reviewer/model (`detail.reviewer`), the call count derived from the post's review-log rows,
the optional runtime (`result_json.durationMs`, omitted when absent), and a link to the raw
`ai_review_log` row on the admin agent-logs surface. Findings sections (Code Quality /
Security / Suggestions) and the four-step `ReviewStepStrip` live in the collapsible region
and are **collapsed by default**; the replay animation plays on first expand, once per
mount, never on the page load path. This reverses the earlier "whole card collapsed by
default" ruling: the recorded run is the product's claim, so the voucher is the headline
and the findings are the detail. Data contract, four states
(pending/loading/error/unavailable/data), the "replay of completed run" caption rule from
ADR-0010, and the existing test assertions on step order and score all carry over unchanged;
`prefers-reduced-motion` still renders the finished state at once.

## Forms, buttons, feedback

- Primary button: solid `vibe-accent` background, `vibe-on-accent` text, `rounded-md`;
  secondary: bordered, `vibe-surface`; danger: `vibe-danger` outline. No gradients, no
  shimmer. (White on the audit green fails AA, hence the paired token.)
- Inputs: 1px `vibe-border`, focus ring `vibe-accent` (2px), `rounded-md`.
- Toasts: `vibe-card` surface, 1px border, sans text, severity icon; no `bg-black/80`.
- Empty states: `EmptyState` stays, icons and copy per i18n; no ASCII-art flourishes.
- Degradation banner, ErrorBoundary: restyle to tokens, keep aria rules.

## i18n architecture

- `i18next` + `react-i18next`, Chinese as the source locale, `en` as the second resource.
  Resources live in `frontend/src/i18n/locales/{zh,en}.json`; namespaces: `common`, `home`,
  `channel`, `post`, `editor`, `auth`, `profile`, `messages`, `admin`, `agentLogs`.
- Detection: `navigator.language` on first visit (en → `en`, otherwise `zh`); manual toggle
  persists to localStorage (`nexus-vibe-lang`); the toggle lives in the user menu and on
  `/user/settings`.
- Scope: all frontend-owned strings (chrome, labels, empty states, toasts, validation
  copy). Backend envelope `message` values pass through verbatim (unchanged
  `serverErrorMessage.ts`); user-generated content is never translated.
- Channel display names come from one shared constants module (single source for sidebar,
  home, badges) keyed by slug, resolved through the dictionary — the HomePage/Sidebar
  duplication dies here.
- ADR-0020 records the dependency addition (the v1-era "no new npm dependencies" rule is
  superseded for exactly these two packages).

## Accessibility

Everything v1 ruled that is not neon-specific carries over: `focus-visible` outlines
(now `vibe-accent`), `motion-reduce:animate-none` on every animation, `aria-live` on
pending states, `aria-label` on landmark sections, AA text pairs, 36px+ touch targets on
mobile rows. New rule: the sidebar drawer traps focus while open and returns it on close.

## Definition of done for the visual round

A page is done when: no retired token/effect class remains on it, its strings resolve
through the dictionary in both locales, it renders correctly in both palettes at 375px
and 1280px, and its tests (updated copy assertions included) pass.
