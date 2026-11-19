# ADR 0004: Return-only attributes live in fill-ins, outside the day book and the content hash

Status: proposed

Attributes GSTR-1 needs that Tally does not give us are stored as workflow-scoped fill-ins and never written to the day book. New captured fields (place of supply, nature, registration, ledger HSN, item lines) are persisted on the parsed voucher but excluded from `contentHash`, as narration already is, so adding them does not raise a tamper finding on every frozen voucher. Considered: pushing fixes back to Tally (rejected: no write path, and the change journal is a Tally export design that is not built).
