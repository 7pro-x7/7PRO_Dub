-- ============================================================================
-- Fix confirm_order_payment permission for service-role callers
--
-- When the checkout edge function confirms a zero-total order (coupon covers
-- the full price), it calls confirm_order_payment with the service-role key.
-- PostgREST checks EXECUTE permission before running the function, and the
-- service_role was never granted it — causing "permission denied".
--
-- The function itself is SECURITY DEFINER and does not check auth.uid(),
-- so the only fix needed is the GRANT below.
-- ============================================================================

GRANT EXECUTE ON FUNCTION public.confirm_order_payment(UUID, TEXT, TEXT, TEXT, JSONB) TO service_role;
GRANT EXECUTE ON FUNCTION public.confirm_order_payment(UUID, TEXT, TEXT, TEXT, JSONB) TO authenticated;
