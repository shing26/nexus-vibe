# Nexus-Vibe UX Architecture: AI Review + Profile Workspace

Extends `docs/design/ui-visual-system.md`; that file owns visual tokens, exact Tailwind classes, severity colors, and accessibility rules. This file adds the information architecture, component states, and interactions.

## Data Contracts

`AiReviewDetail`: `postId`, `reviewer`, `score`, `severity`, `isApproved`, `codeQuality`, `securityConcerns`, `optimizationSuggestions`, `reviewedAt`. Field values may be strings or string arrays; normalize both before rendering. Never synthesize values.

`UserProfileSummary`: public user fields plus `stats.posts`, `stats.comments`, `stats.likesReceived`, `stats.avgAiScore`, `stats.forks`, `stats.versions`, and `recentActivity[]` with `type`, `postId`, `title`, `createdAt`.

## Reuse Constraints

- Use existing `vibe-bg`, `vibe-surface`, `vibe-card`, `vibe-border`, `vibe-cyan`, `vibe-purple`, `vibe-emerald`, plus the approved new `vibe-neon` (`#06B6D4`) for AI score, active states, and focus.
- Use `lucide-react`, `motion`, `react-router-dom`, and TanStack Query patterns already in the repo. No new npm dependencies.
- No DB schema or migration changes. Backend DTOs derive from existing review logs and profile aggregation queries.
- Reuse `BorderBeam`, `DecryptedText`, `EmptyState`, `Avatar`, and the severity/status styling already used in `AgentLogsPage`. Profile does not reuse `SpotlightCard` or `PostCard`.
- Follow the visual system spacing and radius rules: 4px spacing base, `rounded-md`/`rounded-lg` only, mono for headings/scores/timestamps, no negative tracking.

## 1. AiReviewTerminal

### Layout And Hierarchy

Keep the terminal as the AI feedback surface below the post content. Container uses the existing `BorderBeam` wrapper, `bg-vibe-card/90`, and `bg-vibe-bg/95` inner panel.

Hierarchy, top to bottom:
1. Header: Bot icon + `DecryptedText` title on the left; score, severity, and verdict on the right.
2. Replay strip: the four review steps confirming in order, under a caption naming the run and its recorded time.
3. Score block: large `score/100` value plus a linear meter from `vibe-emerald` to `vibe-neon`.
4. Findings sections: Code Quality, Security Concerns, Optimization Suggestions.

On desktop, findings stack vertically inside the terminal because the content is dense and explanatory; no horizontal card grid. On mobile, all rows stay full-width and wrap naturally.

### States

- `pending`: full-width cyan state with `Loader2`, `role="status"`, `aria-live="polite"`, and "AI Agent reviewing...". No terminal details are shown yet. Add `motion-reduce:animate-none`.
- `loading`: terminal chrome visible with skeleton bars in place of the score meter and findings sections.
- `error`: red alert panel with a compact Retry icon button that calls the TanStack Query `refetch`. Show no score or findings.
- `unavailable`: minimal `role="status"` panel: `AlertCircle` plus "AI review data unavailable." This is also the empty state when the endpoint returns `null` or no structured log exists.
- `data`: render the real structured review as a replay of a completed run: the four steps confirm in order (500ms each), then the score counts in (12 ticks of 50ms) and the meter fills, and each findings section arrives with its step. Missing optional sections render `// No findings.`; they never receive fabricated text. The header caption reads `// replay of completed run · <reviewedAt>`; the wording may never read as a live claim, the rule ADR-0010 set for the landing card applied one surface down.

`PostDetailPage` queries `GET /agent-logs/post/{postId}/latest` only when the post has a code block and `aiReviewed === 1`. Keep the existing 5s polling while `aiReviewed !== 1`, then stop it once the review query becomes enabled.

While `aiReviewed === 2` the page shows the in-flight box: the same four steps with exactly one spinner cycling and none confirmed, because the client knows only REVIEWING and not which stage the worker stands in. The strip is `aria-hidden` (a live region re-announcing four labels every 1.4s is worse than the line it would talk over); the box itself carries `role="status"` and `aria-live="polite"`, and its caption states the mechanism - polling every 5s.

### Interactions

- Keep the terminal expanded by default. A collapse toggle is not needed at this density; defer it unless findings become much longer.
- Use `tabular-nums` for the score and timestamps so values do not shift layout.
- Use lucide icons in section headers: `Code2`, `ShieldAlert`, and `Wand2`; use `ShieldCheck` for a clear security result.
- Empty findings use `// No findings.` in `text-slate-500`; do not replace them with generated sentences.

