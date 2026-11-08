# fe-3-group-d — frozen contract: command palette dual destination

Status: **frozen, 2026-11-09.** Base commit `1ce682a`. Ticket:
[frontend-polish-round-3](../tickets/frontend-polish-round-3.md), item 9.

## What the ticket got wrong, and what the round does about it

The ticket declined this item on the grounds that "the tree has no second destination".
That was true of *saving a note* — this project has no notes product. It is not true of the
palette's own contents: a post or channel row already has a second thing a reader
reasonably wants from it, which is its address, not the page. So the dual destination here
is **open** (Enter) versus **copy the link** (modifier + Enter), declared per item rather
than invented for all of them.

`CommandPalette` binds Enter to `run` on the selected row
([CommandPalette.tsx:188-206](../../frontend/src/components/CommandPalette.tsx)) and each
item carries a single `run` closure (lines 33-40).

## Scope

A palette item may declare a `secondary` action with its own label. The footer shows the
modifier only when the selected row has one. Rows without a secondary behave exactly as
today.

## Acceptance criteria

- D1: `PaletteItem` gains an optional `secondary: { label: string; run: () => void }`.
  Post rows and channel rows declare "Copy link"; action rows do not.
- D2: `Ctrl+Enter` and `Meta+Enter` invoke the selected row's `secondary` when it has one
  and the primary `run` when it does not — so the modifier never becomes a dead key.
  Asserted for both modifier keys.
- D3: The copied value is the absolute URL for that post or channel (built from
  `window.location.origin` plus the route), not a relative path; asserted on the clipboard
  payload.
- D4: Copying closes the palette and reports through the existing toast store, in the same
  voice as the rest of the app; no `alert()`.
- D5: The footer hint for the secondary appears only while the selected row has one, and
  names the same modifier the handler listens for. Asserted by rendering with a post row
  selected and with an action row selected.
- D6: Keyboard navigation, Enter, Escape, the focus trap and focus restoration are
  unchanged; the existing palette tests pass unmodified.
- D7: `aria-activedescendant` and the roving selection keep pointing at the row the
  secondary would act on — the two actions can never disagree about which row is selected.

## Allowed paths

- `frontend/src/components/CommandPalette.tsx`
- `frontend/src/components/CommandPalette.test.tsx` (new)
- `frontend/src/components/` — nothing else
- **`README.md` is not yours.** Four groups run at once and the count badge is one line;
  the integrator updates it once after the merge. Report your counts instead.

## Forbidden

- Adding a new product surface (notes, favourites, bookmarks) to give the modifier
  something to do.
- Changing which rows exist, their order, or the sections they belong to.
- Touching routing, the auth store, or any backend file.
- Weakening, deleting or skipping an existing assertion; disabling lint or typecheck.
- Pushing, opening a PR, or editing this file. **Do commit** on your own branch: the
  acceptance chain binds evidence to a HEAD SHA, and an uncommitted tree has none.

## Verification commands

```bash
cd frontend && npm run test && npm run lint && npm run build
```

## Rejected alternatives

- **Shift+Enter as the modifier.** It is the textarea newline reflex and this palette has
  no textarea; `Ctrl`/`Meta`+Enter is the conventional "do the other thing" and is what the
  contract tests.
- **A second visible button per row.** The palette is a keyboard surface; a button per row
  would double its height to serve a mouse the arrow keys already serve.
