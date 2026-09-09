# ARCHIVED — design-only history

**As of 2026-09-07, this `.planning/` tree is archived.** It is kept for
design-history reference only and is **not** the source of project status.

**Current project status lives in `docs/superpowers/`.** Note that `docs/`
is gitignored (see `.gitignore` line 74) — the captain has explicitly
accepted the risk of running project canon out of an untracked,
single-machine directory. That decision stands; this notice is not asking
anyone to revisit it.

## Why this tree was archived

- All 28 commit SHAs cited in the phase summaries under `phases/` are
  absent from every branch of this repository — they do not resolve to
  any commit here.
- `ROADMAP.md` and `STATE.md` contradict each other on the status of
  Phase 2 and Phase 4.
- The entire `phases/` tree arrived in a single squash commit
  (`b56765f`, 2026-06-29) that was already stale at the time it landed.
- The team stopped following the `.planning` process on 2026-05-07 and
  never resumed it.

Full audit: `data/sa-state-audit/report.md` (sections 2 and 3).

## What this means in practice

- Do not trust the progress tables, phase statuses, or commit references
  in `ROADMAP.md` or `STATE.md` below — they are known to be wrong.
- Do not use this tree to answer "what phase are we on" or "what's
  shipped" — ask `docs/superpowers/` instead.
- This tree has not been rewritten, reconciled, or deleted. It is left
  as-is, on purpose, as a historical record of earlier design thinking.