### Replay states are test-enforced

`AiReviewTerminal.test.tsx` asserts the step order and the landing score with fake timers;
`ReducedMotionTerminal.test.tsx` asserts the finished state renders at once under
`prefers-reduced-motion` (the media stub is installed at module scope, because motion caches
the answer on first render); the caption is asserted never to read as a live claim.

## 2. UserProfilePage

### Layout And Hierarchy

Keep `max-w-4xl mx-auto px-4 py-8` and this order: profile header, stat strip, recent activity, compact published posts.

Desktop:
- Profile header is a full-width `bg-vibe-surface` panel.
- Stat strip is one row of six equal cards.
- Activity timeline and published posts stack vertically; the timeline leads because it is the proof-of-work narrative, then posts support discovery.

Mobile:
- Header remains horizontal but allows the identity block to truncate; avatar keeps fixed size.
- Stats become 2-column cards (`grid-cols-2`), then 3 columns at `sm`, then 6 at `lg`.
- Activity and posts become full-width stacked lists with 36px+ touch targets.

### Stat Strip

Six stats in fixed order: Posts (`FileText`), Comments (`MessageSquare`), Likes Received (`Heart`), Avg AI Score (`Gauge`), Forks (`GitFork`), Versions (`GitBranch`).

Each card shows icon, short label, and one prominent value. Average AI score renders `--`, never `0`, when there are no reviewed posts. All values use `tabular-nums` and truncate rather than wrapping.

### Activity Timeline

Render up to 10 items as a vertical timeline with a left rail, dot, icon, title, type label, and relative timestamp. Map types:
- post: `FileText`, neon
- comment: `MessageSquare`, purple
- version: `GitBranch`, emerald
- fork: `GitFork`, neon
- fallback: `Activity`, slate

The entire row is a `Link` to `/post/{postId}`. The title is the primary scan target; the type label is secondary metadata.

### Compact Published Posts

Use dense rows instead of `PostCard`: channel badge, truncated title, optional AI score, relative age, then like/comment counts. Rows remain full-width on mobile and wrap metadata without changing row height unpredictably.

### States

- `loading`: profile header skeleton, then a 2/3/6-column stat skeleton, then a timeline/post skeleton.
- `error`: red alert panel with the failed section label and a Retry action. Summary and posts fail independently so one failure does not blank the other.
- `empty activity`: `EmptyState` with new `noActivity` preset, icon `Activity`, title "No recent activity", desc "No public activity has been recorded yet.", action `null`.
- `empty posts`: reuse `preset="noPosts"`; for another user's profile override `action={null}` and `actionLabel={null}`.
- `data`: render header, stats, timeline, and compact posts from the summary and posts queries.

Use two TanStack queries: `['user', id, 'summary']` for `GET /users/{id}/summary`, and `['posts', 'user', id]` for the existing published posts endpoint. Keep query keys stable so invalidation and caching follow repo patterns.

### Interactions

- Timeline row hover brightens the title and left dot; row receives `focus-visible` outline with `vibe-neon`.
- Stat cards are non-navigational by default; hover shows the existing border/card accent. Use a `title` attribute to explain "Avg AI score excludes unreviewed posts" and other ambiguous metrics. No hover-only information.
- Posts rows and timeline rows both navigate to post detail. Use native `Link` elements so middle-click and keyboard behavior work.
- Empty states give context and, only for the current user's own profile, a `Create First Post` action.

## 3. Navigation: evidence links

The navbar (`lg` and wider) and the footer (every width) link out to the repository's own
evidence, so a visitor who wants to check a claim reaches it in one click: the ADR index, the
async-pool load test, and the observability drill from the footer. They open in a new tab with
`rel="noopener noreferrer"` from one shared `ui/ExternalLink.tsx`, and each carries a `title`
naming what is on the other side.

Deliberately absent: a link to the demo's `/actuator/health`. It answers `{"status":"UP"}` over
the `servable` group with details hidden, so it is one word wide; and when the home machine is
asleep it is an ngrok error page, which reads to a first-time visitor as a broken project. The
GitHub targets answer the "show me the evidence" question whatever this machine is doing.

## Design Priorities

1. Render real structured AI review data and remove the fabricated terminal summary.
2. Keep pending and unavailable states explicit, accessible, and visually distinct.
3. Make profile activity the first proof-of-work layer with full-row post links.
4. Use the existing visual system and TanStack Query patterns; add only `vibe-neon` as a token.
5. Keep empty and legacy-data rendering non-breaking and truthful.
