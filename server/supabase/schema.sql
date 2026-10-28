create table public.toll_highways (id text primary key, name text not null);
create table public.toll_plaza_codes (highway_id text references public.toll_highways(id) on delete cascade, code text not null, name text not null, primary key (highway_id, code));
create table public.toll_rates (highway_id text references public.toll_highways(id) on delete cascade, enter_code text not null, exit_code text not null, car_rm numeric(7,2) not null, primary key (highway_id, enter_code, exit_code));
create table public.toll_flat_rates (highway_id text references public.toll_highways(id) on delete cascade, code text not null, car_rm numeric(7,2) not null, primary key (highway_id, code));
create table public.toll_plaza_points (id bigint generated always as identity primary key, lat double precision not null, lon double precision not null, name text not null, ref text);
create table public.project_notes (topic text primary key, body text not null, updated_at timestamptz not null default now());
create table public.data_sources (name text primary key, source text not null, fetched date, note text);

alter table public.toll_highways enable row level security;
alter table public.toll_plaza_codes enable row level security;
alter table public.toll_rates enable row level security;
alter table public.toll_flat_rates enable row level security;
alter table public.toll_plaza_points enable row level security;
alter table public.project_notes enable row level security;
alter table public.data_sources enable row level security;

create policy "public read" on public.toll_highways for select to anon, authenticated using (true);
create policy "public read" on public.toll_plaza_codes for select to anon, authenticated using (true);
create policy "public read" on public.toll_rates for select to anon, authenticated using (true);
create policy "public read" on public.toll_flat_rates for select to anon, authenticated using (true);
create policy "public read" on public.toll_plaza_points for select to anon, authenticated using (true);
create policy "public read" on public.data_sources for select to anon, authenticated using (true);
-- project_notes: no policies = private.

create or replace function public.toll_fares_doc() returns jsonb
language sql stable security invoker set search_path = '' as $$
  select jsonb_build_object(
    'source', s.source,
    'fetched', s.fetched,
    'vehicleClass', 'Kelas 1 (car)',
    'highways', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', h.id,
        'name', h.name,
        'plazas', coalesce((select jsonb_agg(jsonb_build_array(c.code, c.name) order by c.code) from public.toll_plaza_codes c where c.highway_id = h.id), '[]'::jsonb),
        'open', coalesce((select jsonb_object_agg(f.code, jsonb_build_array(round(f.car_rm * 100)::int)) from public.toll_flat_rates f where f.highway_id = h.id), '{}'::jsonb),
        'pairs', coalesce((select jsonb_object_agg(r.enter_code || '>' || r.exit_code, jsonb_build_array(round(r.car_rm * 100)::int)) from public.toll_rates r where r.highway_id = h.id), '{}'::jsonb)
      ) order by h.id) from public.toll_highways h), '[]'::jsonb)
  )
  from public.data_sources s where s.name = 'toll_rates';
$$;
grant execute on function public.toll_fares_doc() to anon, authenticated;
