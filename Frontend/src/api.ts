export type Role = 'READ' | 'WRITE' | 'CUSTODIAN' | 'ADMIN'

export interface CurrentUser {
  email: string
  role: Role
}

export interface DocumentItem {
  id: string
  filename: string
  minRoleRequired: Role
  status: 'PENDING' | 'UPLOADED' | 'PARSING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXING' | 'READY' | 'FAILED' | 'DELETED'
  createdAt: string
  updatedAt: string | null
  folderId: string | null
  activeVersionId?: string | null
}

export interface DocumentVersion {
  id: string
  versionNumber: number
  contentHash: string
  status: string
  createdAt: string
  createdBy: string
  active: boolean
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
  jobId?: string | null
  chunkCount: number
  status: string
  message: string
}

export interface IngestionJob {
  jobId: string
  documentId: string
  documentVersionId: string
  status: 'UPLOADED' | 'PARSING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXING' | 'READY' | 'FAILED'
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

export interface RagAnswer {
  answer: string
  inScope: boolean
  category: string
  sourceChunkIds: string[]
  sourceDocumentIds: string[]
  sources: SourceCitation[]
}

export interface SourceCitation {
  documentId: string
  documentName: string
  documentVersionId: string | null
  chunkId: string
  page: number | null
  section: string | null
  score: number
  excerpt: string
}

export interface RetrievalFilters {
  department?: string
  documentType?: string
  tags?: string[]
  ownerId?: string
}

export interface ConversationSummary {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface ConversationMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
  sources: SourceCitation[]
}

export interface ConversationDetail {
  conversation: ConversationSummary
  messages: ConversationMessage[]
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

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL
  ?? (import.meta.env.DEV ? 'http://localhost:8080' : '')

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

export type ConversationEvent =
  | { type: 'status'; status: string; userMessageId?: string }
  | { type: 'token'; token: string }
  | { type: 'source'; source: SourceCitation }
  | { type: 'completion'; answer: RagAnswer }
  | { type: 'error'; message: string }

async function streamConversationMessage(
  id: string,
  question: string,
  filters: RetrievalFilters,
  onEvent: (event: ConversationEvent) => void,
  retryMessageId?: string,
): Promise<void> {
  const csrf = await request<{ headerName: string; token: string }>('/api/csrf')
  const response = await fetch(`${apiBaseUrl}/api/conversations/${encodeURIComponent(id)}/messages`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ question, ...filters, ...(retryMessageId ? { retryMessageId } : {}) }),
  })
  if (!response.ok) {
    const body = await response.text()
    let message = body || `Request failed (${response.status})`
    try {
      const parsed: { message?: string } = JSON.parse(body)
      message = parsed.message ?? message
    } catch {
      // Keep the server response text when it is not JSON.
    }
    throw new ApiError(message, response.status)
  }
  if (!response.body) throw new Error('The server did not provide a response stream.')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = ''
  let dataLines: string[] = []
  const dispatch = () => {
    if (!eventName || !dataLines.length) return
    const data = JSON.parse(dataLines.join('\n')) as unknown
    switch (eventName) {
      case 'status':
        onEvent({
          type: 'status',
          status: (data as { status: string }).status,
          userMessageId: (data as { userMessageId?: string }).userMessageId,
        })
        break
      case 'token':
        onEvent({ type: 'token', token: (data as { token: string }).token })
        break
      case 'source':
        onEvent({ type: 'source', source: data as SourceCitation })
        break
      case 'completion':
        onEvent({ type: 'completion', answer: data as RagAnswer })
        break
      case 'error':
        onEvent({ type: 'error', message: (data as { message: string }).message })
        break
    }
    eventName = ''
    dataLines = []
  }

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    let newline = buffer.indexOf('\n')
    while (newline >= 0) {
      const line = buffer.slice(0, newline).replace(/\r$/, '')
      buffer = buffer.slice(newline + 1)
      if (!line) {
        dispatch()
      } else if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
      newline = buffer.indexOf('\n')
    }
    if (done) break
  }
  if (buffer) {
    if (buffer.startsWith('event:')) eventName = buffer.slice(6).trim()
    else if (buffer.startsWith('data:')) dataLines.push(buffer.slice(5).trimStart())
  }
  dispatch()
}

export const api = {
  me: () => request<CurrentUser>('/api/me'),
  documents: () => request<DocumentItem[]>('/api/documents'),
  ingestionJob: (id: string) => request<IngestionJob>(`/api/ingestion-jobs/${encodeURIComponent(id)}`),
  createDocument: (filename: string, content: string, minRoleRequired: Role, folderId?: string | null) =>
    mutate<IngestionResult>('/api/documents', 'POST', { filename, content, minRoleRequired, folderId: folderId ?? null }),
  uploadDocument: async (file: File, minRoleRequired: Role, folderId?: string | null) => {
    const csrf = await request<{ headerName: string; token: string }>('/api/csrf')
    const form = new FormData()
    form.append('file', file)
    form.append('minRoleRequired', minRoleRequired)
    if (folderId) form.append('folderId', folderId)
    const response = await fetch(`${apiBaseUrl}/api/documents/upload`, {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json', [csrf.headerName]: csrf.token },
      body: form,
    })
    if (!response.ok) {
      const body = await response.text()
      let message = body || `Request failed (${response.status})`
      try {
        const parsed: { message?: string } = JSON.parse(body)
        message = parsed.message ?? message
      } catch {
        // Keep the response text when it is not JSON.
      }
      throw new ApiError(message, response.status)
    }
    return response.json() as Promise<IngestionResult>
  },
  documentVersions: (id: string) => request<DocumentVersion[]>(`/api/documents/${encodeURIComponent(id)}/versions`),
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
  query: (question: string, filters: RetrievalFilters = {}) =>
    mutate<RagAnswer>('/api/query', 'POST', { question, ...filters }),
  conversations: () => request<ConversationSummary[]>('/api/conversations'),
  createConversation: () => mutate<ConversationSummary>('/api/conversations', 'POST'),
  conversation: (id: string) => request<ConversationDetail>(`/api/conversations/${encodeURIComponent(id)}`),
  streamConversationMessage: streamConversationMessage,
  users: () => request<UserItem[]>('/api/admin/users'),
  setRole: (id: string, role: Role) =>
    mutate<UserItem>(`/api/admin/users/${encodeURIComponent(id)}/role`, 'PUT', { role }),
  logout: () => mutate<void>('/api/logout', 'POST'),
}

export const signInUrl = `${apiBaseUrl}/oauth2/authorization/google`
