-- Adds manual drag-and-drop ordering for two lists that share the same table:
--   * kind = 'PLACEMENT' -> the admin/owner console's list of level tests
--   * kind = 'EXERCISE'  -> each teacher's own list of exercises
-- One column and one backfill covers both, since the app already scopes every query by kind.

ALTER TABLE public.placement_tests ADD COLUMN IF NOT EXISTS sort_order integer NOT NULL DEFAULT 0;

-- Placement tests are shown as a single shared list in the admin console, so they share one order.
WITH ranked AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY created_at ASC) - 1 AS rn
  FROM public.placement_tests
  WHERE kind = 'PLACEMENT'
)
UPDATE public.placement_tests pt
SET sort_order = ranked.rn
FROM ranked
WHERE pt.id = ranked.id;

-- Each teacher's exercises are their own list, so the order restarts per owner.
WITH ranked AS (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY owner_id ORDER BY created_at ASC) - 1 AS rn
  FROM public.placement_tests
  WHERE kind = 'EXERCISE'
)
UPDATE public.placement_tests pt
SET sort_order = ranked.rn
FROM ranked
WHERE pt.id = ranked.id;

CREATE INDEX IF NOT EXISTS idx_placement_tests_kind_sort_order
  ON public.placement_tests (kind, sort_order);
;
