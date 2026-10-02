-- activate_group, delete_course, delete_course_force, manage_teacher and process_approval_request
-- insert a TEXT value into audit_logs.actor_role (type app_role). Postgres refuses that, the
-- surrounding EXCEPTION block swallowed the error, and none of those actions was ever written to
-- the audit log (0 rows). Found with plpgsql_check across every function. An assignment cast lets
-- those inserts land without rewriting each function.
do $$
begin
  if not exists (
    select 1 from pg_cast
    where castsource = 'text'::regtype and casttarget = 'public.app_role'::regtype
  ) then
    create cast (text as public.app_role) with inout as assignment;
  end if;
end $$;
