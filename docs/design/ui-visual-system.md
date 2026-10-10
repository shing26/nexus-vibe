# Nexus-Vibe UI Visual System: AI Review + Profile Workspace

Scope: implementation-ready spec for `AiReviewTerminal` and `UserProfilePage`, including one Tailwind token change. No full-theme rewrite.

## Reuse Map

- Keep `BorderBeam` default cyan-purple gradient (`#06B6D4` to `#A855F7`) for the AI terminal.
- Keep `DecryptedText` only in the terminal header; render static text under reduced motion.
- `ui/ReviewStepStrip.tsx` (step list in `ui/reviewSteps.ts`) is the strip shared by the review replay and the in-flight review box; `ui/ExternalLink.tsx` carries the outbound-link attributes for the navbar and footer.
- Keep `SpotlightCard` in feed cards only; profile uses plain dark surfaces.
- Reuse `EmptyState`; add a `noActivity` preset.

## Visual Tokens

| Token | Hex | Semantic role |
| --- | --- | --- |
| `vibe-bg` | `#0A0D14` | App and terminal inner background |
| `vibe-surface` | `#111622` | Profile header, section panels |
| `vibe-card` | `#161C2A` | Cards, hover rows, skeletons |
| `vibe-border` | `#232D42` | Borders, dividers, timeline line |
| `vibe-cyan` | `#10B981` | Keep existing green success accent |
| `vibe-neon` | `#06B6D4` | New true cyan: AI score, focus, active states |
| `vibe-purple` | `#A855F7` | AI bot, template, optimization |
| `vibe-emerald` | `#059669` | Approved, low severity, selection |

Decision: add `vibe-neon` to `tailwind.config.js` as `neon: '#06B6D4'`; leave `vibe-cyan` unchanged to avoid regressions.

### Severity Map

| Severity | Tailwind classes |
| --- | --- |
| `low` | `text-vibe-emerald bg-vibe-emerald/10 border-vibe-emerald/30` |
| `medium` | `text-yellow-400 bg-yellow-400/10 border-yellow-400/30` |
| `high` | `text-orange-400 bg-orange-400/10 border-orange-400/30` |
| `critical` | `text-red-400 bg-red-400/10 border-red-400/30` |
| `unknown` | `text-slate-400 bg-slate-400/10 border-slate-400/30` |

## AiReviewTerminal

Use a structured detail object with `score`, `severity`, `verdict`, `isApproved`, `codeQuality`, `securityConcerns`, and `optimizationSuggestions`; values can be strings or string arrays. Never fall back to the current fake summary.

Container:

```
<section className="relative my-6 overflow-hidden rounded-lg border border-vibe-purple/40 bg-vibe-card/90 p-0.5">
  <BorderBeam size={250} duration={6} colorFrom="#06B6D4" colorTo="#A855F7" />
  <div className="rounded-md border border-vibe-border bg-vibe-bg/95 p-4 font-mono text-xs">
```

Add `motion-reduce:animate-none` to the animated beam child inside `BorderBeam`.

Header:

```
<div className="mb-3 flex flex-wrap items-center justify-between gap-2 border-b border-vibe-border pb-2.5">
  <span className="flex items-center gap-1.5 font-semibold text-vibe-purple"><Bot className="h-4 w-4" /> <DecryptedText text="AI Co-Pilot Automated Review System" speed={30} /></span>
  <span className="flex items-center gap-2">
    <span className="flex items-center gap-1 text-vibe-neon tabular-nums"><Gauge className="h-3.5 w-3.5" /> Score: {score ?? '--'}/100</span>
    <span className={isApproved ? 'rounded-md border border-vibe-emerald/30 bg-vibe-emerald/10 px-2 py-0.5 text-[10px] font-semibold text-vibe-emerald' : 'rounded-md border border-yellow-400/30 bg-yellow-400/10 px-2 py-0.5 text-[10px] font-semibold text-yellow-400'}>{isApproved ? 'Approved' : 'Needs Review'}</span>
  </span>
</div>
```

Score block: use a number plus a linear meter; do not build a circular dial for this small surface.

```
<div className="mb-3 flex items-center gap-3">
  <div className="flex h-12 w-16 shrink-0 items-baseline justify-center rounded-lg border border-vibe-neon/30 bg-vibe-surface"><span className="text-xl font-semibold text-vibe-neon tabular-nums">{score ?? '--'}</span><span className="text-[10px] text-slate-500">/100</span></div>
  <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-vibe-border"><div className="h-full rounded-full bg-gradient-to-r from-vibe-emerald to-vibe-neon" style={{ width: `${Math.max(0, Math.min(100, score ?? 0))}%` }} /></div>
</div>
```

Sections:

