# Glossary

## GST return preparation

**Prepared return**:
The complete set of GSTR-1 (or IFF) tables for one workflow period, assembled from the finalized day book plus the accountant's fill-ins. It is what gets frozen at Finalize and what every filing channel submits.
_Avoid_: draft return, GSTR-1 draft, line-item draft

**Return table**:
One numbered section of the return (B2B, B2CL, B2CS, Exports, Nil/Exempt, CDNR/CDNUR, Advances, Adjustments, HSN summary, Documents issued, and the amendment variants).
_Avoid_: section, tab, bucket

**Fill-in**:
A value the accountant enters in Prepare for a return attribute the day book does not carry (HSN, unit, quantity, place of supply, export type, shipping bill, SEZ or deemed-export flag), attached to one voucher or to one ledger as a default. It never changes the day book.
_Avoid_: override, manual edit, patch

**Reporting class**:
The return table a voucher routes to (for example B2B, B2CL, B2CS), decided from the counterparty's registration, the place of supply and the invoice value.
_Avoid_: bucket, category (already used for ledger category)

**Amendment**:
A correction to something an earlier return already reported, made in the amendment tables of a later return. It always starts from an accepted amendment follow-up.
_Avoid_: revision (reserved for returns that have a statutory revised form, such as 26Q), revised return

**Late reporting**:
Reporting for the first time, in the current return, a voucher dated in an already-finalized period. It is not an amendment because nothing was reported before.
_Avoid_: backdated amendment

**Validation result**:
An error or warning about the prepared return itself. Errors block the portal file download and Finalize; warnings do not. It is separate from a finding, which is about the day book.
_Avoid_: finding (reserved for day-book, masters and tamper findings), gate

**Portal file**:
The GSTN-format JSON of a prepared return, made for upload on the GST portal. It is regenerated on demand from the frozen prepared return; only its hash is kept.
_Avoid_: export, offline JSON, return JSON

**Portal upload**:
The accountant's act of uploading the portal file on the GST portal. It is logged as an event before the return is filed. It is not a workflow stage.
_Avoid_: filing (filing is the act that produces the ARN)

**Filing channel**:
The route by which a prepared return reaches the portal: the accountant uploads the portal file, or (later) a GSP adapter submits the same payload. A channel only submits; it does not fetch anything.
_Avoid_: gateway (the spec's `PortalGateway` also fetched IMS and 2B; that stays separate), submission mode

**IFF (Invoice Furnishing Facility)**:
An optional monthly return for months 1 and 2 of a QRMP quarter that reports only B2B and CDNR (and their amendments). Its documents are left out of the quarter's GSTR-1. It is its own workflow type (N7a).
_Avoid_: monthly GSTR-1 for QRMP filers

## PAN

Permanent Account Number issued by the Income Tax Department: ten characters in the
format `AAAAA9999A` (five letters, four digits, one letter). Tally exports it on a ledger
as `incometaxnumber`. Lowercase or longer values found in Tally are invalid and are what
the `PAN_FORMAT` masters rule flags.

## PAN override

A company-level corrected PAN for one ledger, stored once in `tds_pan_overrides` and keyed
by the organization and the normalized ledger name. It is written when an accountant
approves a `PAN_MISSING`/`PAN_INVALID` TDS finding or a `PAN_FORMAT` masters finding.
TDS analysis overlays it on the ledger; the masters rule suppresses the finding while an
override exists; the Tally write-back records a change item from this same row. It is not a
`preconfigured_masters` field and does not create a `MasterEditLog` row.

## TAN

Tax Deduction and Collection Account Number issued by the Income Tax Department: ten
characters in the format `AAAA99999A` (four letters, five digits, one letter). Super
Accountant stores a debtor's TAN after trimming and uppercasing; a blank value means no
TAN. It is distinct from the company's own TAN in `Organization`.

## Debtor TAN

The TAN of a customer (Tally ledger under Sundry Debtors) that deducts TDS on payments to
the company, stored once in `debtor_tans` per organization and normalized ledger key. It
also keeps the Tally ledger GUID so the TAN follows a ledger rename. Every role can view
debtor TANs; only Owner and Accountant can edit them, while Operators propose a TAN by
resolving a `DEBTOR_TDS_RECEIVABLE_MISSING_TAN` finding. The stored TAN is the foundation
for later matching against 26AS.
_Avoid_: customer TAN (say debtor TAN), deductee TAN (reserved for TDS deducted by the company)

## Tally write-back

**Change item**:
One intended Tally ledger-field change: (organization, ledger, field PAN) from a baseline value to a new value, with source, approver and status. Rows are never deleted.
_Avoid_: change journal entry, diff

**Baseline**:
The PAN the latest completed masters upload held for that ledger when the correction was approved (raw, trimmed; blank is null). It may be an invalid over-length value; the column is 255 chars like `upload_ledger_snapshots.pan`.
_Avoid_: old value, snapshot

**Export batch**:
One generated file: who, when, which masters upload it was checked against, hash, stale confirmation, and the items with the name sent and the existence outcome of each.
_Avoid_: export, push, sync

**Existence outcome**:
FOUND (same name), RENAMED (GUID found under another name; the current name is sent), NOT_FOUND (blocked).
_Avoid_: match status, lookup result

## No PAN resolution

A deliberate resolution of a `PAN_FORMAT` masters finding (or a TDS PAN finding) saying the party
has no PAN. It is stored as an empty-string `pan` in `tds_pan_overrides`; the override row still
suppresses `PAN_FORMAT`. Every read treats blank and null alike, and the Tally write-back clears
the field by sending `incometaxnumber` as an empty string.
_Avoid_: blank PAN, empty PAN
