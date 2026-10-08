-- CSP demonstration: name + mobile entry, backed by a private guest identity.
-- Run after the initial CleanTrack migration. This does not enable the Auth provider.
alter table public.profiles add column if not exists mobile_number text
  check (mobile_number is null or mobile_number ~ '^[6-9][0-9]{9}$');
alter table public.complaints add column if not exists citizen_mobile_number text
  check (citizen_mobile_number is null or citizen_mobile_number ~ '^[6-9][0-9]{9}$');

create or replace function public.create_citizen_profile()
returns trigger language plpgsql security definer set search_path = '' as $$
declare v_mobile text := trim(new.raw_user_meta_data->>'mobile_number');
begin
  insert into public.profiles(id, full_name, role, mobile_number)
  values (new.id, left(coalesce(nullif(trim(new.raw_user_meta_data->>'full_name'), ''),
                              nullif(split_part(new.email, '@', 1), ''), 'Citizen'), 100),
          'citizen', case when v_mobile ~ '^[6-9][0-9]{9}$' then v_mobile else null end);
  return new;
end;
$$;
revoke all on function public.create_citizen_profile() from public, anon, authenticated;

create or replace function public.submit_complaint(
  p_submission_id uuid, p_category text, p_photo_path text, p_latitude double precision,
  p_longitude double precision, p_location_address text, p_description text default null
) returns setof public.complaints language plpgsql security definer set search_path = '' as $$
declare v_user uuid := auth.uid(); v_name text; v_mobile text; v_role text; v_result public.complaints;
begin
  if v_user is null then raise exception 'Enter your details before submitting a report'; end if;
  select * into v_result from public.complaints where citizen_id = v_user and submission_id = p_submission_id;
  if found then return next v_result; return; end if;
  select full_name, mobile_number, role into v_name, v_mobile, v_role from public.profiles where id = v_user;
  if v_name is null then raise exception 'Citizen profile is missing'; end if;
  if v_role <> 'citizen' then raise exception 'Use the citizen app to submit a report'; end if;
  if v_mobile is null then raise exception 'Enter a valid citizen mobile number before submitting'; end if;
  if p_photo_path not like v_user::text || '/reports/%'
     or not exists(select 1 from storage.objects where bucket_id = 'complaint-images' and name = p_photo_path)
  then raise exception 'Upload your report photo first'; end if;
  insert into public.complaints(submission_id, citizen_id, citizen_name, citizen_mobile_number,
    category, photo_path, latitude, longitude, location_address, description)
  values (p_submission_id, v_user, v_name, v_mobile, p_category, p_photo_path,
          p_latitude, p_longitude, trim(p_location_address), nullif(trim(p_description), ''))
  on conflict (citizen_id, submission_id) do nothing returning * into v_result;
  if not found then
    select * into v_result from public.complaints where citizen_id = v_user and submission_id = p_submission_id;
  end if;
  return next v_result;
end;
$$;
revoke all on function public.submit_complaint(uuid,text,text,double precision,double precision,text,text) from public, anon, authenticated;
grant execute on function public.submit_complaint(uuid,text,text,double precision,double precision,text,text) to authenticated;

comment on column public.profiles.mobile_number is 'Self-entered contact number for CSP demo follow-up. Not identity or OTP verified.';
comment on column public.complaints.citizen_mobile_number is 'Contact snapshot at submission; not identity verified. Visible through existing complaint RLS only.';
