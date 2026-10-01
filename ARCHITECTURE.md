# RAG Ingestor Architecture

## Overview

The application combines a React/TypeScript frontend with a single Spring Boot
service and an embedded Vert.x event bus. In the production Docker image, Spring
Boot serves the built frontend and is the only external HTTP entry point; it owns
authentication, authorization, and request validation. In frontend development,
Vite serves the UI on port 5173 and calls Spring Boot on port 8080 using
credentialed CORS. Vert.x handles the document-ingestion handoff inside the same
JVM; it is not a separate public server.

```text
Browser / API client
       |
       | HTTPS in production; JSESSIONID + CSRF token
       v
Spring Boot :8080
  |-- Spring Security: OAuth2/OIDC login, HTTP session, role checks
  |-- /api/documents --> Vert.x event bus --> ingestion worker
  |                                         |-- chunk text
  |                                         |-- call Ollama embeddings
  |                                         `-- save metadata, chunks, vectors
  |
  `-- /api/query --> intent classification (Ollama chat)
                     --> embedding (Ollama)
                     --> pgvector search with role filter
                     `--> grounded answer generation (Ollama chat)
                              |
                              +-- PostgreSQL + pgvector :5432
                              `-- Ollama on host :11434
```

## Runtime and deployment

Two Compose files cover the two ways you'd run this:

| File | Purpose | Services |
| --- | --- | --- |
| `docker-compose.dev.yml` | Local development with hot reload | `db` (Postgres/pgvector), `adminer` (DB browser on `:8081`) — backend/frontend run natively via `mvn spring-boot:run` / `npm run dev` |
| `docker-compose.prod.yml` | Production-like, fully containerized | `app` (built Spring Boot + bundled frontend), `db` (Postgres/pgvector) |

`docker-compose.prod.yml` runs:

| Service | Purpose | Persistence / connection |
| --- | --- | --- |
| `app` | Spring Boot API and embedded Vert.x | Port `8080`; connects to the Compose database and to host Ollama |
| `db` | PostgreSQL 16 with pgvector | Port `5432`; `postgres_data` volume |

The Docker build runs the frontend TypeScript check and Vite production build,
packages the resulting static assets into the Spring Boot JAR, and serves the
complete app from port `8080`.

Ollama runs on the host, not in Compose. The app container connects to it using
`host.docker.internal:11434`. The default chat model is `qwen3:8b`; the default
embedding model is `qwen3-embedding:0.6b`. The embedding database column is
`vector(1024)`, matching that model's output dimension.

The Docker entrypoint at `docker/initdb/00-pgvector.sql` enables pgvector when a
new database volume is first initialized. Spring then applies
`src/main/resources/schema.sql`; Hibernate validates the resulting schema.
Database data persists in the named volume across normal container restarts.

Configuration is supplied through environment variables (normally via a local,
untracked `.env` file). Important settings include:

| Variable | Purpose |
| --- | --- |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth web client credentials |
| `APP_ADMIN_EMAIL` | Verified Google email assigned the protected system ADMIN role |
| `DATABASE_PASSWORD` | Database password |
| `LLM_URL`, `LLM_MODEL` | Ollama chat API URL and model |
| `EMBEDDINGS_URL`, `EMBEDDINGS_MODEL`, `EMBEDDINGS_DIMENSION` | Embeddings API, model, and vector size |

The checked-in configuration contains development defaults. Replace placeholder
OAuth credentials before attempting a login, and do not commit real secrets.

## Authentication and authorization

Google OAuth2/OIDC authenticates a user. The application provisions or retrieves
the user's `app_user` row by email, and uses the database role—not provider roles—
for application permissions. Successful authentication is carried between requests
in a stateful HTTP session (`JSESSIONID`); this is not a JWT/stateless API.

The configured `APP_ADMIN_EMAIL` is promoted to `ADMIN` and marked
`system_admin` during startup seeding and login. The OAuth provider must confirm that
email as verified. Other new users receive `READ`.

| Role | Query documents | Ingest/update/delete documents | Assign roles |
| --- | --- | --- | --- |
| `READ` | Yes | No | No |
| `WRITE` | Yes | Yes | No |
| `CUSTODIAN` | Yes | Yes | Grant/revoke `WRITE` |
| `ADMIN` | Yes | Yes | Set any role, except changing the protected system admin |

The HTTP session cookie is automatically sent by browsers, so CSRF protection
remains enabled. Fetch `/api/csrf` in the session and send its token in the returned
header (`X-CSRF-TOKEN`) for POST, PUT, and DELETE requests.
The frontend handles the session lookup, Google sign-in redirect, CSRF token fetch,
and session logout through the shared `Frontend/src/api.ts` client. Vite development
uses `VITE_API_BASE_URL` (default `http://localhost:8080`) and the backend's
`FRONTEND_ORIGIN`; configure `OAUTH_SUCCESS_URL=http://localhost:5173/` when
running the backend for development.

## Document ingestion and folder organization

The controller at `/api/documents` accepts document create, update, delete, move,
and metadata lookup requests; `/api/folders` accepts folder create, rename, move,
delete, and contents-browsing requests. Only `WRITE`, `CUSTODIAN`, and `ADMIN` may
use the write routes; any authenticated role may browse (`GET`) both.

