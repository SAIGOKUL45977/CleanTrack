-- CleanTrack pilot: run in Supabase SQL Editor as the project administrator.
-- A citizen can read only their reports; authorized officers can review the pilot queue.
begin;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null check (char_length(full_name) between 1 and 100),
  role text not null default 'citizen' check (role in ('citizen', 'officer')),
  created_at timestamptz not null default now()
);

create or replace function public.create_citizen_profile()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles(id, full_name, role)
  values (new.id, left(coalesce(nullif(trim(new.raw_user_meta_data->>'full_name'), ''),
                              nullif(split_part(new.email, '@', 1), ''), 'Citizen'), 100), 'citizen');
  return new;
end;
$$;
drop trigger if exists cleantrack_new_user on auth.users;
create trigger cleantrack_new_user after insert on auth.users
for each row execute function public.create_citizen_profile();
insert into public.profiles(id, full_name)
select id, left(coalesce(nullif(trim(raw_user_meta_data->>'full_name'), ''),
                        nullif(split_part(email, '@', 1), ''), 'Citizen'), 100)
from auth.users on conflict (id) do nothing;

create or replace function public.is_officer()
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.profiles where id = auth.uid() and role = 'officer');
$$;

create table if not exists public.complaints (
  id bigint generated always as identity primary key,
  submission_id uuid not null,
  citizen_id uuid not null references public.profiles(id),
  citizen_name text not null,
  category text not null check (category in ('Overflowing Waste Bin', 'Plastic Waste Accumulation',
    'Organic/Food Waste', 'Dry Leaf & Bio Litter', 'E-Waste Disposal')),
  photo_path text not null,
  resolution_photo_path text,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  location_address text not null check (char_length(location_address) between 1 and 500),
  description text check (char_length(description) <= 2000),
  status text not null default 'Submitted' check (status in ('Submitted', 'Pending Manual Review',
    'In Progress', 'Resolved', 'Rejected')),
  verification_status text not null default 'Manual review; AI screening not configured',
  assigned_to uuid references public.profiles(id),
  last_note text not null default 'Report submitted by citizen',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default clock_timestamp(),
  unique(citizen_id, submission_id),
  check (status <> 'Resolved' or resolution_photo_path is not null)
);
create index if not exists complaints_citizen_created on public.complaints(citizen_id, created_at desc);
create index if not exists complaints_status_created on public.complaints(status, created_at desc);

create table if not exists public.complaint_events (
  id bigint generated always as identity primary key,
  complaint_id bigint not null references public.complaints(id) on delete cascade,
  actor_id uuid references public.profiles(id),
  status text not null,
  assigned_to uuid references public.profiles(id),
  note text not null,
  created_at timestamptz not null default clock_timestamp()
);
create index if not exists events_complaint_created on public.complaint_events(complaint_id, created_at);

create or replace function public.audit_complaint()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.complaint_events(complaint_id, actor_id, status, assigned_to, note)
  values (new.id, auth.uid(), new.status, new.assigned_to, new.last_note);
  return new;
end;
$$;
drop trigger if exists cleantrack_audit on public.complaints;
create trigger cleantrack_audit after insert or update on public.complaints
for each row execute function public.audit_complaint();

