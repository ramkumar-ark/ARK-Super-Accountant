# ADR 0006: Store debtor TANs separately for later 26AS reconciliation

Status: proposed

Customers can deduct TDS on payments to the company and report it against the company's
TAN; matching those entries needs the customer's own TAN. Tally's ledger master does not
carry it and the TDS module's `tds_pan_overrides` answers a different question (the
counterparty PAN used when the company deducts). We add `debtor_tans`, one row per
organization and normalized ledger key, holding the TAN, the Tally ledger GUID, the
ledger name at write time, and the finding or user that last changed it. The GUID lets a
later masters upload carry the TAN across a ledger rename even though the key changes;
a ledger-row match binds a GUID to an existing key-only row. The same store is exposed
through `DebtorTanGateway` to masters and to the TDS finding applier, so the masters upload
can sync renames without depending on the TDS module.

Debtor TANs are managed on a dedicated Masters tab and from
`DEBTOR_TDS_RECEIVABLE_MISSING_TAN` findings. That finding is informational (LOW) and is
raised only when a voucher debits a TDS Receivable ledger and credits a Sundry Debtor with
no stored TAN. Everyone may view debtor TANs; Owner and Accountant may edit them directly,
while Operators propose a TAN through the finding review flow. 26AS matching itself is out
of scope for this decision.
