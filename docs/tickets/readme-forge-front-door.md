# The README gets a console front door

Source: this round (2026-11-22, re-shot 2026-11-24). The request was a sync of the GitHub repository README
with the current tree, with the visual front door as the subject: the shell changed under
ADR-0020, the community surface reopened under ADR-0022, and the README still led with
captures and a live-demo screenshot from the pre-Forge build. The first capture pass ran on
2026-11-22; while the pull request was open, `master` took the C2-C4 slices (#58-#60) and
ADR-0023's console palette, so the branch was rebased and every capture re-shot against the
new layout on 2026-11-24.

## What changed

The README's first screen now opens with a mark, the CI/test/licence badges, three
for-the-badge calls to action (live demo, Chaos Theatre, run locally), a theme-aware
resilience banner, one real review capture, and a four-card thesis table. A new **Product
tour** section carries dark/light pairs for the community feed, the Chaos Theatre, the
profile score curve and the My Creations library. The live-demo section now shows the
current showcase post and states that the hand-rebuilt instance can lag `master`.

The old `recorded-review.png` (pre-Forge cyan shell) is left in the tree as history but is
no longer referenced by the README.

## Capture provenance

All captures are 1440 x 1000, taken 2026-11-24 with Playwright's Chromium against this
working tree. The frontend was served by Vite from `frontend/`; the theme was set through
`localStorage['nexus-vibe-theme']` and the language through `localStorage['nexus-vibe-lang'] = 'en'`.
The React Query devtools button was hidden for the shot; no product markup was edited.

| Asset | Route | Backend | What the capture proves |
|---|---|---|---|
| `home-{dark,light}.png` | `/` | production seed on `:8080`, proxied at `:5175` | Mission control, the recorded-review showcase, the theatre entry, system pulse, the recent-reviews rail and the hot feed |
| `review-{dark,light}.png` | `/post/2104516266387906562` | production seed | A stored review: 5/10 -> 50/100, severity low, Approved |
| `theatre-{dark,light}.png` | `/theatre` | dev H2 on `:8082` with `DEMO_ENDPOINTS_ENABLED=true`, proxied at `:5174` | The C4 case rail and stage: five cases, three honesty counters, and the fix-challenge at 3/10, severity high |
| `profile-{dark,light}.png` | `/user/1` | dev H2 | The score curve drawn from one review record, with the narrative stats sentence |
| `creations-{dark,light}.png` | `/creations` | dev H2 | Posts, prompt forks and device-local drafts in one tabbed library |
| `showcase-{dark,light}.png` | `/post/900000000000000001` | production seed | The deliberately unsafe cache, 30/100, severity high, Needs Review |
| `banner.svg`, `banner-light.svg`, `logo.svg` | n/a | n/a | Original vectors in the ADR-0023 palette; no screenshot state |

The theatre/profile/creations captures run on the H2 development stack, where search and
AI review report as degraded because Elasticsearch and Ollama are not part of that stack.
That is a real capability state, not a mock; the README says which captures use it and the
banner was dismissed before each shot, as a visitor can.

## Live-demo honesty

The ngrok instance was still serving `APP_TAG=20261115-01` (pre-Forge, pre-console) when
this round re-ran on 2026-11-24: `/theatre` still returned the SPA's 404 and the shell was
the old cyan one. The README now says the instance is rebuilt by hand and can lag `master`,
and that `/theatre` appears only after a rebuild. Redeploying is an operator action, not
part of a README round.

## Acceptance

- Every image in `README.md` resolves from the repository root and renders non-blank.
- Dark and light variants are selected with `<picture>` + `prefers-color-scheme`.
- Every visible score, severity and verdict in a capture matches the stored record or the
  current tree's theatre fixture.
- The live-demo caveat names the deployment state instead of implying the live box is
  already on this tree.
- The first screen still reads as a README in raw Markdown: the HTML is limited to the
  hero, the thesis cards and the themed image pairs.

## Files

`README.md`, `docs/assets/readme/{logo,banner-light}.svg`,
`docs/assets/readme/{home,review,theatre,profile,creations,showcase}-{dark,light}.png`,
`docs/tickets/readme-forge-front-door.md`, `docs/INDEX.md`, `CHANGELOG.md`.

## Rejected

- **A painted score-92 hero.** No run produced one; the real approved capture is 50/100.
  The substitution rule is the repository's own, and ADR-0010 exists to enforce it.
- **A dark-only gallery.** GitHub has a light theme; shipping only dark images makes the
  front door look broken for half its readers. The pairs are real renders, not recolours.
- **Replacing the live demo with a screenshot claim.** The demo URL stays; the caveat says
  what the instance currently is.
- **Redeploying the home machine from a documentation round.** The change is an operator
  action and would silently move the live URL under the README's feet.
- **Deleting the pre-Forge captures.** They are the record of what the old shell looked
  like; ADR-0020 keeps old screenshots as history rather than rewriting them.
- **Keeping the first-pass captures after the rebase.** The C2-C4 slices changed the
  detail column, the theatre rail and the home sidebar; shipping the 2026-11-22 images
  would have advertised a layout `master` no longer had.
- **Cropping the theatre to the old five-card grid.** The case rail and the stage are now
  the screen; the capture shows what a reader opening `/theatre` actually gets.

## Verification

```text
python -c "import re,pathlib; t=pathlib.Path('README.md').read_text(encoding='utf-8'); \
  refs=sorted(set(re.findall(r'docs/assets/[A-Za-z0-9_./-]+\.(?:png|svg)', t))); \
  print('\n'.join(f'{p}: {pathlib.Path(p).exists()}' for p in refs))"
  -> every referenced asset True
python -c "import xml.etree.ElementTree as ET; \
  [ET.parse(p) for p in ['docs/assets/readme/logo.svg','docs/assets/readme/banner-light.svg']]"
  -> both parse as XML
```

The captures can be re-shot by starting the dev backend on `:8082` with
`DEMO_ENDPOINTS_ENABLED=true`, pointing a Vite proxy at `:8082` (theatre/profile/creations)
or at the compose stack's `:8080` (home/review/showcase), and driving Playwright at
1440 x 1000 with the theme and language keys above.

The rebase was checked against `origin/master` at `c85b59e`; the frontend count in that
tree is 175 tests / 34 files, which is what the README badge and Testing section print.
