create table if not exists public.photos (
    id uuid primary key default gen_random_uuid(),
    category text not null check (length(btrim(category)) > 0),
    filename text not null check (length(btrim(filename)) > 0),
    content_type text not null default 'image/webp'
        check (content_type = 'image/webp'),
    storage_path text not null unique
        check (storage_path ~ '^[0-9a-fA-F-]{36}\.webp$'),
    byte_size integer not null check (byte_size between 1 and 200000),
    created_at timestamptz not null default now()
);

create index if not exists photos_category_created_id_idx
    on public.photos (category, created_at desc, id desc);

alter table public.photos enable row level security;
revoke all on table public.photos from anon, authenticated;
grant all on table public.photos to service_role;

comment on table public.photos is
    'Metadata for gallery images stored in the private gallery-images Storage bucket.';
comment on column public.photos.storage_path is
    'Path of the WebP binary in the private Supabase Storage bucket.';

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('gallery-images', 'gallery-images', false, 200000, array['image/webp'])
on conflict (id) do update
set name = excluded.name,
    public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;
