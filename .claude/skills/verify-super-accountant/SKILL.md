---
name: verify-super-accountant
description: Launch the Super Accountant product (backend + frontend) on an isolated database and ports, drive user-facing HTTP scenarios, capture machine-readable evidence, and clean up only what was started. Use before handoff of any user-facing PR and during no-mistakes live validation.
---

# verify-super-accountant

Project-wide verification framework: proves user-facing behavior against the **running product**, not just tests. pstack-style — every claim in a PR verdict should be backed by a driven scenario.

## When to run it

- Before handoff of any PR that adds/changes a user-facing endpoint or UI: run the scenarios for the touched feature (`scripts/drive <scenario>` or `all`).
- During no-mistakes live validation: HTTP scenarios are mandatory for touched features; browser scenarios (later build) only for GSTR-1 UI changes.
- A weekly or per-milestone full pass: `scripts/drive all` re-validates the whole feature map.

## Usage

```bash
bash .claude/skills/verify-super-accountant/scripts/up          # start isolated instance (auto slug from worktree name)
bash .claude/skills/verify-super-accountant/scripts/up --tag lane-a
bash .claude/skills/verify-super-accountant/scripts/drive all   # run every scenario
bash .claude/skills/verify-super-accountant/scripts/drive sign-in workflow-lifecycle
bash .claude/skills/verify-super-accountant/scripts/down        # kill own pids + drop verify DB
```

Run from the worktree root (slug defaults to the worktree directory name). `up` loads `~/.config/super-accountant/db.env` without printing values, creates `superaccountant_verify_<slug>` on local Postgres (never touches the default `superaccountant` DB), starts the backend on `8080 + offset(slug)` with a per-slug `DB_URL`, and the Vite dev server on `5173 + offset(slug)` with `SA_API_PORT` pointing its proxy at the backend. Health-checked within 120s; marker written to `evidence/instance-<slug>.json` so `down` kills exactly the pids recorded there.

## Scenarios

`scenarios/*.sh` are HTTP-only (curl + jq). Each run writes:

- `evidence/run-<ts>/scenarios.jsonl` — one line per step: `{scenario, step, method, url, status, assertion, ok, ts}`
- `evidence/run-<ts>/summary.json` — `{passed_steps, failed_steps, scenarios_failed, scenarios_run, product_version_sha}`
- `evidence/run-<ts>/http.log`, per-scenario `.out` transcripts

Cite `summary.json` + `scenarios.jsonl` paths in PR verdicts and live-validation reports.

## Feature map and maintenance

`feature-map.md` maps every user-facing feature to its agent-driving HTTP surface and proof. Rules:

1. A PR that adds/changes a user-facing endpoint or UI must update the affected feature-map row and scenario in the same PR (re-check file:line anchors).
2. A failing scenario may **not** be "fixed" by editing the scenario to expect broken behavior — that masks product bugs. Fix the product or stop.
3. Not-yet-covered modules (TDS, Tally import, masters, compliance) are listed as TODO rows; adding them needs no restructuring.
