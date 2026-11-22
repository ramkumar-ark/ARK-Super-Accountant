# ADR 0005: Masters PAN findings reuse the TDS PAN override store

Status: proposed

Masters review cannot correct a ledger's PAN because PAN is not a `preconfigured_masters` field, and TDS already owns `tds_pan_overrides`; the Tally write-back (ADR 0007) needs one source for the corrected value. We add a `PAN_FORMAT` rule to the masters rules that flags invalid PAN values found in Tally and dispatches its correction, through the masters-declared `PanOverrideGateway`, to `PanFormatOverrideService`; the gateway is implemented in `tds` by `TdsPanOverrideStore`, the single upsert into `tds_pan_overrides`, which `TdsFindingApplier` now uses too. `PAN_FORMAT` gets an explicit empty workflow scope because it is about ledger data, not a return, but stays in the day-book gate count; the rule suppresses the finding while an override exists.

Consequences: one store and one format constant (`PanFormat`) serve TDS and masters; masters does not depend on `tds`; the rule depends on the gateway, a deliberate coupling to approved data. Bulk approval cannot supply a typed PAN, and an empty PAN stays unflagged until TDS or a later signal covers it.