alter table public.profiles enable row level security;
alter table public.complaints enable row level security;
alter table public.complaint_events enable row level security;
revoke all on public.profiles, public.complaints, public.complaint_events from anon, authenticated;
grant select on public.profiles, public.complaints, public.complaint_events to authenticated;
drop policy if exists profile_read on public.profiles;
create policy profile_read on public.profiles for select to authenticated
using (id = auth.uid() or public.is_officer());
drop policy if exists complaint_read on public.complaints;
create policy complaint_read on public.complaints for select to authenticated
using (citizen_id = auth.uid() or public.is_officer());
drop policy if exists event_read on public.complaint_events;
create policy event_read on public.complaint_events for select to authenticated
using (exists(select 1 from public.complaints c where c.id = complaint_id
  and (c.citizen_id = auth.uid() or public.is_officer())));

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values ('complaint-images', 'complaint-images', false, 5242880,
        array['image/jpeg','image/png','image/webp'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;
drop policy if exists cleantrack_image_insert on storage.objects;
create policy cleantrack_image_insert on storage.objects for insert to authenticated
with check (bucket_id = 'complaint-images' and (storage.foldername(name))[1] = auth.uid()::text
  and ((storage.foldername(name))[2] = 'reports'
       or ((storage.foldername(name))[2] = 'resolutions' and public.is_officer())));
drop policy if exists cleantrack_image_read on storage.objects;
create policy cleantrack_image_read on storage.objects for select to authenticated
using (bucket_id = 'complaint-images' and ((storage.foldername(name))[1] = auth.uid()::text
  or exists(select 1 from public.complaints c where (c.photo_path = name or c.resolution_photo_path = name)
    and (c.citizen_id = auth.uid() or public.is_officer()))));
-- This helper deliberately ignores table RLS: even a demoted officer cannot
-- delete their image if it is evidence on another citizen's complaint.
create or replace function public.own_image_is_unreferenced(p_path text)
returns boolean language sql stable security definer set search_path = '' as $$
  select auth.uid() is not null and p_path like auth.uid()::text || '/%'
    and not exists(select 1 from public.complaints c
                   where c.photo_path = p_path or c.resolution_photo_path = p_path);
$$;
revoke all on function public.own_image_is_unreferenced(text) from public, anon, authenticated;
grant execute on function public.own_image_is_unreferenced(text) to authenticated;
drop policy if exists cleantrack_unreferenced_image_delete on storage.objects;
create policy cleantrack_unreferenced_image_delete on storage.objects for delete to authenticated
using (bucket_id = 'complaint-images' and (storage.foldername(name))[1] = auth.uid()::text
  and public.own_image_is_unreferenced(name));

create or replace function public.submit_complaint(
  p_submission_id uuid, p_category text, p_photo_path text, p_latitude double precision,
  p_longitude double precision, p_location_address text, p_description text default null
) returns setof public.complaints language plpgsql security definer set search_path = '' as $$
declare v_user uuid := auth.uid(); v_name text; v_result public.complaints;
begin
  if v_user is null then raise exception 'Sign in before submitting a report'; end if;
  select * into v_result from public.complaints where citizen_id = v_user and submission_id = p_submission_id;
  if found then return next v_result; return; end if;
  select full_name into v_name from public.profiles where id = v_user;
  if v_name is null then raise exception 'Account profile is missing'; end if;
  if p_photo_path not like v_user::text || '/reports/%'
     or not exists(select 1 from storage.objects where bucket_id = 'complaint-images' and name = p_photo_path)
  then raise exception 'Upload your report photo first'; end if;
  insert into public.complaints(submission_id, citizen_id, citizen_name, category, photo_path,
    latitude, longitude, location_address, description)
  values (p_submission_id, v_user, v_name, p_category, p_photo_path, p_latitude, p_longitude,
          trim(p_location_address), nullif(trim(p_description), ''))
  on conflict (citizen_id, submission_id) do nothing returning * into v_result;
  if not found then
    select * into v_result from public.complaints where citizen_id = v_user and submission_id = p_submission_id;
  end if;
  return next v_result;
end;
$$;

create or replace function public.officer_update_complaint(
  p_id bigint, p_expected_updated_at timestamptz, p_status text, p_note text,
  p_assigned_to uuid default null, p_resolution_photo_path text default null
) returns setof public.complaints language plpgsql security definer set search_path = '' as $$
declare v_current public.complaints; v_after text;
begin
  if not public.is_officer() then raise exception 'Officer access required'; end if;
  select * into v_current from public.complaints where id = p_id for update;
  if not found then raise exception 'Complaint not found'; end if;
  if p_expected_updated_at is null or v_current.updated_at <> p_expected_updated_at then
    raise exception 'This complaint changed. Refresh and review it before saving.';
  end if;
  if p_note is null or char_length(trim(p_note)) not between 1 and 2000 then
    raise exception 'Add an officer note (1 to 2000 characters)';
  end if;
  if p_status is null or not (p_status = v_current.status
    or (v_current.status = 'Submitted' and p_status in ('Pending Manual Review','In Progress','Rejected'))
    or (v_current.status = 'Pending Manual Review' and p_status in ('In Progress','Rejected'))
    or (v_current.status = 'In Progress' and p_status in ('Resolved','Pending Manual Review'))
    or (v_current.status in ('Resolved','Rejected') and p_status = 'Pending Manual Review')) then
    raise exception 'Invalid status transition';
  end if;
  if p_assigned_to is not null and not exists(select 1 from public.profiles where id = p_assigned_to and role = 'officer')
  then raise exception 'Assign an authorized officer'; end if;
  v_after := coalesce(p_resolution_photo_path, v_current.resolution_photo_path);
  if p_resolution_photo_path is not null and (p_resolution_photo_path not like auth.uid()::text || '/resolutions/%'
    or not exists(select 1 from storage.objects where bucket_id = 'complaint-images' and name = p_resolution_photo_path))
  then raise exception 'Upload your cleanup photo first'; end if;
  if p_status = 'Resolved' and v_after is null then raise exception 'A cleanup photo is required to resolve the report'; end if;
  update public.complaints set status = p_status, last_note = trim(p_note), assigned_to = p_assigned_to,
    resolution_photo_path = v_after, updated_at = clock_timestamp()
  where id = p_id returning * into v_current;
  return next v_current;
end;
$$;

revoke all on function public.create_citizen_profile(), public.audit_complaint(), public.is_officer() from public, anon, authenticated;
grant execute on function public.is_officer() to authenticated;
revoke all on function public.submit_complaint(uuid,text,text,double precision,double precision,text,text) from public, anon, authenticated;
grant execute on function public.submit_complaint(uuid,text,text,double precision,double precision,text,text) to authenticated;
revoke all on function public.officer_update_complaint(bigint,timestamptz,text,text,uuid,text) from public, anon, authenticated;
grant execute on function public.officer_update_complaint(bigint,timestamptz,text,text,uuid,text) to authenticated;
commit;
