# ADR 0010: GST tax-ledger mapping gates day-book uploads

Status: proposed

Day-book intake already refuses uploads for TDS-applicable organizations with an
unsaved TDS ledger mapping (ADR 0009). GST had the same gap on the intake path:
`TaxLedgerMappingGate` was only enforced at baseline confirm and analysis time,
so a GSTIN organization could upload a day book, run analysis and only then hit
`TAX_MAPPING` findings — after the upload had already replaced earlier results
and reset the workflow to Data uploaded.

Day-book intake now also consults `TaxLedgerMappingGate` through a dedicated
`daybookBlockerMessage(organizationId)`: empty unless the organization has a
non-blank GSTIN, otherwise the existing `blockerMessage` (every GST-categorized
ledger of the latest completed masters upload has a saved mapping row, and each
of the six required areas covers IGST, CGST and SGST). GSTIN is the same
applicability signal the gate already uses everywhere, so a non-GSTIN
organization is unaffected even when its masters contain unmapped GST ledgers —
unlike analysis-time blocking, the upload refusal is scoped to organizations
the GST returns actually apply to. When both gates are incomplete the 422 body
carries both sentences; a TDS-only refusal remains byte-identical to the
pre-existing message.

The mapping view (`GET /api/v1/gst/tax-ledger-mapping`) now also returns
`gstApplicable` so the upload UI can scope its disabled state and message the
same way, mirroring the TDS gate's frontend behavior in `WorkflowUploadStep`.

Alternatives considered and rejected: gating on unmapped GST ledgers without
the GSTIN check (would block purely-TDS organizations whose masters happen to
carry GST ledgers); a single combined "compliance mapping" gate (the two gates
have different applicability signals — deductor status vs GSTIN — and must stay
independently extensible); and leaving intake ungated (accepted only for
non-GSTIN organizations, where GST analysis is out of scope anyway).
