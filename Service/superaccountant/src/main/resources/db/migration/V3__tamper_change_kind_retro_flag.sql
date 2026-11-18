-- V3 Tamper change kinds: retro-active TDS flags
-- Apply BEFORE deploying the JAR that writes RETRO_FLAGGED rows.
-- Older schemas pinned change_kind to INSERTED/MODIFIED/DELETED with a CHECK
-- constraint even though the entity stores the enum through a String converter.
-- The converter has no fixed value list, so the stale constraint is dropped.
ALTER TABLE tamper_changes DROP CONSTRAINT IF EXISTS tamper_changes_change_kind_check;
ALTER TABLE tamper_findings DROP CONSTRAINT IF EXISTS tamper_findings_change_kind_check;
