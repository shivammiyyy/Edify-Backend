import { useEffect, useState } from "react"
import { Activity, ArrowRight, Bot, CheckCircle2, Clock3, FileText, Folder, MessageSquare, Sparkles } from "lucide-react"
import { api, type CurrentUser, type DocumentItem, type FolderContents } from "./api"
import { Button, Card } from "./Commons/ui"

interface DashboardPageProps {
  user: CurrentUser
  conversationCount: number
  onAsk: (question: string) => void
  onOpenDocuments: () => void
}

const DashboardPage = ({ user, conversationCount, onAsk, onOpenDocuments }: DashboardPageProps) => {
  const [documents, setDocuments] = useState<DocumentItem[]>([])
  const [folders, setFolders] = useState<FolderContents["folders"]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  useEffect(() => {
    let active = true
    Promise.all([api.documents(), api.folderContents()])
      .then(([items, root]) => {
        if (!active) return
        setDocuments(items)
        setFolders(root.folders)
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : "Could not load workspace overview.")
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  const processing = documents.filter((document) =>
    !["READY", "FAILED", "DELETED"].includes(document.status),
  ).length
  const ready = documents.filter((document) => document.status === "READY").length
  const recent = [...documents].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 5)

  return (
    <section className="min-h-0 flex-1 overflow-y-auto bg-slate-50">
      <div className="mx-auto max-w-6xl space-y-8 px-5 py-8 md:px-10 md:py-10">
        <header>
          <p className="text-sm font-medium text-indigo-600">Your workspace</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950 md:text-4xl">
            Good to see you, {user.email.split("@")[0]}
          </h1>
          <p className="mt-2 text-sm text-slate-500">A clear view of your knowledge base and its latest activity.</p>
        </header>

        {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{error}</p>}

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
          <Metric icon={FileText} label="Documents" value={documents.length} loading={loading} />
          <Metric icon={Folder} label="Top-level folders" value={folders.length} loading={loading} />
          <Metric icon={Clock3} label="Processing" value={processing} loading={loading} />
          <Metric icon={CheckCircle2} label="Ready to search" value={ready} loading={loading} />
          <Metric icon={MessageSquare} label="Conversations" value={conversationCount} loading={false} />
        </div>

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(280px,0.9fr)]">
          <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
            <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
              <div>
                <h2 className="font-semibold text-slate-900">Recent documents</h2>
                <p className="mt-1 text-xs text-slate-500">Recently added to your accessible workspace</p>
              </div>
              <button type="button" onClick={onOpenDocuments} className="inline-flex items-center gap-1 text-sm font-medium text-indigo-600 hover:text-indigo-700">
                Browse <ArrowRight size={15} />
              </button>
            </div>
            {loading ? (
              <div className="space-y-3 p-5">{[0, 1, 2].map((item) => <div key={item} className="h-12 animate-pulse rounded-lg bg-slate-100" />)}</div>
            ) : recent.length ? (
              <div className="divide-y divide-slate-100">
                {recent.map((document) => (
                  <div key={document.id} className="flex items-center gap-3 px-5 py-3.5">
                    <span className="rounded-lg bg-slate-100 p-2 text-slate-500"><FileText size={17} /></span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-800">{document.filename}</p>
                      <p className="mt-0.5 text-xs text-slate-500">{new Date(document.createdAt).toLocaleDateString()}</p>
                    </div>
                    <span className={`rounded-full px-2.5 py-1 text-[11px] font-medium ${
                      document.status === "READY" ? "bg-emerald-50 text-emerald-700" :
                      document.status === "FAILED" ? "bg-rose-50 text-rose-700" : "bg-amber-50 text-amber-700"
                    }`}>{document.status}</span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="px-5 py-12 text-center">
                <FileText className="mx-auto text-slate-300" size={28} />
                <p className="mt-3 text-sm font-medium text-slate-700">Your knowledge base is ready for its first document</p>
                <button type="button" onClick={onOpenDocuments} className="mt-3 text-sm font-semibold text-indigo-600">Add a document</button>
              </div>
            )}
          </section>

          <section className="rounded-2xl border border-indigo-100 bg-white p-5 md:p-6">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600"><Sparkles size={19} /></div>
            <h2 className="mt-4 text-lg font-semibold text-slate-900">Ask your knowledge base</h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              Get a grounded answer from documents you can access, with source citations.
            </p>
            <Button type="button" onClick={() => onAsk("")} className="mt-5 w-full py-3">
              <Bot size={17} /> Open AI assistant
            </Button>
            <div className="mt-5 space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Try asking</p>
              {["What are the key policies?", "Summarize recent engineering guidance."].map((prompt) => (
                <button key={prompt} type="button" onClick={() => onAsk(prompt)} className="block w-full rounded-lg px-3 py-2 text-left text-sm text-slate-600 hover:bg-slate-50">
                  {prompt}
                </button>
              ))}
            </div>
          </section>
        </div>

        <section className="rounded-2xl border border-slate-200 bg-white">
          <div className="flex items-center gap-2 border-b border-slate-100 px-5 py-4">
            <Activity size={17} className="text-slate-500" />
            <div>
              <h2 className="text-sm font-semibold text-slate-900">Recent document activity</h2>
              <p className="mt-0.5 text-xs text-slate-500">Latest document additions visible to your role</p>
            </div>
          </div>
          {recent.length ? <div className="divide-y divide-slate-100 sm:grid sm:grid-cols-2 sm:divide-y-0">
            {recent.slice(0, 4).map((document, index) => (
              <div key={document.id} className={`flex items-center gap-3 px-5 py-3.5 ${index > 1 ? "sm:border-t sm:border-slate-100" : ""}`}>
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-50 text-indigo-600"><FileText size={15} /></span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-slate-700"><span className="font-medium">{document.filename}</span> added</p>
                  <p className="mt-0.5 text-xs text-slate-500">{new Date(document.createdAt).toLocaleString()}</p>
                </div>
                <span className="text-[11px] text-slate-500">{document.status}</span>
              </div>
            ))}
          </div> : <p className="px-5 py-8 text-sm text-slate-500">Document activity will appear when sources are added.</p>}
        </section>

        <p className="text-xs text-slate-400">Activity is based on document records; a separate audit-event feed is not yet available.</p>
      </div>
    </section>
  )
}

function Metric({ icon: Icon, label, value, loading }: {
  icon: typeof FileText
  label: string
  value: number
  loading: boolean
}) {
  return (
    <Card className="p-5">
      <span className="inline-flex rounded-lg bg-slate-100 p-2 text-slate-600"><Icon size={17} /></span>
      <p className="mt-4 text-sm text-slate-500">{label}</p>
      {loading ? <div className="mt-2 h-8 w-12 animate-pulse rounded bg-slate-100" /> : (
        <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900">{value}</p>
      )}
    </Card>
  )
}

export default DashboardPage
