create extension if not exists vector;

create table if not exists app_user (
    id              uuid primary key default gen_random_uuid(),
    email           varchar(320) not null unique,
    display_name    varchar(255),
    provider        varchar(50),
    role            varchar(20) not null default 'READ',
    system_admin    boolean not null default false,
    created_at      timestamptz not null default now(),
    last_login_at   timestamptz
);

create table if not exists folder (
    id                 uuid primary key default gen_random_uuid(),
    name               varchar(255) not null,
    parent_id          uuid references folder(id),
    created_by         uuid not null references app_user(id),
    min_role_required  varchar(20) not null default 'READ',
    created_at         timestamptz not null default now(),
    updated_at         timestamptz,
    deleted            boolean not null default false
);
create index if not exists idx_folder_parent_id on folder(parent_id);

create table if not exists document_meta (
    id                 uuid primary key default gen_random_uuid(),
    filename           varchar(500) not null,
    uploaded_by        uuid not null references app_user(id),
    folder_id          uuid references folder(id),
    min_role_required  varchar(20) not null default 'READ',
    status             varchar(20) not null default 'PENDING',
    created_at         timestamptz not null default now(),
    updated_at         timestamptz
);

-- Idempotent upgrade path for databases created before folders existed.
alter table document_meta add column if not exists folder_id uuid references folder(id);

create index if not exists idx_document_meta_folder_id on document_meta(folder_id);

create table if not exists document_chunk (
    id                 uuid primary key default gen_random_uuid(),
    document_id        uuid not null references document_meta(id),
    chunk_index        int not null,
    content            text not null,
    min_role_required  varchar(20) not null default 'READ',
    created_by         uuid not null references app_user(id),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz,
    deleted            boolean not null default false
);
create index if not exists idx_document_chunk_document_id on document_chunk(document_id);

-- Qwen3-Embedding-0.6B returns 1024-dimensional vectors.
create table if not exists chunk_embedding (
    chunk_id   uuid primary key references document_chunk(id) on delete cascade,
    embedding  vector(1024) not null
);
create index if not exists idx_chunk_embedding_ivfflat
    on chunk_embedding using ivfflat (embedding vector_cosine_ops) with (lists = 100);

-- The single hardcoded admin row is seeded in code (see security.AdminSeeder), reading
-- app.security.admin-email, rather than a literal here -- so there's exactly one place
-- that needs to agree on which email is "the" admin.
