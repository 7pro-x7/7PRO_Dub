-- Dead/duplicate leftovers from pre-rebuild migrations that the clean rebuild's
-- explicit-signature DROP statements missed (different arg lists than what
-- was targeted). Confirmed unused: the Android app never calls any of these
-- three by name (it either uses the differently-named/signatured versions
-- kept by the rebuild, or reads/writes the underlying tables directly), and
-- no trigger references record_subscription_earning(uuid,uuid).
DROP FUNCTION IF EXISTS public.record_subscription_earning(UUID, UUID) CASCADE;
DROP FUNCTION IF EXISTS public.subscription_earnings_summary(UUID, TEXT, DATE, DATE) CASCADE;
DROP FUNCTION IF EXISTS public.subscription_earnings_reports(UUID) CASCADE;
;
