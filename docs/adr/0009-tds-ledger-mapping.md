# ADR 0009: TDS ledger mapping

Status: proposed

Implicit recognition of TDS ledgers fails for real organizations. In the Narayanan
books, the payable ledger is `TDS Payable - 194C A/c` under `tds|duties & taxes`, while
`TDS (FY:24-25) A/c` sits under `loans & advances (asset)` as a receivable — and other
organizations name, group and flag their ledgers differently, so guessing a ledger's role
(and which section it covers) from name alone produces wrong TDS analysis, blocked
workflows and wrong returns.

We store a separate per-organization mapping table, `tds_ledger_mappings` (one row per
organization + ledger key, unique), mirroring `tax_ledger_mappings`. Each row records the
ledger name and key, a role — `TDS_PAYABLE`, `TDS_RECEIVABLE` or `NOT_TDS` — and one
nullable section: a `TDS_PAYABLE` row may name a single section code, or leave it null to
mean a combined payable ledger whose section is inferred per voucher at analysis time.
Receivable rows carry no section. The `section` column stores codes validated against
`tds_section_configs` today and is designed to reference the configurable statutory
sections table planned separately (sa-statutory-config-plan).

Mappings are authoritative from day one: there is no legacy name- or category-based
fallback at analysis time. An unmapped TDS payable ledger blocks TDS analysis and
workflow advance for the organization until it is mapped; an unmapped TDS receivable
ledger only warns. For TDS-applicable organizations an unmapped payable also refuses
day-book uploads (422) until the mapping is saved. Candidates exclude GST-taxtype
ledgers and EXPENSE, PURCHASE and GST-category ledgers. Suggestions are computed from the ledger name, its parent group and
group chain, Tally tax type, and the nature-of-payment section code — nothing else — so the user can
accept or correct them before a row is saved (who saved it and when are recorded).

Alternatives considered and rejected: a legacy name/category fallback (re-introduces the
wrong-analysis problem this table exists to fix); a per-financial-year mapping (ledger
names do not change with the year, and the per-year dimension is already covered by the
deductor-year applicability record); and extending `tax_ledger_mappings` (GST and TDS
roles, heads and sections are different vocabularies, and TDS rows need the nullable
section that GST rows do not have).