```
const sections = [
  { key: 'code', icon: Code2, color: 'text-vibe-neon', title: 'Code Quality' },
  { key: 'security', icon: ShieldAlert, color: 'text-red-400', title: 'Security Concerns' },
  { key: 'suggestions', icon: Wand2, color: 'text-vibe-purple', title: 'Optimization Suggestions' },
];
<div className="space-y-2">
  {sections.map((s) => (
    <section key={s.key} className="rounded-md border border-vibe-border bg-vibe-surface/80 p-3">
      <h4 className={`mb-1.5 flex items-center gap-1.5 text-[11px] font-semibold ${s.color}`}><s.icon className="h-3.5 w-3.5" /> [{s.title}]</h4>
      {renderFindings(s.value)}
    </section>
  ))}
</div>
```

`renderFindings` renders arrays as `li` items with `text-slate-300`; empty values show `// No findings.` in `text-slate-500`. Use `ShieldCheck text-vibe-emerald` when security is clear.

States:

```
pending: <div role="status" aria-live="polite" className="flex items-center gap-2 p-3 text-vibe-neon"><Loader2 className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none" /> AI Agent reviewing...</div>
unavailable: <div role="status" className="flex items-center gap-2 p-3 text-slate-500"><AlertCircle className="h-3.5 w-3.5" /> AI review data unavailable.</div>
```

### Replay (data state)

The data state renders a stored, finished review. It reveals instead of appearing: four steps
confirm in order - Code Quality, Security Concerns, Optimization Suggestions, Score Evaluation,
the three section names plus the score - each checking off as its section arrives, then the
score counts in and the meter fills. It is a replay, so the header says so with the run's own
recorded time: `// replay of completed run · 2026-09-15 10:00`. Never wording that reads as a
live claim; ADR-0010 removed that claim from the landing card and this is the same surface one
level down.

```tsx
<ReviewStepStrip mode="replay" completed={replayCompleted} className="mb-3" />
```

