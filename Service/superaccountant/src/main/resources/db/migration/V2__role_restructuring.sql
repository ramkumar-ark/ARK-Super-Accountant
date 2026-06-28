-- V2 Role Restructuring (Phase 2 AUTH-01)
-- Apply BEFORE deploying the JAR with the updated ERole enum.
-- Idempotent: safe to re-run. Uses WHERE EXISTS / WHERE NOT EXISTS guards.

-- 1. Remap user_organizations.role string values
UPDATE user_organizations SET role = 'ROLE_OPERATOR'   WHERE role = 'ROLE_DATA_ENTRY_OPERATOR';
UPDATE user_organizations SET role = 'ROLE_ACCOUNTANT' WHERE role = 'ROLE_CASHIER';

-- 2. Remap organization_invites.role string values
UPDATE organization_invites SET role = 'ROLE_OPERATOR'   WHERE role = 'ROLE_DATA_ENTRY_OPERATOR';
UPDATE organization_invites SET role = 'ROLE_ACCOUNTANT' WHERE role = 'ROLE_CASHIER';

-- 3. Insert new role rows (ROLE_OPERATOR, ROLE_AUDITOR_CA) if missing
INSERT INTO roles (name)
SELECT 'ROLE_OPERATOR'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_OPERATOR');

INSERT INTO roles (name)
SELECT 'ROLE_AUDITOR_CA'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_AUDITOR_CA');

-- 4. Repoint users.role_id from obsolete roles to ROLE_OPERATOR (data-entry users)
--    and ROLE_ACCOUNTANT (former cashiers — safer than OPERATOR; stakeholders accept upward shift).
UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'ROLE_OPERATOR')
WHERE role_id IN (SELECT id FROM roles WHERE name = 'ROLE_DATA_ENTRY_OPERATOR');

UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'ROLE_ACCOUNTANT')
WHERE role_id IN (SELECT id FROM roles WHERE name = 'ROLE_CASHIER');

-- 5. Delete obsolete role rows (must be AFTER step 4; FK would otherwise block)
DELETE FROM roles WHERE name IN ('ROLE_CASHIER', 'ROLE_DATA_ENTRY_OPERATOR');
