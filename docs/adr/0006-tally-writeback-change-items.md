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

## No PAN resolution

A PAN correction may deliberately record that the party has no PAN, shown to the reviewer as
"No PAN / not available". It is stored as an empty string in `tds_pan_overrides.pan`; the row's
existence still suppresses the `PAN_FORMAT` finding, and the empty value means "no PAN", exactly
like a ledger whose PAN is null or blank today. The empty value is only reachable through the
explicit `noPan` choice on a `PAN_FORMAT` ACKNOWLEDGE: a blank without that choice is a 400, so
an accidental blank never becomes a stored correction.

Every read treats blank and null as the same no-PAN value. The TDS analysis overlay copies null
onto its detached snapshot for a blank override, and `TdsEngine` reports a null or blank PAN as
`PAN_MISSING` (never `PAN_INVALID`) with the higher rate applying. The write-back records
`newValue = ""` and compares canonically, so clearing an already-empty PAN is a no-op and a
reconciled blank Tally PAN matches an empty `newValue`.

Tally clearing rule: the import renderer always includes `incometaxnumber`, even when its value
is empty. An Alter message that includes the key with an empty value clears the field; omitting
the key would leave the existing Tally value untouched.
