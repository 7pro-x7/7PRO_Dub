-- The security advisor (get_advisors) flagged a mutable search_path on both functions the
-- free-answer migration just touched. Pinning it closes the (largely theoretical, but free to
-- close) risk of another schema on the search path shadowing an unqualified identifier inside
-- them. The other 8 functions the same lint flagged predate this feature and are left alone —
-- out of scope here.
--
-- APPLIED DIRECTLY to the live 7pro-x7 project on 2026-09-17 via the Supabase MCP tools.

ALTER FUNCTION public.conversation_score(jsonb, text) SET search_path = public, pg_temp;
ALTER FUNCTION public.validate_test_question_payload() SET search_path = public, pg_temp;
