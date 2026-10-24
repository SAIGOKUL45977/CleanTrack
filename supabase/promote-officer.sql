-- After creating and confirming the officer's Auth account, replace this email.
-- Run only as the project administrator in Supabase SQL Editor.
do $$
declare v_id uuid;
begin
  select id into v_id from auth.users where lower(email) = lower('REPLACE_WITH_OFFICER_EMAIL');
  if v_id is null then raise exception 'Create and confirm this Auth account first'; end if;
  update public.profiles set role = 'officer' where id = v_id;
  if not found then raise exception 'Run the CleanTrack migration first'; end if;
end;
$$;
