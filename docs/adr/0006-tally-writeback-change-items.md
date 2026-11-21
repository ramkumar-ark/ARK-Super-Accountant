# ADR 0006: Tally write-back records field-level change items and exports a JSON import file

Status: proposed

Supersedes the 2026-07-20 Tally export design (entity change journal, voucher-centred, day-book baseline).
That design stored pointers instead of values, never learned whether a change reached Tally, and centred on
vouchers the app does not edit. We instead keep one `tally_change_items` row per intended ledger-field change
(from, to, source, approver, status), generated from the PAN override store (ADR 0005) and exported as a
TallyPrime JSON (JSONEx) Alter file the accountant imports with Alt+O. Phase 0 on TallyPrime 7.1.0 showed a
single-field Alter changes only that field and is idempotent, but a missing ledger name returns
exceptions>0 and can leave a half-built object, so an item is exported only when its ledger is in the
latest completed masters upload. States: PENDING, EXPORTED, APPLIED, FAILED, SUPERSEDED; APPLIED is set only
by a later masters upload showing the new value, never by the download. The first version covers ledger PAN.

Consequences: masters tamper comparison must treat an applied change as expected; the existence check is a proxy
for live Tally until a local bridge exists; `change_type` and the renderer registry leave room for a later
CREATE_LEDGER change (new ledgers from findings resolution), which would not use the not-found block.
