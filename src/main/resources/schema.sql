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
    active_version_id  uuid,
    department         varchar(120),
    document_type      varchar(80),
    tags               varchar(2048) not null default '',
    min_role_required  varchar(20) not null default 'READ',
    status             varchar(20) not null default 'PENDING',
    created_at         timestamptz not null default now(),
    updated_at         timestamptz
);

-- Idempotent upgrade path for databases created before folders existed.
alter table document_meta add column if not exists folder_id uuid references folder(id);
alter table document_meta add column if not exists active_version_id uuid;
alter table document_meta add column if not exists department varchar(120);
alter table document_meta add column if not exists document_type varchar(80);
alter table document_meta add column if not exists tags varchar(2048) not null default '';

create index if not exists idx_document_meta_folder_id on document_meta(folder_id);

create table if not exists document_version (
    id              uuid primary key default gen_random_uuid(),
    document_id     uuid not null references document_meta(id),
    version_number  integer not null,
    content_hash    varchar(64) not null,
    storage_key     varchar(1024) not null,
    content_type    varchar(255) not null default 'application/octet-stream',
    status          varchar(20) not null default 'UPLOADED',
    created_at      timestamptz not null default now(),
    created_by      uuid not null references app_user(id),
    constraint uq_document_version_number unique (document_id, version_number),
    constraint uq_document_version_hash unique (document_id, content_hash)
);
alter table document_version add column if not exists content_type varchar(255) not null default 'application/octet-stream';
create index if not exists idx_document_version_document_id on document_version(document_id);

create table if not exists ingestion_job (
    id                   uuid primary key default gen_random_uuid(),
    document_id          uuid not null references document_meta(id),
    document_version_id  uuid not null references document_version(id),
    status               varchar(20) not null default 'UPLOADED',
    error_message        varchar(1000),
    correlation_id       varchar(128),
    created_at           timestamptz not null default now(),
    started_at           timestamptz,
    completed_at         timestamptz
);
create index if not exists idx_ingestion_job_document_created
    on ingestion_job(document_id, created_at desc);

create table if not exists document_chunk (
    id                 uuid primary key default gen_random_uuid(),
    document_id        uuid not null references document_meta(id),
    version_id         uuid references document_version(id),
    chunk_index        int not null,
    content            text not null,
    min_role_required  varchar(20) not null default 'READ',
    created_by         uuid not null references app_user(id),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz,
    deleted            boolean not null default false
);
alter table document_chunk add column if not exists version_id uuid references document_version(id);
create index if not exists idx_document_chunk_document_id on document_chunk(document_id);
create index if not exists idx_document_chunk_version_id on document_chunk(version_id);
create index if not exists idx_document_chunk_content_fts
    on document_chunk using gin (to_tsvector('english', content));

-- Qwen3-Embedding-0.6B returns 1024-dimensional vectors.
create table if not exists chunk_embedding (
    chunk_id   uuid primary key references document_chunk(id) on delete cascade,
    embedding  vector(1024) not null
);
create index if not exists idx_chunk_embedding_ivfflat
    on chunk_embedding using ivfflat (embedding vector_cosine_ops) with (lists = 100);

create table if not exists conversation (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references app_user(id) on delete cascade,
    title       varchar(200) not null default 'New conversation',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create index if not exists idx_conversation_user_updated
    on conversation(user_id, updated_at desc);

create table if not exists conversation_message (
    id               uuid primary key default gen_random_uuid(),
    conversation_id  uuid not null references conversation(id) on delete cascade,
    role             varchar(20) not null,
    content          text not null,
    sources_json     text not null default '[]',
    created_at       timestamptz not null default now()
);
create index if not exists idx_conversation_message_conversation_created
    on conversation_message(conversation_id, created_at);

-- The single hardcoded admin row is seeded in code (see security.AdminSeeder), reading
-- app.security.admin-email, rather than a literal here -- so there's exactly one place
-- that needs to agree on which email is "the" admin.
