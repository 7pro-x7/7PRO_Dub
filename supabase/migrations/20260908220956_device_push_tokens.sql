CREATE TABLE IF NOT EXISTS device_push_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  token TEXT NOT NULL UNIQUE,
  platform TEXT NOT NULL DEFAULT 'android' CHECK (platform IN ('android', 'ios')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_device_push_tokens_user_id ON device_push_tokens(user_id);

ALTER TABLE device_push_tokens ENABLE ROW LEVEL SECURITY;

CREATE POLICY device_push_tokens_owner_all ON device_push_tokens
  FOR ALL
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

REVOKE ALL ON device_push_tokens FROM anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON device_push_tokens TO authenticated;

CREATE OR REPLACE FUNCTION register_push_token(p_token TEXT, p_platform TEXT DEFAULT 'android')
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'UNAUTHORIZED';
  END IF;

  INSERT INTO device_push_tokens (user_id, token, platform)
  VALUES (auth.uid(), p_token, p_platform)
  ON CONFLICT (token) DO UPDATE
    SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform, updated_at = now();
END;
$$;

REVOKE ALL ON FUNCTION register_push_token(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION register_push_token(TEXT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION unregister_push_token(p_token TEXT)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  DELETE FROM device_push_tokens WHERE token = p_token AND user_id = auth.uid();
END;
$$;

REVOKE ALL ON FUNCTION unregister_push_token(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION unregister_push_token(TEXT) TO authenticated;
;
