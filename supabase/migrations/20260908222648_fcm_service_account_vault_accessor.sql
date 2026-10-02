-- Lets the classroom-call-push Edge Function (calling with the service-role key) read the
-- Firebase service account JSON out of Supabase Vault, since Deno.env secrets cannot be set
-- through this project's current tooling. SECURITY DEFINER so the function itself can read
-- vault.decrypted_secrets (normally restricted to postgres/service_role at the schema level)
-- while only ever being callable by service_role — never anon or authenticated — since the
-- content returned is a private key capable of sending push notifications as this Firebase
-- project.
CREATE OR REPLACE FUNCTION get_fcm_service_account_json()
RETURNS TEXT
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, vault AS $$
DECLARE
  v_secret TEXT;
BEGIN
  SELECT decrypted_secret INTO v_secret
  FROM vault.decrypted_secrets
  WHERE name = 'fcm_service_account_json'
  LIMIT 1;
  RETURN v_secret;
END;
$$;

REVOKE ALL ON FUNCTION get_fcm_service_account_json() FROM PUBLIC;
REVOKE ALL ON FUNCTION get_fcm_service_account_json() FROM anon;
REVOKE ALL ON FUNCTION get_fcm_service_account_json() FROM authenticated;
GRANT EXECUTE ON FUNCTION get_fcm_service_account_json() TO service_role;
;
