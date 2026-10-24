begin;
select set_config('cleantrack.test_guest_id', gen_random_uuid()::text, true);
insert into auth.users(id, aud, role, is_anonymous, raw_user_meta_data)
values (current_setting('cleantrack.test_guest_id')::uuid, 'authenticated', 'authenticated', true,
        '{"full_name":"CSP QA guest (rolled back)","mobile_number":"9000000000","role":"officer"}'::jsonb);
do $$
begin
  if not exists(select 1 from public.profiles where id=current_setting('cleantrack.test_guest_id')::uuid
    and full_name='CSP QA guest (rolled back)' and mobile_number='9000000000' and role='citizen') then
    raise exception 'FAIL: guest contact details or forced citizen role';
  end if;
end $$;
select full_name = 'CSP QA guest (rolled back)' as name_saved,
       mobile_number = '9000000000' as mobile_saved,
       role = 'citizen' as metadata_cannot_promote_role
from public.profiles where id=current_setting('cleantrack.test_guest_id')::uuid;
select set_config('request.jwt.claims', jsonb_build_object(
  'sub',current_setting('cleantrack.test_guest_id'),'role','authenticated','is_anonymous',true)::text,true);
set local role authenticated;
select count(*) = 1 as guest_sees_only_own_profile, not public.is_officer() as guest_is_not_officer from public.profiles;
do $$
begin
  if (select count(*) from public.profiles) <> 1 or public.is_officer() then
    raise exception 'FAIL: guest profile isolation';
  end if;
  begin
    update public.profiles set role='officer' where id=auth.uid();
    raise exception 'FAIL: role change unexpectedly allowed';
  exception when insufficient_privilege then null; end;
  begin
    perform * from public.officer_update_complaint(-1,now(),'In Progress','QA forbidden update',null,null);
    raise exception 'FAIL: officer RPC unexpectedly allowed';
  exception when raise_exception then
    if sqlerrm <> 'Officer access required' then raise; end if;
  end;
  begin
    perform * from public.submit_complaint(gen_random_uuid(),'Overflowing Waste Bin',
      auth.uid()::text || '/reports/missing.jpg',16.1875,81.1380,'QA coordinates',null);
    raise exception 'FAIL: report without photo unexpectedly allowed';
  exception when raise_exception then
    if sqlerrm <> 'Upload your report photo first' then raise; end if;
  end;
end $$;
reset role;
rollback;
select count(*) as remaining_qa_profiles from public.profiles where full_name='CSP QA guest (rolled back)';
