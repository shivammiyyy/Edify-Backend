import { useCallback, useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react"
import {
  ChevronRight, FilePlus2, FileText, FolderPlus, Folder as FolderIcon, Home,
  LoaderCircle, Pencil, RefreshCw, Trash2, Upload, X,
} from "lucide-react"
import { api, type CurrentUser, type DocumentItem, type FolderContents, type Role } from "../api"

interface DocumentPageProps {
  user: CurrentUser
}

const roleOrder: Role[] = ["READ", "WRITE", "CUSTODIAN", "ADMIN"]
const canWrite = (role: Role) => role !== "READ"
const prettyDate = (value: string) => new Date(value).toLocaleDateString(undefined, {
  year: "numeric", month: "short", day: "numeric",
})

const emptyContents: FolderContents = { folders: [], documents: [], breadcrumbs: [] }

const DocumentPage = ({ user }: DocumentPageProps) => {
  const [currentFolderId, setCurrentFolderId] = useState<string | null>(null)
  const [contents, setContents] = useState<FolderContents>(emptyContents)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")
  const [filename, setFilename] = useState("")
  const [content, setContent] = useState("")
  const [minRole, setMinRole] = useState<Role>("READ")
  const [uploading, setUploading] = useState(false)
  const [editing, setEditing] = useState<DocumentItem | null>(null)
  const [editContent, setEditContent] = useState("")
  const [saving, setSaving] = useState(false)
  const [creatingFolder, setCreatingFolder] = useState(false)
  const [newFolderName, setNewFolderName] = useState("")
  const [newFolderRole, setNewFolderRole] = useState<Role>("READ")
  const fileInput = useRef<HTMLInputElement>(null)

  const load = useCallback(async (folderId: string | null) => {
    setLoading(true)
    setError("")
    try {
      setContents(await api.folderContents(folderId))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to load this folder.")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let active = true
    api.folderContents(currentFolderId).then((data) => {
      if (!active) return
      setContents(data)
      setError("")
    }).catch((cause: unknown) => {
      if (active) setError(cause instanceof Error ? cause.message : "Unable to load this folder.")
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [currentFolderId])

  const openFolder = (folderId: string | null) => {
    setNotice("")
    setError("")
    setLoading(true)
    setCurrentFolderId(folderId)
  }

  const createFolder = async (event: FormEvent) => {
    event.preventDefault()
    if (!newFolderName.trim()) return
    setError("")
    setNotice("")
    try {
      await api.createFolder(newFolderName.trim(), currentFolderId, newFolderRole)
      setNewFolderName("")
      setNewFolderRole("READ")
      setCreatingFolder(false)
      await load(currentFolderId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create folder.")
    }
  }

  const removeFolder = async (folderId: string, name: string) => {
    if (!window.confirm(`Delete the empty folder "${name}"?`)) return
    setError("")
    setNotice("")
    try {
      await api.deleteFolder(folderId)
      setNotice(`Folder "${name}" was deleted.`)
      await load(currentFolderId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Only empty folders can be deleted.")
    }
  }

  const readFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    setError("")
    if (file.size > 2 * 1024 * 1024) {
      setError("Files must be 2 MB or smaller.")
      event.target.value = ""
      return
    }
    try {
      setFilename(file.name)
      setContent(await file.text())
    } catch {
      setError("This file could not be read as text.")
    }
  }

  const upload = async (event: FormEvent) => {
    event.preventDefault()
    if (!filename.trim() || !content.trim()) return
    setUploading(true)
    setError("")
    setNotice("")
    try {
      const result = await api.createDocument(filename.trim(), content, minRole, currentFolderId)
      setNotice(`Document ingested with ${result.chunkCount} chunks.`)
      setFilename("")
      setContent("")
      if (fileInput.current) fileInput.current.value = ""
      await load(currentFolderId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Document upload failed.")
    } finally {
      setUploading(false)
    }
  }

  const startEdit = (document: DocumentItem) => {
    setEditing(document)
    setEditContent("")
    setError("")
  }

  const saveEdit = async (event: FormEvent) => {
    event.preventDefault()
    if (!editing || !editContent.trim()) return
    setSaving(true)
    setError("")
    try {
      const result = await api.updateDocument(editing.id, editContent)
      setNotice(`Document updated with ${result.chunkCount} chunks.`)
      setEditing(null)
      await load(currentFolderId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Document update failed.")
    } finally {
      setSaving(false)
    }
  }

  const remove = async (document: DocumentItem) => {
    if (!window.confirm(`Delete "${document.filename}"? This removes it from search results.`)) return
    setError("")
    setNotice("")
    try {
      await api.deleteDocument(document.id)
      setNotice(`"${document.filename}" was deleted.`)
      await load(currentFolderId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Document deletion failed.")
    }
  }

  const minimumRoles = roleOrder.filter((role) => roleOrder.indexOf(role) <= roleOrder.indexOf(user.role))
  const { folders, documents, breadcrumbs } = contents

  return (
    <section className="min-h-0 flex-1 overflow-y-auto">
      <header className="border-b border-slate-100 px-6 py-6 md:px-10">
        <h1 className="text-2xl font-semibold tracking-tight">Documents</h1>
        <p className="mt-1 text-sm text-slate-500">Browse your workspace like a drive: folders, uploads, and search sources.</p>
        <nav className="mt-4 flex flex-wrap items-center gap-1 text-sm">
          <button type="button" onClick={() => openFolder(null)}
            className={`inline-flex items-center gap-1 rounded-lg px-2 py-1 hover:bg-slate-100 ${currentFolderId === null ? "font-semibold text-slate-900" : "text-slate-500"}`}>
            <Home size={14} /> My Drive
          </button>
          {breadcrumbs.map((crumb, index) => (
            <span key={crumb.id} className="flex items-center gap-1">
              <ChevronRight size={14} className="text-slate-300" />
              <button type="button" onClick={() => openFolder(crumb.id)}
                className={`rounded-lg px-2 py-1 hover:bg-slate-100 ${index === breadcrumbs.length - 1 ? "font-semibold text-slate-900" : "text-slate-500"}`}>
                {crumb.name}
              </button>
            </span>
          ))}
        </nav>
      </header>
      <div className="mx-auto max-w-5xl space-y-8 px-5 py-7 md:px-10">
        {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{error}</p>}
        {notice && <p role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</p>}

        <section>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-semibold">Folders</h2>
            <div className="flex items-center gap-2">
              {canWrite(user.role) && (
                <button type="button" onClick={() => setCreatingFolder(true)}
                  className="inline-flex items-center gap-2 rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50">
                  <FolderPlus size={15} /> New folder
                </button>
              )}
              <button type="button" onClick={() => void load(currentFolderId)} aria-label="Refresh"
                className="rounded-lg border border-slate-200 p-2 text-slate-500 hover:bg-slate-50"><RefreshCw size={16} /></button>
            </div>
          </div>
          {folders.length === 0 && !creatingFolder ? (
            <p className="text-sm text-slate-500">No subfolders here yet.</p>
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
              {folders.map((folder) => (
                <div key={folder.id} className="group relative flex items-center gap-2 rounded-xl border border-slate-200 bg-white p-3 hover:border-indigo-300 hover:bg-indigo-50/40">
                  <button type="button" onClick={() => openFolder(folder.id)} className="flex min-w-0 flex-1 items-center gap-2 text-left">
                    <FolderIcon size={20} className="shrink-0 text-indigo-500" />
                    <span className="truncate text-sm font-medium">{folder.name}</span>
                  </button>
                  {canWrite(user.role) && (
                    <button type="button" onClick={() => void removeFolder(folder.id, folder.name)} title="Delete empty folder"
                      className="rounded-lg p-1.5 text-slate-400 opacity-0 hover:bg-rose-50 hover:text-rose-700 group-hover:opacity-100">
                      <Trash2 size={14} />
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>

        {canWrite(user.role) && (
          <form onSubmit={upload} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
            <div className="mb-5 flex items-center gap-3">
              <div className="rounded-xl bg-indigo-50 p-2.5 text-indigo-600"><FilePlus2 size={20} /></div>
              <div>
                <h2 className="font-semibold">Add a knowledge source</h2>
                <p className="text-sm text-slate-500">
                  Uploads to {currentFolderId ? `"${breadcrumbs[breadcrumbs.length - 1]?.name ?? "this folder"}"` : "My Drive"}.
                </p>
              </div>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="block text-sm font-medium text-slate-700">
                Document name
                <input value={filename} onChange={(event) => setFilename(event.target.value)} required maxLength={500}
                  placeholder="e.g. Engineering handbook.txt"
                  className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal outline-none focus:border-indigo-400 focus:ring-4 focus:ring-indigo-50" />
              </label>
              <label className="block text-sm font-medium text-slate-700">
                Minimum access role
                <select value={minRole} onChange={(event) => setMinRole(event.target.value as Role)}
                  className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 outline-none focus:border-indigo-400">
                  {minimumRoles.map((role) => <option key={role} value={role}>{role} and above</option>)}
                </select>
              </label>
            </div>
            <label className="mt-4 block text-sm font-medium text-slate-700">
              Content
              <textarea value={content} onChange={(event) => setContent(event.target.value)} required rows={6}
                placeholder="Paste the document text here, or select a plain-text file below."
                className="mt-2 w-full resize-y rounded-xl border border-slate-300 px-3 py-2.5 font-normal leading-6 outline-none focus:border-indigo-400 focus:ring-4 focus:ring-indigo-50" />
            </label>
            <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
              <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
                <Upload size={16} />
                Choose text file
                <input ref={fileInput} type="file" accept=".txt,.md,.csv,.json,.xml,.html,text/plain" onChange={readFile} className="sr-only" />
              </label>
              <button disabled={uploading || !filename.trim() || !content.trim()}
                className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-slate-300">
                {uploading ? <LoaderCircle size={16} className="animate-spin" /> : <FilePlus2 size={16} />}
                {uploading ? "Ingesting…" : "Ingest document"}
              </button>
            </div>
          </form>
        )}

        <section>
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h2 className="font-semibold">Documents in this folder</h2>
              <p className="mt-1 text-sm text-slate-500">{documents.length} document{documents.length === 1 ? "" : "s"} visible to your role</p>
            </div>
            <button type="button" onClick={() => void load(currentFolderId)} aria-label="Refresh documents"
              className="rounded-lg border border-slate-200 p-2 text-slate-500 hover:bg-slate-50"><RefreshCw size={17} /></button>
          </div>
          {loading ? (
            <div className="flex items-center gap-2 py-10 text-sm text-slate-500"><LoaderCircle className="animate-spin" size={18} /> Loading documents…</div>
          ) : documents.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-300 px-5 py-12 text-center">
              <FileText className="mx-auto text-slate-300" size={30} />
              <p className="mt-3 font-medium text-slate-700">No documents here yet</p>
              <p className="mt-1 text-sm text-slate-500">Ingest a document to make it searchable.</p>
            </div>
          ) : (
            <div className="overflow-hidden rounded-2xl border border-slate-200">
              {documents.map((document, index) => (
                <article key={document.id} className={`flex flex-wrap items-center gap-4 px-4 py-4 md:px-5 ${index ? "border-t border-slate-100" : ""}`}>
                  <div className="rounded-xl bg-slate-100 p-3 text-slate-600"><FileText size={19} /></div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{document.filename}</p>
                    <p className="mt-1 text-xs text-slate-500">{prettyDate(document.createdAt)} · {document.minRoleRequired}+ access</p>
                  </div>
                  <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                    document.status === "READY" ? "bg-emerald-50 text-emerald-700" :
                    document.status === "FAILED" ? "bg-rose-50 text-rose-700" : "bg-amber-50 text-amber-700"
                  }`}>{document.status}</span>
                  {canWrite(user.role) && document.status !== "DELETED" && (
                    <div className="flex gap-1">
                      <button type="button" onClick={() => startEdit(document)} title="Replace content"
                        className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-900"><Pencil size={16} /></button>
                      <button type="button" onClick={() => void remove(document)} title="Delete document"
                        className="rounded-lg p-2 text-slate-500 hover:bg-rose-50 hover:text-rose-700"><Trash2 size={16} /></button>
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>
      </div>

      {creatingFolder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
          <form onSubmit={createFolder} className="w-full max-w-md rounded-2xl bg-white p-5 shadow-xl md:p-6">
            <div className="mb-4 flex items-start justify-between gap-4">
              <div><h2 className="font-semibold">New folder</h2><p className="mt-1 text-sm text-slate-500">Created inside {currentFolderId ? (breadcrumbs[breadcrumbs.length - 1]?.name ?? "this folder") : "My Drive"}.</p></div>
              <button type="button" onClick={() => setCreatingFolder(false)} aria-label="Close" className="rounded-lg p-2 text-slate-500 hover:bg-slate-100"><X size={18} /></button>
            </div>
            <label className="block text-sm font-medium text-slate-700">
              Folder name
              <input autoFocus value={newFolderName} onChange={(event) => setNewFolderName(event.target.value)} required maxLength={255}
                placeholder="e.g. Onboarding"
                className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal outline-none focus:border-indigo-400 focus:ring-4 focus:ring-indigo-50" />
            </label>
            <label className="mt-4 block text-sm font-medium text-slate-700">
              Minimum access role
              <select value={newFolderRole} onChange={(event) => setNewFolderRole(event.target.value as Role)}
                className="mt-2 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 outline-none focus:border-indigo-400">
                {minimumRoles.map((role) => <option key={role} value={role}>{role} and above</option>)}
              </select>
            </label>
            <div className="mt-5 flex justify-end gap-3">
              <button type="button" onClick={() => setCreatingFolder(false)} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-medium hover:bg-slate-50">Cancel</button>
              <button disabled={!newFolderName.trim()} className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:bg-slate-300">
                <FolderPlus size={15} />Create folder
              </button>
            </div>
          </form>
        </div>
      )}

      {editing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
          <form onSubmit={saveEdit} className="w-full max-w-2xl rounded-2xl bg-white p-5 shadow-xl md:p-6">
            <div className="mb-4 flex items-start justify-between gap-4">
              <div><h2 className="font-semibold">Replace document content</h2><p className="mt-1 text-sm text-slate-500">{editing.filename}</p></div>
              <button type="button" onClick={() => setEditing(null)} aria-label="Close" className="rounded-lg p-2 text-slate-500 hover:bg-slate-100"><X size={18} /></button>
            </div>
            <textarea autoFocus required rows={10} value={editContent} onChange={(event) => setEditContent(event.target.value)}
              placeholder="Paste the complete replacement text here."
              className="w-full resize-y rounded-xl border border-slate-300 p-3 text-sm leading-6 outline-none focus:border-indigo-400 focus:ring-4 focus:ring-indigo-50" />
            <div className="mt-4 flex justify-end gap-3">
              <button type="button" onClick={() => setEditing(null)} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-medium hover:bg-slate-50">Cancel</button>
              <button disabled={saving || !editContent.trim()} className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:bg-slate-300">
                {saving && <LoaderCircle size={15} className="animate-spin" />}Replace and re-index
              </button>
            </div>
          </form>
        </div>
      )}
    </section>
  )
}

export default DocumentPage
