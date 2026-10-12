<div align="center">

<img src="Frontend/public/favicon.svg" alt="Edify logo" width="84" height="84" />

# EDIFY

### Enterprise AI Knowledge Platform

**Upload your company documents. Ask questions. Get grounded answers with citations — only from what you're allowed to see.**

<br />

[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)

[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://github.com/pgvector/pgvector)
[![Redis](https://img.shields.io/badge/Redis-7.4-DC382D?style=flat-square&logo=redis&logoColor=white)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Apache_Kafka-3.9-231F20?style=flat-square&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Vert.x](https://img.shields.io/badge/Eclipse_Vert.x-4.5-782A90?style=flat-square&logo=eclipsevertdotx&logoColor=white)](https://vertx.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![Ollama](https://img.shields.io/badge/Ollama-local_AI-000000?style=flat-square&logo=ollama&logoColor=white)](https://ollama.com/)
[![CI](https://img.shields.io/github/actions/workflow/status/shivammiyyy/Edify-Backend/ci.yml?branch=main&style=flat-square&label=CI&logo=githubactions&logoColor=white)](https://github.com/shivammiyyy/Edify-Backend/actions/workflows/ci.yml)

<br />

[Problem](#-problem-statement) ·
[Solution](#-solution) ·
[Architecture](#-architecture) ·
[Quick Start](#-quick-start) ·
[Features](#-features) ·
[API](#api-documentation) ·
[Deploy](#cloud-deployment)

<br />

<img src="docs/images/workspace-search.png" alt="Edify AI assistant answering a question with source citations" width="92%" />

</div>

<br />

> [!NOTE]
> The Compose MinIO service uses the pinned `bitnamilegacy/minio:2025.7.23-debian-12-r5` image because the previously configured `minio/minio` Docker Hub tag is no longer publicly available. It is for **local development only** — use managed S3-compatible storage for deployments.

---

## 🎯 Problem Statement

Organizations accumulate policies, runbooks, product specs, and other critical knowledge across countless files and folders. Getting a reliable answer today means:

- 🔍 **Hunting** for the right document among many.
- 🔐 **Wondering** whether you're even allowed to see it — and whether the people searching are.
- 🧩 **Stitching together** relevant passages by hand.
- 🤖 **Distrusting** generic chatbots that hallucinate answers and never tell you where they came from.

## 💡 Solution

**Edify** is a secure, role-aware knowledge workspace powered by retrieval-augmented generation (RAG).

| | What Edify does |
|---|---|
| 📁 **Organize** | Drive-style nested folders, versioned PDF / TXT / Markdown / DOCX documents |
| 🛡️ **Protect** | Four-level RBAC plus folder-level gating, enforced in the API **and** inside SQL retrieval queries |
| ⚡ **Ingest** | Asynchronous Kafka → Vert.x pipeline: extract, chunk, embed, index — with live progress |
| 🔎 **Retrieve** | Hybrid search (pgvector semantic + PostgreSQL full-text) with weighted reranking |
| 💬 **Answer** | Streaming (SSE) answers grounded in your documents, always with source citations |
| 🔌 **Flex** | Local Ollama or any OpenAI-compatible cloud provider — swap with one env var |

## 🧩 Architecture

```mermaid
flowchart LR
    User(["👤 User"]) --> UI["⚛️ React + TypeScript UI"]
    UI -->|"HTTPS · session cookie · CSRF"| API["🍃 Spring Boot API + SSE"]

    API --> Auth["🔐 Google OAuth2/OIDC<br/>+ role checks"]
    API --> Docs["📄 Document & folder services"]
    API --> RAG["🧠 RAG & conversation services"]
    API --> Redis[("⚡ Redis<br/>embedding cache · rate limits")]

    Docs --> Store[("🗄️ MinIO / S3<br/>original files")]
    Docs -->|"uploaded event"| Kafka{{"📨 Kafka"}}
    Kafka --> Worker["⚙️ Vert.x worker<br/>(same JVM)"]
    Worker --> Parse["Extract → Chunk → Embed"]
    Parse --> DB[("🐘 PostgreSQL + pgvector")]
    Parse --> AI["🤖 Ollama / OpenAI-compatible"]

    RAG --> Search["Authorized hybrid retrieval<br/>+ weighted reranking"]
    Search --> DB
    RAG --> AI
    RAG -->|"tokens · sources · status"| UI
```

<details>
<summary><b>How the pieces fit together</b></summary>

<br />

- **Spring Boot** owns HTTP, sessions, CSRF, authorization, and SSE streaming.
- **Vert.x** runs inside the same JVM and processes ingestion off the request thread.
- **Kafka** carries ingestion events in local Compose. Hosted deployments can set `KAFKA_ENABLED=false` and dispatch straight to the in-process worker (asynchronous, but no durable replay after a restart).
- **PostgreSQL + pgvector** store application data, chunks, and 1024-dimensional embeddings.
- **Redis** caches embeddings (by content SHA-256 + model) and enforces per-user rate limits — it is never a source of truth.
- **MinIO / S3** holds the original document versions.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the full write-up.

</details>

## 🚀 Quick Start

### Prerequisites

| Requirement | Needed for | Notes |
|---|---|---|
| 🐳 **Docker Desktop** (Compose v2) | Both options | Runs Postgres, Redis, Kafka, MinIO (and Ollama in Option A) |
| ☕ **Java 17** + **Maven** | Option B | Native backend |
| 🟢 **Node.js** + **npm** | Option B | Native frontend |
| 🔑 **Google OAuth 2.0 Web client** | Both options | See [Google OAuth setup](#google-oauth-setup) |
| 🦙 **Ollama models** | Both options | `qwen3:8b` (chat) and `qwen3-embedding:0.6b` (1024-dim embeddings) |

> [!TIP]
> Set the Google OAuth redirect URI to `http://localhost:8080/login/oauth2/code/google` and the authorized origin to `http://localhost:8080` (plus `http://localhost:5173` for Option B).

### Option A — Everything in Docker

Runs the UI, backend, PostgreSQL/pgvector, Redis, Kafka, MinIO, and Ollama.

```powershell
# 1. Create your environment file, then set GOOGLE_CLIENT_ID,
#    GOOGLE_CLIENT_SECRET and APP_ADMIN_EMAIL inside it
Copy-Item .env.example .env

# 2. Build and start the stack
docker compose -f docker-compose.prod.yml up --build -d

# 3. Pull the AI models once (stored in a persistent volume)
docker compose -f docker-compose.prod.yml exec ollama ollama pull qwen3:8b
docker compose -f docker-compose.prod.yml exec ollama ollama pull qwen3-embedding:0.6b
```

Open **http://localhost:8080** and sign in with Google. 🎉

<details>
<summary><b>Already have Ollama on your host? Use the default Compose file</b></summary>

<br />

`docker-compose.yml` builds the UI into the Spring Boot image and expects Ollama on the host at port `11434`.

```powershell
ollama pull qwen3:8b
ollama pull qwen3-embedding:0.6b
docker compose up --build -d
```

</details>

<details>
<summary><b>Useful Compose commands</b></summary>

<br />

```powershell
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml down        # add -v to also delete all data
```

</details>

### Option B — Native hot-reload development

Docker supplies the infrastructure; the backend and frontend run natively for fast feedback. Install and run Ollama on the host first and pull both models.

```powershell
# Infrastructure: Postgres + pgvector, Redis, MinIO, Kafka, Adminer
docker compose -f docker-compose.dev.yml up -d

# Terminal 1 — backend on :8080 (Spring Boot does not auto-load .env)
$env:GOOGLE_CLIENT_ID     = "your-google-client-id"
$env:GOOGLE_CLIENT_SECRET = "your-google-client-secret"
$env:APP_ADMIN_EMAIL      = "you@example.com"
$env:OAUTH_SUCCESS_URL    = "http://localhost:5173/"
mvn spring-boot:run

# Terminal 2 — frontend on :5173 with hot reload
cd Frontend
npm ci
npm run dev
```

| Service | URL |
|---|---|
| Frontend (Vite) | http://localhost:5173 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| MinIO console | http://localhost:9001 |
| Adminer (DB browser) | http://localhost:8081 |
| Kafka (host) | `localhost:29092` |

Stop the infrastructure with `docker compose -f docker-compose.dev.yml down` (add `-v` to delete local data).

---

## 📑 Table of contents

- [Problem Statement](#-problem-statement)
- [Solution](#-solution)
- [Architecture](#-architecture)
- [Quick Start](#-quick-start)
- [Features](#-features)
- [Screenshots](#screenshots)
- [Technology stack](#technology-stack)
- [RAG pipeline](#rag-pipeline)
- [Event-driven ingestion](#event-driven-ingestion)
- [Security](#security)
- [Roles and permissions](#roles-and-permissions)
- [Project layout](#project-layout)
- [Google OAuth setup](#google-oauth-setup)
- [Configuration reference](#configuration-reference)
- [API documentation](#api-documentation)
- [Testing](#testing)
- [Cloud deployment](#cloud-deployment)
- [Health and metrics](#health-and-metrics)
- [CI/CD](#cicd)
- [Known limitations and future improvements](#known-limitations-and-future-improvements)

## ✨ Features

- **Stateful session auth** with Google OAuth2/OIDC login (`JSESSIONID` cookie, CSRF
  enabled) — no JWTs, no stateless API tokens.
- **Four roles**, enforced both at the HTTP route layer and in SQL: `READ < WRITE <
  CUSTODIAN < ADMIN`. A configured bootstrap admin email is provisioned as `ADMIN` on
  first login; everyone else starts as `READ` until promoted.
- **Drive-style document organization** — nested folders, breadcrumbs, per-folder
  minimum-role gating, move documents/folders between locations.
- **Versioned document storage** — PDF, TXT, Markdown, and DOCX originals live in MinIO;
  PostgreSQL stores version metadata, SHA-256 hashes, active-version pointers, and indexed
  chunks. Duplicate uploads in the same user's folder are skipped.
- **Consistent resource authorization** — `AuthorizationService` applies document and
  ancestor-folder permissions to browsing, retrieval, and document/folder writes; vector
  search excludes inaccessible folders before chunks can enter RAG context.
- **Traceable API failures** — consistent JSON error responses and validated
  `X-Correlation-ID` values propagated into Vert.x ingestion work and log MDC.
- **Vert.x-powered ingestion pipeline** — chunking, embedding (Ollama `qwen3-embedding`),
  and pgvector persistence run off the main HTTP thread via Kafka and a Vert.x worker;
  document processing jobs persist stage, completion, and failure state.
- **Role-filtered RAG search** — queries are classified for in-scope/out-of-scope intent,
  matched against role- and folder-aware pgvector and PostgreSQL full-text results, then
  reranked with 0.7 vector / 0.3 keyword score weights. Optional department, document
  type, tags, and owner filters apply before retrieval. Answers include source citations,
  and retrieved excerpts are treated as untrusted evidence by the LLM.
- **Swappable AI providers** — `EmbeddingProvider` and `LlmProvider` select Ollama locally
  or OpenAI-compatible cloud endpoints through `AI_PROVIDER`; the configured embedding
  model must return 1024 dimensions to match the pgvector column.
- **Persisted AI conversations** — per-user conversation/message history, short recent
  context for follow-up questions, progressively rendered Markdown answers and source
  cards, retry for interrupted responses, and token/source/status/completion/error SSE events.
- **Selective Redis use** — embeddings are cached by content SHA-256 and model ID, while
  atomic per-user minute limits protect document uploads and conversation messages.
- **Application observability** — Spring Boot Actuator provides safe health/probe endpoints
  and admin-only metrics; Micrometer records RAG, retrieval, embedding, LLM, ingestion, and
  Kafka publish failure measurements.
- **Integration coverage** — Testcontainers exercises pgvector, Redis, and Kafka together;
  focused unit tests cover authorization, ingestion failures, conversation retry rules,
  reranking, and RAG citation/prompt behavior.
- **Responsive workspace UI** — dashboard overview, searchable/sortable knowledge explorer,
  drag-and-drop supported-file uploads with ingestion-stage progress for uploads and text
  changes, version history, collapsible navigation, and light/dark/system appearance settings.
- **Local Compose infrastructure** for PostgreSQL/pgvector, Redis, MinIO, and single-node
  Kafka, plus a GitHub Actions pipeline that builds/tests/packages on every push.

## Screenshots

Representative UI captures currently checked into the repository:

| AI assistant with citations | Knowledge explorer |
|---|---|
| ![RAG workspace](docs/images/workspace-search.png) | ![Documents page](docs/images/documents-drive.png) |

| Role and access management |
|---|
| ![Access management](docs/images/access-management.png) |

## Technology stack

| Area | Technologies |
|---|---|
| Backend | Java 17, Spring Boot, Spring Security, OAuth2/OIDC, Spring MVC, Vert.x |
| Data and infrastructure | PostgreSQL, pgvector, Redis, Kafka, MinIO/S3 |
| AI | Ollama or OpenAI-compatible cloud providers, hybrid RAG, SSE |
| Frontend | React, TypeScript, Vite, Tailwind CSS, Lucide |
| Delivery | Docker Compose, GitHub Actions, GHCR, Vercel/Render deployment configuration |
| Quality | JUnit, Spring Boot Test, Testcontainers, Actuator, Micrometer |

## RAG pipeline

```mermaid
flowchart LR
    Q["User question"] --> I["Intent and scope classification"]
    I --> E["Query embedding"]
    E --> H["Hybrid search: pgvector + PostgreSQL full-text, with role/folder/metadata SQL filters"]
    H --> R["Weighted reranking (0.7 vector / 0.3 keyword)"]
    R --> C["Untrusted context + citation labels"]
    C --> L["LLM provider"]
    L --> S["Answer + source citations"]
    S -->|SSE tokens, sources, status| UI["Assistant UI"]
```

Retrieval applies role/folder authorization and optional department, document-type, tag,
and owner filters inside the SQL searches, before retrieved text can reach the LLM. Source
locations are not currently extracted, so page and section citations may be empty.

## Event-driven ingestion

For local development, document metadata, version records, and the original object are
saved before an `edify.document.uploaded` Kafka event is published. A Kafka listener
dispatches work to a Vert.x verticle; `executeBlocking` runs parsing, chunking, embedding,
and indexing away from the Vert.x event loop. Ingestion stage, completion, and failure
are persisted as job state. In the hosted profile, Kafka may be disabled and dispatch
goes directly to the same worker; this fallback does not provide durable replay.

## Security

Google OAuth2/OIDC establishes a stateful HTTP session. Spring Security retains CSRF
protection, and role checks are combined with document and ancestor-folder authorization.
The same access constraints apply during browsing, download, and RAG retrieval. New users
start with `READ`; role elevation follows the documented custodian/admin rules.

This repository currently uses a single shared knowledge corpus and is **not tenant
isolated**. Do not operate it as a multi-tenant service without adding tenant-scoped
identity, storage, database constraints, and retrieval filters.

## Roles and permissions

| Role | Can read search results & browse documents | Can upload / update / delete documents & folders | Can grant `WRITE` | Can grant `CUSTODIAN`/`ADMIN` |
|---|:---:|:---:|:---:|:---:|
| `READ` | ✅ | ❌ | ❌ | ❌ |
| `WRITE` | ✅ | ✅ | ❌ | ❌ |
| `CUSTODIAN` | ✅ | ✅ | ✅ | ❌ |
| `ADMIN` | ✅ | ✅ | ✅ | ✅ |

- The configured system admin (`app.security.admin-email`) is seeded in the DB and
  re-asserted as `ADMIN` + `systemAdmin=true` on every login — it cannot be demoted
  through the API.
- Every other new Google sign-in starts at `READ`. An `ADMIN` or `CUSTODIAN` must
  promote them via `PUT /api/admin/users/{id}/role`.

## Project layout

```
src/main/java/com/example/ragingestor/
  config/       Security, CORS, OpenAPI, Vert.x, and SSE configuration
  domain/       JPA entities and PostgreSQL/pgvector repositories
  ingestion/    Document lifecycle, object storage, Kafka and Vert.x processing
  observability/ Micrometer timers and counters
  rag/          Retrieval, reranking, provider abstractions, conversations, SSE
  security/     OAuth2/OIDC principals, authorization, roles, API error handling

Frontend/       React + TypeScript + Vite workspace
docker-compose.yml        Bundled UI/API app with Postgres, Redis, Kafka, and MinIO;
                          Ollama runs on the host
docker-compose.dev.yml    Native hot-reload development infrastructure
docker-compose.prod.yml   Containerized frontend/backend stack with Ollama
Dockerfile                Bundled frontend/backend image
Dockerfile.backend        Backend-only image
Frontend/Dockerfile       Nginx-served SPA image
.github/workflows/        CI, dependency scan, and GHCR publishing
```

## Google OAuth setup

1. In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), create
   an OAuth 2.0 Client ID of type **Web application**.
2. Authorized JavaScript origins: `http://localhost:8080` (and `http://localhost:5173`
   if you also develop against Vite directly). Add the deployed Vercel origin for the
   public frontend.
3. Authorized redirect URI: `http://localhost:8080/login/oauth2/code/google` — this must
   match **exactly**; Spring Security always redirects back to the backend regardless of
   which frontend origin you're using.
4. If your OAuth consent screen is in "Testing" mode, add your Google account as a test
   user or it will be rejected at the consent screen.
5. Put the generated Client ID/Secret into `.env` as `GOOGLE_CLIENT_ID` /
   `GOOGLE_CLIENT_SECRET`. Never commit them — if a secret was ever pasted into a
   tracked file or shared in chat, rotate it in Cloud Console immediately.
6. For production, also authorize
   `https://<render-service>.onrender.com/login/oauth2/code/google` as a redirect URI.

## Configuration reference

All of these have `localhost`-friendly defaults in `application.yml`; override via `.env`
/ compose environment for anything container-specific.

| Variable | Purpose | Default |
|---|---|---|
| `DATABASE_URL` / `_USERNAME` / `_PASSWORD` | Postgres connection | `jdbc:postgresql://localhost:5432/rag_ingestor` |
| `GOOGLE_CLIENT_ID` / `_SECRET` | OAuth2 client | — |
| `APP_ADMIN_EMAIL` | The one hardcoded admin | `admin@example.com` |
| `FRONTEND_ORIGIN` | CORS-allowed SPA origin | `http://localhost:5173` |
| `OAUTH_SUCCESS_URL` | Redirect target after login | `/` |
| `AI_PROVIDER` | AI implementation: `ollama` (default) or `cloud` | `ollama` |
| `LLM_URL` / `LLM_MODEL` / `LLM_API_KEY` | Chat endpoint, model, optional bearer token. Cloud mode expects an OpenAI-compatible chat-completions endpoint. | Ollama endpoint, `qwen3:8b`, empty |
| `EMBEDDINGS_URL` / `_MODEL` / `_API_KEY` / `_DIMENSION` | Embedding endpoint, model, optional bearer token, and vector size. Cloud mode expects an OpenAI-compatible embeddings endpoint; output must match the database's 1024 dimensions. | Ollama endpoint, `qwen3-embedding:0.6b`, empty, `1024` |
| `DOCKER_LLM_URL` / `DOCKER_EMBEDDINGS_URL` | Optional container-network AI endpoint overrides (use these for hosted providers in the full Compose stack) | Compose Ollama service URLs |
| `LLM_TIMEOUT` / `EMBEDDINGS_TIMEOUT` | Provider request timeouts | `PT90S` / `PT60S` |
| `OBJECT_STORAGE_ENDPOINT` / `_ACCESS_KEY` / `_SECRET_KEY` / `_BUCKET` | MinIO/S3-compatible object storage | `http://localhost:9000`, local development credentials, `edify-documents` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address | `localhost:29092` |
| `REDIS_URL` | Redis connection URI; `rediss://` is supported for managed TLS Redis such as Upstash | `redis://localhost:6379` |
| `EMBEDDING_CACHE_TTL` | Redis embedding-cache lifetime | `PT168H` |
| `DOCUMENTS_PER_MINUTE` / `MESSAGES_PER_MINUTE` | Per-user limits for document changes and AI messages | `10` / `20` |

## API documentation

Start the application and sign in through Google OAuth. Open
`/swagger-ui/index.html` on the backend origin for the interactive API reference;
`/v3/api-docs` returns the OpenAPI JSON. The documentation covers authentication,
documents, folders, RAG search, conversations, ingestion jobs, and role management.
Swagger uses the existing browser session cookie. For state-changing calls, retrieve the
CSRF token from `GET /api/csrf`, then use the **Authorize** dialog to set `X-CSRF-TOKEN`.
Endpoints remain protected by the same role and resource authorization as the UI.

### API examples (curl)

After signing in through the browser, copy the `JSESSIONID` cookie value from browser
dev tools, then:

```powershell
$env:JSESSIONID = "paste-your-session-cookie"
$csrf = (curl.exe -s -H "Cookie: JSESSIONID=$env:JSESSIONID" http://localhost:8080/api/csrf | ConvertFrom-Json).token

# Create a folder
curl.exe -X POST http://localhost:8080/api/folders `
  -H "Cookie: JSESSIONID=$env:JSESSIONID" -H "X-CSRF-TOKEN: $csrf" -H "Content-Type: application/json" `
  --data-raw '{"name":"Engineering","parentId":null,"minRoleRequired":"READ"}'

# Queue a document for ingestion (swap folderId with the id returned above)
$queued = curl.exe -s -X POST http://localhost:8080/api/documents `
  -H "Cookie: JSESSIONID=$env:JSESSIONID" -H "X-CSRF-TOKEN: $csrf" -H "Content-Type: application/json" `
  --data-raw '{"filename":"handbook.txt","content":"The handbook content goes here.","minRoleRequired":"READ","folderId":"<folder-id>"}'

# Check the persisted asynchronous job state
$jobId = ($queued | ConvertFrom-Json).jobId
curl.exe -s -H "Cookie: JSESSIONID=$env:JSESSIONID" "http://localhost:8080/api/ingestion-jobs/$jobId"

# Browse a folder's contents (folders + documents + breadcrumbs)
curl.exe -s -H "Cookie: JSESSIONID=$env:JSESSIONID" "http://localhost:8080/api/folders/contents?folderId=<folder-id>"

# Create a conversation for streaming messages
$conversation = curl.exe -s -X POST http://localhost:8080/api/conversations `
  -H "Cookie: JSESSIONID=$env:JSESSIONID" -H "X-CSRF-TOKEN: $csrf"
$conversationId = ($conversation | ConvertFrom-Json).id

# Promote a user to WRITE (CUSTODIAN or ADMIN can do this)
curl.exe -X PUT http://localhost:8080/api/admin/users/<user-id>/role `
  -H "Cookie: JSESSIONID=$env:JSESSIONID" -H "X-CSRF-TOKEN: $csrf" -H "Content-Type: application/json" `
  --data-raw '{"role":"WRITE"}'
```

Stream a response with `POST /api/conversations/{id}/messages`:

```powershell
curl.exe -N -X POST "http://localhost:8080/api/conversations/$conversationId/messages" `
  -H "Cookie: JSESSIONID=$env:JSESSIONID" -H "X-CSRF-TOKEN: $csrf" -H "Content-Type: application/json" `
  --data-raw '{"question":"What does the handbook say?","department":"Engineering","tags":["on-call"]}'
```

Use `GET /api/conversations` to list the signed-in user's conversations and
`GET /api/conversations/{id}` to retrieve a conversation with its messages and citations.
SSE event names are `status`, `source`, `token`, `completion`, and `error`; the React UI
consumes the stream and renders answer text as tokens arrive. Both this endpoint and
`POST /api/query` return citations and apply optional department, document-type, tags,
and owner filters alongside document-role and ancestor-folder authorization.

New document submissions return `202 Accepted` with `documentId`, `jobId`, and
`PROCESSING`; fetch `GET /api/ingestion-jobs/{jobId}` for persisted progress/failure state.
For binary uploads, use `POST /api/documents/upload` as multipart form data (`file`,
optional `minRoleRequired`, optional `folderId`). Supported formats are PDF, TXT,
Markdown, and DOCX; uploads are limited to 10 MiB by default. The original file is stored
in MinIO while extracted text is chunked for retrieval. Use
`GET /api/documents/{id}/versions` for version history and
`GET /api/documents/{id}/download` to fetch the active original. Replace a stored file using `PUT /api/documents/{id}/upload` with a multipart `file`;
text updates through `PUT /api/documents/{id}` are also queued asynchronously.

Other document/folder routes: `PUT /api/documents/{id}` (replace text content, re-chunk),
`PUT /api/documents/{id}/move` (`{"folderId": "..."}`), `DELETE /api/documents/{id}`,
`PUT /api/folders/{id}` (rename/re-role), `PUT /api/folders/{id}/move`,
`DELETE /api/folders/{id}` (only when empty).

## Testing

Run the backend unit and Testcontainers integration tests:

```powershell
mvn verify
```

The infrastructure integration test uses disposable PostgreSQL/pgvector, Redis, and
Kafka containers; Docker must be available. Frontend validation:

```powershell
cd Frontend
npm ci
npm run lint
npm run build
```

## CI/CD

- **`.github/workflows/ci.yml`** runs on every push/PR to `main`: `mvn verify` for the
  backend (including Testcontainers), `npm run lint && npm run build` for the frontend,
  Trivy high/critical dependency scanning, and Docker builds for bundled, backend-only,
  and frontend images.
- **`.github/workflows/docker-publish.yml`** publishes `ghcr.io/<owner>/edify-backend`
  and `ghcr.io/<owner>/edify-frontend` on pushes to `main`, version tags, and manual
  dispatch. The workflow uses the built-in `GITHUB_TOKEN`; no registry secret is needed.

## Cloud deployment

The public frontend can be deployed from `Frontend/` to Vercel. Configure the build
environment variable `VITE_API_BASE_URL` to the HTTPS Render backend origin, without a
trailing slash (for example, `https://edify-backend.onrender.com`). `Frontend/vercel.json`
contains the Vite build configuration.

Deploy the backend with `render.yaml` or create a Render Docker web service using
`Dockerfile.backend`. The blueprint marks credentials and service URLs as dashboard
secrets (`sync: false`); set the following:

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | Neon JDBC URL beginning `jdbc:postgresql://` |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Neon credentials |
| `REDIS_URL` | Upstash TLS URI beginning `rediss://` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | OAuth web client credentials |
| `APP_ADMIN_EMAIL` | Verified Google account email for the system administrator |
| `FRONTEND_ORIGIN`, `OAUTH_SUCCESS_URL` | Exact Vercel origin, e.g. `https://edify.vercel.app` and `https://edify.vercel.app/` |
| `OBJECT_STORAGE_ENDPOINT`, `OBJECT_STORAGE_ACCESS_KEY`, `OBJECT_STORAGE_SECRET_KEY`, `OBJECT_STORAGE_BUCKET` | S3-compatible object storage credentials and bucket |
| `LLM_URL`, `LLM_MODEL`, `LLM_API_KEY` | Hosted OpenAI-compatible chat provider |
| `EMBEDDINGS_URL`, `EMBEDDINGS_MODEL`, `EMBEDDINGS_API_KEY` | Hosted embedding provider returning 1024-dimensional vectors |

Add the Render callback URL `https://<render-service>.onrender.com/login/oauth2/code/google`
to the Google OAuth client's authorized redirect URIs. Production sessions use secure,
HTTP-only, cross-site cookies; CORS is restricted to the configured frontend origin.
`KAFKA_ENABLED=false` (the default in `prod`) avoids requiring a hosted Kafka service:
uploads are handed directly to the in-process Vert.x worker. Local Compose continues to
use Kafka. This hosted fallback is asynchronous but does not provide Kafka durability or
automatic replay after a backend restart; use a durable queue for workloads requiring it.

The free Render web tier can sleep and has ephemeral local storage, so store documents in
external object storage and expect cold starts. Never use Render's filesystem for
permanent documents. Provision Neon, Upstash, object storage, the hosted LLM, and OAuth
secrets separately; no cloud resources or credentials are created by this repository.
The app does not yet implement tenant isolation.

## Health and metrics

`GET /actuator/health` and Spring's liveness/readiness health probes return status only.
The `info` and `metrics` endpoints require an authenticated `ADMIN` session; no metric
endpoint exposes prompt text, document contents, user IDs, or other request-level labels.
Run `mvn verify` to execute unit tests and the Testcontainers integration test for
PostgreSQL/pgvector, Redis, and Kafka. The integration test is skipped automatically if
Docker is unavailable.

## Known limitations and future improvements

- **Document parsing and chunking** — PDF/DOCX text is extracted without OCR or retained
  page/section offsets; chunking uses a fixed word-count strategy.
- **Drive parity** — no shared starred/recent views, bulk actions, or trash/restore UI.
- **Authorization model** — document and folder permissions use a minimum role threshold,
  not per-user or per-group ACL grants.
- **Tenant boundary** — the current product uses one shared knowledge corpus and does not
  implement tenant isolation; do not deploy it as a multi-tenant service without adding
  tenant-scoped identities, persistence constraints, and retrieval filters.
- **Conversation memory** — only a short recent-message window is sent with a query; there
  is no summarization or long-term memory.
- **Cloud deployment** — local Compose includes Kafka, MinIO, and Ollama support; a public
  deployment must use managed database/cache/object storage and a hosted AI provider.
- **Activity feed** — the dashboard shows recent document additions, not a complete
  audit trail for searches, downloads, deletes, and role changes.
- **Screenshots** — checked-in captures cover the assistant, knowledge explorer, and
  access management; refreshed dashboard, document details, upload, and settings captures
  still need to be taken from a configured demo workspace.
- **Observability and migrations** — core Micrometer metrics and Actuator health probes
  exist, but distributed tracing and versioned Flyway/Liquibase migrations remain follow-up
  work; schema setup currently uses idempotent `schema.sql`.