Row anatomy (`rounded-md`; done = `border-vibe-border bg-vibe-surface/80 text-slate-300` with a `Check` in `text-vibe-emerald` and an `ok` caption; active = `border-vibe-neon/40 bg-vibe-surface text-vibe-neon` with a spinning `Loader2`; waiting = `border-vibe-border/60 bg-vibe-surface/40 text-slate-500` with the step's own icon at 50% opacity):

```tsx
<li data-step={step.key} data-state={status} className="flex items-center gap-2 rounded-md border px-2 py-1.5 transition-colors ...">
  <span className="w-4 shrink-0 text-[10px] font-mono tabular-nums text-slate-500">{String(index + 1).padStart(2, '0')}</span>
  {icon}
  <span className="min-w-0 flex-1 truncate text-[11px] font-mono">{step.label}</span>
  {status === 'done' && <span className="shrink-0 text-[10px] font-mono text-vibe-emerald">ok</span>}
</li>
```

Pacing: 500ms per step, then the score over 12 ticks of 50ms; the reveal plays once per mount.
Section rows dim to `opacity-40` until their step confirms (`transition-opacity duration-500
motion-reduce:transition-none`); the content is in the DOM from the first render, so this is a
reveal for the eye and not a fetch. Under `prefers-reduced-motion` the strip renders every step
done and the score final on first paint.

The in-flight review box on the post detail page (`rounded-lg`, cyan border) uses the same
strip with `mode="running"`: one step cycles a spinner and none is ever confirmed, because the
client knows only `aiReviewed === 2`. The strip is `aria-hidden` there; the box keeps
`role="status" aria-live="polite"`.

### Evidence links

Navbar and footer outbound links use `ui/ExternalLink.tsx`: `text-[10px] font-mono
text-slate-400 hover:text-vibe-cyan` with a lucide icon in the navbar, `target="_blank"`,
`rel="noopener noreferrer"`, and a `title` that names what is on the other side. The navbar
shows them at `lg` and wider; the footer carries them at every width.

## User Profile Workspace

Keep `max-w-4xl mx-auto px-4 py-8`. Keep profile header, then stat strip, activity, posts.

### Stat Strip

Use `FileText`, `MessageSquare`, `Heart`, `Gauge`, `GitFork`, `GitBranch` for posts/comments/likes/avg AI/forks/versions.

```
<section aria-label="Profile stats">
  <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
    {stats.map((stat) => (
      <div key={stat.key} className="min-w-0 rounded-lg border border-vibe-border bg-vibe-card/70 p-3">
        <div className="flex items-center gap-1.5"><stat.icon className="h-3.5 w-3.5 text-vibe-neon" /><span className="text-[10px] font-mono text-slate-400">{stat.label}</span></div>
        <p className="mt-1 truncate text-xl font-semibold text-slate-100 tabular-nums">{stat.value}</p>
      </div>
    ))}
  </div>
</section>
```

Avg AI renders `--`, never `0`, when there are no reviewed posts.

### Activity Timeline

```
<ol>
  {activities.map((item) => (
    <li key={item.id} className="relative pb-4 pl-6 last:pb-0 before:absolute before:left-[3px] before:top-2 before:bottom-0 before:w-px before:bg-vibe-border last:before:hidden">
      <Link to={`/post/${item.postId}`} className="group block rounded-md px-2 py-1.5 hover:bg-vibe-card/70 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-neon">
        <span className="absolute left-0 top-1.5 h-1.5 w-1.5 rounded-full bg-vibe-neon shadow-[0_0_8px_rgba(6,182,212,0.45)]" />
        <div className="flex items-center gap-2"><item.icon className="h-3.5 w-3.5 shrink-0 text-vibe-neon" /><span className="min-w-0 flex-1 truncate font-mono text-xs text-slate-300 group-hover:text-vibe-neon">{item.title}</span><span className="shrink-0 text-[10px] text-slate-400">{relativeTime(item.createdAt)}</span></div>
        <p className="mt-0.5 pl-[22px] text-[10px] text-slate-500">// {typeLabel(item.type)}</p>
      </Link>
    </li>
  ))}
</ol>
```

Type colors: posts `FileText text-vibe-neon`, comments `MessageSquare text-vibe-purple`, versions `GitBranch text-vibe-emerald`.

### Compact Post Rows

```
<div className="space-y-2">
  {posts.map((post) => (
    <Link key={post.id} to={`/post/${post.id}`} className="group block rounded-lg border border-vibe-border bg-vibe-card/70 p-3 hover:border-vibe-neon/40 hover:bg-vibe-card focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-neon">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="rounded-md border border-vibe-cyan/30 bg-vibe-cyan/10 px-2 py-0.5 font-mono text-[10px] text-vibe-cyan">{post.categoryName}</span>
        <span className="min-w-0 flex-1 basis-48 truncate font-mono text-xs text-slate-200 group-hover:text-vibe-neon">{post.title}</span>
        {post.aiScore !== null && <span className="font-mono text-[10px] text-vibe-neon tabular-nums">AI {post.aiScore}</span>}
        <span className="shrink-0 font-mono text-[10px] text-slate-400">{relativeTime(post.createTime)}</span>
      </div>
      <div className="mt-1.5 flex flex-wrap items-center gap-3 font-mono text-[10px] text-slate-400"><span className="flex items-center gap-1"><Heart className="h-3 w-3" /> {post.likeCount}</span><span className="flex items-center gap-1"><MessageCircle className="h-3 w-3" /> {post.commentCount}</span></div>
    </Link>
  ))}
</div>
```

### Empty, Loading, Error

- No activity: `EmptyState` preset `noActivity` with `Activity`, title `No recent activity`, desc `No public activity has been recorded yet.`, action `null`.
- No posts: reuse `preset="noPosts"`; for another user's profile override `action={null}` and `actionLabel={null}`.
- Loading: header skeleton, then `grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6` of `h-16 animate-pulse rounded-lg bg-vibe-card`, then `h-40 animate-pulse rounded-lg bg-vibe-card`.
- Error: `rounded-lg border border-red-500/40 bg-red-950/40 p-4 font-mono text-xs text-red-400`.

## Typography, Spacing, Radius

- `font-sans` for prose; `font-mono` for headings, labels, scores, timestamps, terminal content.
- Sizes: profile name `text-2xl`, stat values `text-xl`, section headings `text-sm`, terminal body `text-xs`, metadata `text-[10px]`/`text-[11px]`.
- Spacing: 4px base; use `gap-1.5`, `gap-2`, `gap-3`, `p-3`, `p-4`, `mt-8` between sections.
- Radius: `rounded-md` 6px and `rounded-lg` 8px only for new surfaces; no new `rounded-xl` or `rounded-[10px]`.
- No negative tracking; keep `tracking-normal` or omit.

## Accessibility

- Required secondary text uses `text-slate-300`/`text-slate-400`; `text-slate-500` is reserved for disabled/decorative/empty copy.
- AA-safe dark pairs: `#06B6D4` on `#0A0D14`, `#94A3B8` on `#111622`, `#F87171` on `#0A0D14`.
- Links/buttons: `focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-neon`.
- Add `motion-reduce:animate-none` to `animate-border-beam`, `animate-pulse`, `animate-spin`.
- Render `DecryptedText` as static text under `prefers-reduced-motion: reduce`.
- Use `aria-label` for stat strip/timeline/posts and `role="status"`/`aria-live="polite"` for terminal pending.
