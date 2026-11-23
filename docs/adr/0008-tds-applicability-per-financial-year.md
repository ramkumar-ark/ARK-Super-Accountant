# ADR 0008: TDS deductor applicability per financial year

Status: proposed

Whether an organization must deduct TDS is a property of a financial year, not a global
switch: companies, firms, trusts, AOPs, BOIs and AJPs always deduct, while individuals/HUFs
depend on the preceding year's turnover (₹1 crore business / ₹50 lakh profession), so the
answer can change only at a financial-year boundary. We store one row per organization and
financial year in `tds_applicability_years` (unique per org + year): `applicable`, a basis
(`ENTITY_TYPE`, `DECLARED`, `CONFIRMED_FROM_BOOKS`), the declared preceding-year turnover
and its nature, who decided it and when, and an optional note. Rows are never deleted; a
year that stops being a deductor year is hidden, not erased, and a year with a finalized
TDS workflow cannot be switched off.

The effective deductor class is known without extra input: a stored `deductorType` wins;
otherwise the 4th character of the PAN decides (C→K, F→F, A→P, T→T, B→B, J→J, P/H→Q).
The resolver (`TdsApplicabilityService`), in order:

1. effective code known and not `Q` → applicable, basis `ENTITY_TYPE`;
2. a recorded row for the year → the row's values;
3. the organization has no rows at all → applicable, basis `LEGACY_DEFAULT`
   (existing organizations keep today's all-on behaviour);
4. otherwise the nearest recorded year by financial-year distance — earlier preferred on
   a tie — carries its answer forward with basis `CARRIED_FORWARD` and `needsAnswer=true`
   until the user answers for that year.

The read API (`GET /api/v1/organizations/{id}/tds-applicability`, any member) returns the
effective code and source, the TAN, and the resolved applicability for every year from
the coverage-start financial year through the current one. When TDS does not apply for a
year, deductor-side features are hidden for that year (TDS masters findings, TDS workflows,
TDS ledger mapping, deductor settings) while receivable-side checks such as
`DEBTOR_TDS_RECEIVABLE_MISSING_TAN` stay on (ADR 0006); the PAN-only obligations (194M,
194-IB, 194-IA, 194S) that need no TAN are advisories and never make the organization a
deductor. Gating, registration capture and prompts are built in later slices.
