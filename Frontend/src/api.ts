export type Role = 'READ' | 'WRITE' | 'CUSTODIAN' | 'ADMIN'

export interface CurrentUser {
  email: string
  role: Role
}

export interface DocumentItem {
  id: string
  filename: string
  minRoleRequired: Role
  status: 'PENDING' | 'CHUNKING' | 'EMBEDDING' | 'READY' | 'FAILED' | 'DELETED'
  createdAt: string
  updatedAt: string | null
  folderId: string | null
}

export interface FolderItem {
  id: string
  name: string
  parentId: string | null
  minRoleRequired: Role
  createdAt: string
  updatedAt: string | null
}

export interface FolderContents {
  folders: FolderItem[]
  documents: DocumentItem[]
  breadcrumbs: FolderItem[]
}

export interface IngestionResult {
  documentId: string
  chunkCount: number
  status: string
  message: string
}

export interface RagAnswer {
  answer: string
  inScope: boolean
  category: string
  sourceChunkIds: string[]
  sourceDocumentIds: string[]
}

export interface UserItem {
  id: string
  email: string
  displayName: string | null
  role: Role
  systemAdmin: boolean
}

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

const apiBaseUrl = import.meta.env.DEV
  ? (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080')
  : ''

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  })

  if (!response.ok) {
    const body = await response.text()
    let message = body || `Request failed (${response.status})`
    try {
      const parsed: { detail?: string; message?: string; error?: string } = JSON.parse(body)
      message = parsed.detail ?? parsed.message ?? parsed.error ?? message
    } catch {
      // Keep the server response text when it is not JSON.
    }
    throw new ApiError(message, response.status)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function mutate<T>(path: string, method: 'POST' | 'PUT' | 'DELETE', body?: unknown): Promise<T> {
  const csrf = await request<{ headerName: string; token: string }>('/api/csrf')
  return request<T>(path, {
    method,
    headers: { [csrf.headerName]: csrf.token },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
}

export const api = {
  me: () => request<CurrentUser>('/api/me'),
  documents: () => request<DocumentItem[]>('/api/documents'),
  createDocument: (filename: string, content: string, minRoleRequired: Role, folderId?: string | null) =>
    mutate<IngestionResult>('/api/documents', 'POST', { filename, content, minRoleRequired, folderId: folderId ?? null }),
  updateDocument: (id: string, content: string) =>
    mutate<IngestionResult>(`/api/documents/${encodeURIComponent(id)}`, 'PUT', { content }),
  deleteDocument: (id: string) =>
    mutate<IngestionResult>(`/api/documents/${encodeURIComponent(id)}`, 'DELETE'),
  moveDocument: (id: string, folderId: string | null) =>
    mutate<DocumentItem>(`/api/documents/${encodeURIComponent(id)}/move`, 'PUT', { folderId }),
  folderContents: (folderId?: string | null) =>
    request<FolderContents>(`/api/folders/contents${folderId ? `?folderId=${encodeURIComponent(folderId)}` : ''}`),
  createFolder: (name: string, parentId: string | null, minRoleRequired: Role) =>
    mutate<FolderItem>('/api/folders', 'POST', { name, parentId, minRoleRequired }),
  renameFolder: (id: string, name: string, minRoleRequired: Role) =>
    mutate<FolderItem>(`/api/folders/${encodeURIComponent(id)}`, 'PUT', { name, minRoleRequired }),
  moveFolder: (id: string, parentId: string | null) =>
    mutate<FolderItem>(`/api/folders/${encodeURIComponent(id)}/move`, 'PUT', { parentId }),
  deleteFolder: (id: string) =>
    mutate<void>(`/api/folders/${encodeURIComponent(id)}`, 'DELETE'),
  query: (question: string) =>
    mutate<RagAnswer>('/api/query', 'POST', { question }),
  users: () => request<UserItem[]>('/api/admin/users'),
  setRole: (id: string, role: Role) =>
    mutate<UserItem>(`/api/admin/users/${encodeURIComponent(id)}/role`, 'PUT', { role }),
  logout: () => mutate<void>('/api/logout', 'POST'),
}

export const signInUrl = `${apiBaseUrl}/oauth2/authorization/google`
