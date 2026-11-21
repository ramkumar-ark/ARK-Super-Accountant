# Super Accountant feature map

How a user reaches each feature, how an agent drives it over HTTP, and what observable state proves it works. Verified against `main` controllers (GSTR-1 endpoints live in `gstr1/controllers/Gstr1WorkflowController.java`, not the old plan's paths).

| Feature | User path | Agent driving (HTTP) | Proof | Scenario |
|---|---|---|---|---|
| Sign in + roles | /login → JWT per role | `POST /api/auth/signin` as Operator and Accountant; bad password 401; unauthenticated request rejected | 200 + `role` claim matches | `sign-in` |
| Workflow lifecycle | Workflows page → create → advance → finalize/unfinalize | `POST /api/v1/workflows` {type, periodKey}, `POST /{id}/advance`, `/{id}/finalize`, `/{id}/unfinalize`, `GET /{id}/events` (WorkflowController) | stage transitions PREPARE→FINALIZED in responses and events; OPERATOR forbidden to advance (403) | `workflow-lifecycle` |
| GSTR-1 prepare (tables) | Workflow detail → Gstr1PreparePanel | `GET /api/v1/workflows/{id}/gstr1/tables` (Gstr1WorkflowController:104) | 200 object; readable by Operator too | `gstr1-tables` |
| GSTR-1 prepare (fill-ins) | Gstr1PreparePanel fill-in save | `PUT /api/v1/workflows/{id}/gstr1/fill-ins` {scope: LEDGER\|VOUCHER, key, attribute: HSN\|UQC\|QTY\|POS\|..., value} (Gstr1WorkflowController:145); blank value = delete (204) | fill-in value appears in `GET .../gstr1/tables`; Accountant-only (Operator 403) | `gstr1-fill-ins` |
| GSTR-1 validate + findings | Workflow detail → Gstr1ValidationPanel / findings | `GET /api/v1/workflows/{id}/gstr1/validation` (errors/warnings), `GET /{id}/findings`, `POST /{id}/findings/{fid}/action` | `{errors, warnings}` non-null; findings array; bogus finding id rejected | `gstr1-findings` |
| GST validation (standalone) | Upload → validate | `POST /api/gst/validate` multipart file (GstValidationController:38) | 200 `{totalVouchersAnalyzed, results}`; unauthenticated rejected | `gstr1-validate` |
| GSTR-1 download | Workflow detail → download button | `GET /api/v1/workflows/{id}/gstr1/export?includeIrp=false` (Gstr1WorkflowController:201), Accountant-only, from PREPARE onward | 200 JSON containing the fill-in value; Operator 403 | `gstr1-export` |
| Portal-upload record | Workflow detail → record portal upload | `POST /api/v1/workflows/{id}/gstr1/portal-upload` {fileHash, note} (Gstr1WorkflowController:179) | `{recorded: true}` | `filed-returns-import` |
| Filed-return JSON import | Filed-returns page → import GSTR-1 JSON | `POST /api/v1/filed-returns/gstr1/json` multipart file + form=GSTR1 (FiledReturnController:57), `GET /api/v1/filed-returns/snapshots` | 201 snapshot id; id present in snapshots list | `filed-returns-import` |
| Filed-return missing baseline | Filed-returns page → import for a period without a baseline | `POST /api/v1/filed-returns/gstr3b` period without baseline (FiledReturnImportService matchBaselineRow) | 409 message names the month and says to create a baseline in Settings > Baseline periods | `filed-returns-import` |

## TODO rows (not yet covered — add scenarios without restructuring)

| Feature | User path | Agent driving (HTTP) | Proof | Scenario |
|---|---|---|---|---|
| Masters upload pipeline | Masters page → upload day-book CSV | `POST /api/v1/uploads`, findings actions (`/uploads/{jobId}/findings/{fid}/action`, `bulk-action`), `GET /uploads/{id}/mismatches` | findings resolved in mismatches | TODO |
| Tally import | (module) | `POST /api/tally/import` synthetic XML | import summary counters | TODO |
| TDS challans | Workflow TDS tab | `/api/v1/workflows/{id}/tds/challans`, `/entries`, `/entries/map`, `/gate` (TdsWorkflowController) | gate status green after mapping | TODO |
| Amendments / IFF | Fill-ins on later period | repeat fill-in flow with amended payloads (builds 6-7 behavior) | deltas visible in tables | TODO |
| GSTR-3B | Filed-returns page | `POST /api/v1/filed-returns/gstr3b`, `/gstr3b/pdf` | snapshot created | TODO |
| Browser scenarios | GSTR-1 panels via chrome-devtools-axi | login → workflow detail → panels | screenshots + visible finding count | TODO (task 4) |

## Maintenance

- Re-check file:line anchors whenever a PR touches a controller or GSTR-1 UI.
- Scenario files live in `scenarios/`; fixtures in `scenarios/fixtures/` (synthetic, committed — never from Downloads).
