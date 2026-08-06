-- Make phone mandatory (exactly 10 digits enforced at the application layer).
-- Backfill any legacy NULL/empty phone values so the NOT NULL constraint can be added.
UPDATE students SET phone = '0000000000' WHERE phone IS NULL OR btrim(phone) = '';
ALTER TABLE students ALTER COLUMN phone SET NOT NULL;
