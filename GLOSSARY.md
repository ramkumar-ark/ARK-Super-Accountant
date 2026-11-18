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
