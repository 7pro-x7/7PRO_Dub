-- The security advisor flags a mutable search_path on both functions this feature just
-- touched. Pinning it removes the (largely theoretical, but free to close) risk of another
-- schema on the search path shadowing an unqualified identifier inside these functions.
ALTER FUNCTION public.conversation_score(jsonb, text) SET search_path = public, pg_temp;
ALTER FUNCTION public.validate_test_question_payload() SET search_path = public, pg_temp;
;
