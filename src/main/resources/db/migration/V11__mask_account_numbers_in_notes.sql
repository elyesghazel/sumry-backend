-- Imported statements used to keep full IBANs and postal account numbers in the notes.
-- Mask what is already stored the same way the backend masks new text
-- (ch.sumry.core.privacy.AccountMasking): "CH93 0076 2011 6238 5295 7" -> "CH•• … 2957",
-- "12-345678-9" -> "••-••••••-9". Keep the patterns in sync with that file.

CREATE FUNCTION pg_temp.sumry_mask_accounts(t text) RETURNS text
    LANGUAGE plpgsql IMMUTABLE AS
$$
DECLARE
    m       text;
    compact text;
BEGIN
    IF t IS NULL THEN
        RETURN NULL;
    END IF;
    FOR m IN
        SELECT DISTINCT (regexp_matches(t,
            '((?<![A-Za-z0-9])[A-Za-z]{2}[0-9]{2}(?: ?[A-Za-z0-9]{4}){2,7}(?: ?[A-Za-z0-9]{1,3})?(?![A-Za-z0-9]))',
            'g'))[1]
    LOOP
        compact := upper(replace(m, ' ', ''));
        -- A real IBAN carries a long run of digits; "ab12 kauf coop" stays as it is.
        IF length(regexp_replace(compact, '[^0-9]', '', 'g')) >= 6 THEN
            t := replace(t, m, left(compact, 2) || '•• … ' || right(compact, 4));
        END IF;
    END LOOP;
    RETURN regexp_replace(t, '(?<![0-9-])[0-9]{2}-[0-9]{1,6}-([0-9])(?![0-9-])', '••-••••••-\1', 'g');
END;
$$;

UPDATE transactions
SET description = pg_temp.sumry_mask_accounts(description)
WHERE description IS NOT NULL
  AND description IS DISTINCT FROM pg_temp.sumry_mask_accounts(description);

UPDATE planned_transactions
SET description = pg_temp.sumry_mask_accounts(description)
WHERE description IS NOT NULL
  AND description IS DISTINCT FROM pg_temp.sumry_mask_accounts(description);