Folders (`Folder`/`FolderRepository`/`FolderService`) form a self-referencing tree
via `parent_id` (`null` = workspace root, like Google Drive's "My Drive"). Each
document optionally belongs to one folder (`document_meta.folder_id`). Folder
browsing (`FolderController#contents`) returns three things in one call: the
subfolders and documents directly inside the requested folder (filtered to what the
caller's role can see), and the breadcrumb chain from the root down to that folder,
which the frontend renders as clickable navigation. Folders carry their own
`min_role_required` so an entire subtree can be gated without tagging every file
inside it individually. Deleting a folder is blocked (409) while it still contains
subfolders or non-deleted documents, so content is never silently orphaned.

The document controller forwards create/update/delete commands to the Vert.x event
bus; the deployed `IngestionVerticle` executes the blocking persistence and model
calls on a worker thread. Moving a document or folder between folders is pure
metadata and bypasses the event bus entirely (no re-chunking/re-embedding needed).

For create/update, the ingestion service:

1. Saves document metadata and marks it as processing.
2. Splits text into overlapping, word-count-based chunks.
3. Requests one embedding per chunk from Ollama.
4. Saves each chunk and its vector.
5. Marks the document `READY` when the operation succeeds.

Document updates replace the active chunks; old chunk records are soft-deleted and
their embeddings removed. Document deletion soft-deletes its active chunks and
marks the document `DELETED`.

## RAG query

`POST /api/query` is available to every authenticated role. The query flow is:

1. `QueryIntentClassifier` asks the chat model whether the question is in scope for
   the configured corpus description. Out-of-scope requests are declined before
   vector retrieval.
2. `VectorSearchService` embeds an in-scope query using Ollama.
3. `ChunkVectorRepository` performs pgvector cosine-distance search. SQL restricts
   results to non-deleted chunks in `READY` documents whose `min_role_required`
   does not exceed the caller's role.
4. `RagOrchestrator` sends the retrieved text and original question to the chat
   model, instructing it to answer only from that context. The response includes
   source chunk and document IDs.

## Storage model

- `app_user`: OAuth-linked application identity, role, admin marker, and login times.
- `folder`: name, parent folder (self-reference, nullable = root), minimum retrieval
  role, creator, and soft-delete flag.
- `document_meta`: filename, uploader, optional folder, minimum retrieval role, and
  ingestion status.
- `document_chunk`: chunk content, order, minimum role, audit timestamps, and soft-delete flag.
- `chunk_embedding`: one pgvector embedding per chunk.

The schema is initialized by `schema.sql`; Hibernate uses `ddl-auto: validate` and
does not make schema changes.

## API entry points

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /oauth2/authorization/google` | Public | Begin Google sign-in |
| `GET /api/me` | Authenticated | Return current email and role |
| `GET /api/csrf` | Public | Obtain a CSRF token for the current session |
| `POST /api/documents` | `WRITE` and above | Create and ingest a document |
| `PUT /api/documents/{documentId}` | `WRITE` and above | Replace document content |
| `PUT /api/documents/{documentId}/move` | `WRITE` and above | Move a document to another folder |
| `DELETE /api/documents/{documentId}` | `WRITE` and above | Soft-delete a document |
| `GET /api/documents/{documentId}` | `WRITE` and above | Read document metadata |
| `GET /api/documents` | Authenticated | List documents visible to the caller's role |
| `GET /api/folders/contents` | Authenticated | List a folder's subfolders, documents, and breadcrumbs |
| `POST /api/folders` | `WRITE` and above | Create a folder |
| `PUT /api/folders/{folderId}` | `WRITE` and above | Rename a folder / change its minimum role |
| `PUT /api/folders/{folderId}/move` | `WRITE` and above | Move a folder under a new parent |
| `DELETE /api/folders/{folderId}` | `WRITE` and above | Delete an empty folder |
| `POST /api/query` | Authenticated | Ask a scoped RAG question |
| `GET /api/admin/users` | `CUSTODIAN` and `ADMIN` | List users |
| `PUT /api/admin/users/{userId}/role` | `CUSTODIAN` and `ADMIN` | Assign a role, with custodian restrictions |
| `POST /api/logout` | Authenticated | Invalidate the session |

## Current scope and limitations

- Chunking uses a fixed word-count window and overlap, not model tokenization.
- Ingestion and model calls are synchronous within the service transaction; larger
  deployments should decouple lengthy model calls from database transactions.
- Document/folder permissions use a single minimum-role threshold, not per-user or
  per-team ACLs.
- The RAG implementation uses a fixed top-K and one chat provider configuration.
- `schema.sql` is for initial setup. Treat schema changes to an existing named
  database volume as migrations; changing the SQL file alone does not update an
  already-created schema.

## CI/CD

`.github/workflows/ci.yml` runs on every push/PR to `main`: `mvn verify` (backend),
`npm run lint && npm run build` (frontend), and a Docker image build as a smoke test
for the Dockerfile. `.github/workflows/docker-publish.yml` builds and pushes the
production image to GitHub Container Registry on `vX.Y.Z` tags or manual dispatch.
