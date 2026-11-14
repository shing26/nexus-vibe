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
   replay reveal inside the expanded terminal). Every animation keeps
   `motion-reduce:animate-none`.
4. **Mono is for code.** `font-mono` appears only inside code blocks, code spans, and the
   trace id. Headings, buttons, labels, timestamps are `font-sans`.
5. **One accent.** Blue for interactive. Green/red for success/danger semantics only.

## Tokens

Token names keep the `vibe-` prefix (the codebase, tests, and ADRs reference it); the values
change. Update `frontend/src/index.css` variables and `frontend/tailwind.config.js` mapping
in one commit.

| Token | Light | Dark | Role |
| --- | --- | --- | --- |
| `vibe-bg` | `#FFFFFF` | `#0D1117` | App background |
| `vibe-surface` | `#F6F8FA` | `#161B22` | Sidebar, panels, header bars |
| `vibe-card` | `#FFFFFF` | `#21262D` | Cards, hover rows, elevated content |
| `vibe-border` | `#D0D7DE` | `#30363D` | All borders and dividers |
| `vibe-accent` | `#0969DA` | `#58A6FF` | Links, primary buttons, active states, focus |
| `vibe-success` | `#1A7F37` | `#3FB950` | Approved, safe, done |
| `vibe-danger` | `#CF222E` | `#F85149` | Critical severity, destructive actions |
| `vibe-warning` | `#9A6700` | `#D29922` | Medium severity, pending states |
| `vibe-text` | `#1F2328` | `#E6EDF3` | Primary text |
| `vibe-muted` | `#59636E` | `#8B949E` | Secondary text, metadata |
| `vibe-faint` | `#818B98` | `#6E7681` | Disabled, decorative, empty copy |

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
tabs (GitHub underline style, `vibe-accent` active rail). Pagination stays.

Skeletons: one shared `Skeleton` component (plain `animate-pulse` `bg-vibe-surface`
blocks); the five copied shimmer blocks die with it. `ShimmerButton`, `BorderBeam`,
`DecryptedText`, `SpotlightCard` are deleted outright (files, usages, their tests).

## Post detail

Single centered column (`max-w-3xl`), GitHub Discussions anatomy: title row (`text-xl`,
sans), under it a metadata line (author, channel badge, relative time, AI score badge —
the badge is a bordered pill, `vibe-accent` text, not a neon chip), then rendered body,
then the comment stream. `CodeBlock` styling unchanged (it already reads well). The
in-flight review box and replay box live in the same column, above comments.

## AiReviewTerminal v2

A quiet check-run card, not a terminal: `rounded-lg border border-vibe-border
bg-vibe-surface`. Header row always visible: `Bot` icon + "AI 评审" (i18n), score
`tabular-nums`, severity pill, verdict badge (approved `vibe-success` / needs review
`vibe-warning`), chevron. Findings sections (Code Quality / Security / Suggestions) and
the four-step `ReviewStepStrip` render inside the collapsed region and are **collapsed by
default**; the replay animation plays on first expand, once per mount, never on the page
load path. Data contract, four states (pending/loading/error/unavailable/data), the
"replay of completed run" caption rule from ADR-0010, and the existing test assertions on
step order and score all carry over unchanged; `prefers-reduced-motion` still renders the
finished state at once.

## Forms, buttons, feedback

- Primary button: solid `vibe-accent` background, white text, `rounded-md`; secondary:
  bordered, `vibe-surface`; danger: `vibe-danger` outline. No gradients, no shimmer.
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
