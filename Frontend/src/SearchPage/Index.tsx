import { useEffect, useRef, useState, type FormEvent } from "react"
import { ArrowUp, BookOpen, LoaderCircle, Menu, RotateCcw, Sparkles } from "lucide-react"
import {
  api,
  type ConversationDetail,
  type ConversationSummary,
  type CurrentUser,
  type RagAnswer,
  type SourceCitation,
} from "../api"
import DocumentPage from "../DocumentPage/Index"
import UserAdminPage from "../UserAdminPage"
import DashboardPage from "../DashboardPage"
import SettingsPage, { type ThemePreference } from "../SettingsPage"
import SideBar, { type WorkspaceView } from "./components/SideBar"
import MarkdownAnswer from "./components/MarkdownAnswer"

interface Message {
  id: string
  userMessageId?: string
  question: string
  result?: RagAnswer
  streamedAnswer?: string
  streamedSources?: SourceCitation[]
  status?: string
  pending?: boolean
  error?: string
}

interface WorkspaceProps {
  user: CurrentUser
  onLogout: () => void
  loggingOut: boolean
}

const Index = ({ user, onLogout, loggingOut }: WorkspaceProps) => {
  const [view, setView] = useState<WorkspaceView>("dashboard")
  const [question, setQuestion] = useState("")
  const [messages, setMessages] = useState<Message[]>([])
  const [conversations, setConversations] = useState<ConversationSummary[]>([])
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => localStorage.getItem("edify.sidebar-collapsed") === "true")
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)
  const [theme, setTheme] = useState<ThemePreference>(() => {
    const preference = localStorage.getItem("edify.theme")
    return preference === "light" || preference === "dark" ? preference : "system"
  })
  const [requestError, setRequestError] = useState("")
  const scrollRef = useRef<HTMLDivElement>(null)
  const nextId = useRef(0)

  useEffect(() => {
    localStorage.setItem("edify.theme", theme)
    const media = window.matchMedia("(prefers-color-scheme: dark)")
    const updateTheme = () => {
      const dark = theme === "dark" || (theme === "system" && media.matches)
      document.documentElement.classList.toggle("dark", dark)
      document.documentElement.style.colorScheme = dark ? "dark" : "light"
    }
    updateTheme()
    media.addEventListener("change", updateTheme)
    return () => media.removeEventListener("change", updateTheme)
  }, [theme])

  useEffect(() => {
    localStorage.setItem("edify.sidebar-collapsed", String(sidebarCollapsed))
  }, [sidebarCollapsed])

  useEffect(() => {
    void api.conversations().then((items) => {
      setConversations(items)
    }).catch((error: unknown) => {
      setRequestError(error instanceof Error ? error.message : "Could not load conversations.")
    })
  }, [])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" })
  }, [messages])

  const sendQuestion = async (text: string, id: string, retryMessageId?: string) => {
    setMessages((current) => current.map((message) => message.id === id
      ? { ...message, pending: true, error: undefined, result: undefined, streamedAnswer: "", streamedSources: [], status: "searching" }
      : message))
    try {
      let targetConversationId = conversationId
      if (!targetConversationId) {
        const created = await api.createConversation()
        targetConversationId = created.id
        setConversationId(created.id)
        setConversations((current) => [created, ...current])
      }
      await api.streamConversationMessage(targetConversationId, text, {}, (event) => {
        setMessages((current) => current.map((message) => {
          if (message.id !== id) return message
          if (event.type === "token") {
            return { ...message, streamedAnswer: (message.streamedAnswer ?? "") + event.token }
          }
          if (event.type === "source") {
            return { ...message, streamedSources: [...(message.streamedSources ?? []), event.source] }
          }
          if (event.type === "status") return {
            ...message,
            status: event.status,
            userMessageId: event.userMessageId ?? message.userMessageId,
          }
          if (event.type === "completion") {
            return { ...message, result: event.answer, streamedAnswer: undefined, pending: false }
          }
          return { ...message, pending: false, error: event.message }
        }))
      }, retryMessageId)
      setMessages((current) => current.map((message) =>
        message.id === id ? { ...message, pending: false } : message,
      ))
      const refreshed = await api.conversations()
      setConversations(refreshed)
    } catch (error) {
      setMessages((current) => current.map((message) =>
        message.id === id
          ? { ...message, pending: false, error: error instanceof Error ? error.message : "The query failed." }
          : message,
      ))
    }
  }

  const ask = async (event?: FormEvent) => {
    event?.preventDefault()
    const text = question.trim()
    if (!text || messages.some((message) => message.pending)) return

    const id = `message-${nextId.current++}`
    setQuestion("")
    setRequestError("")
    setMessages((current) => [...current, { id, question: text, pending: true }])
    await sendQuestion(text, id)
  }

  const retryMessage = (message: Message) => {
    if (!message.userMessageId || message.pending || messages.some((item) => item.pending)) return
    void sendQuestion(message.question, message.id, message.userMessageId)
  }

  const startNewConversation = () => {
    setConversationId(null)
    setMessages([])
    setRequestError("")
    setView("chat")
  }

  const selectConversation = async (id: string) => {
    try {
      const detail = await api.conversation(id)
      setConversationId(id)
      setMessages(toMessages(detail))
      setView("chat")
    } catch (error) {
      setRequestError(error instanceof Error ? error.message : "Could not load this conversation.")
    }
  }

  const askFromDashboard = (prompt: string) => {
    setView("chat")
    setQuestion(prompt)
  }

  const titles: Record<WorkspaceView, { title: string; subtitle: string }> = {
    dashboard: { title: "Overview", subtitle: "Your knowledge workspace at a glance" },
    chat: { title: "AI assistant", subtitle: "Answers grounded in documents you can access" },
    documents: { title: "Knowledge", subtitle: "Documents, folders, and source management" },
    users: { title: "Access management", subtitle: "Workspace roles and permissions" },
    settings: { title: "Settings", subtitle: "Profile, appearance, and AI preferences" },
  }

  return (
    <div className="flex h-dvh min-h-[560px] overflow-hidden bg-white text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <SideBar
        user={user}
        view={view}
        conversations={conversations}
        activeConversationId={conversationId}
        collapsed={sidebarCollapsed}
        mobileOpen={mobileSidebarOpen}
        onNavigate={setView}
        onToggleCollapsed={() => setSidebarCollapsed((current) => !current)}
        onCloseMobile={() => setMobileSidebarOpen(false)}
        onNewConversation={() => { startNewConversation(); setMobileSidebarOpen(false) }}
        onSelectConversation={(id) => { void selectConversation(id) }}
        onLogout={onLogout}
        loggingOut={loggingOut}
      />

      <main className="flex min-w-0 flex-1 flex-col bg-white dark:bg-slate-950">
        <header className="flex min-h-16 items-center gap-3 border-b border-slate-100 px-4 md:px-7 dark:border-slate-800">
          <button type="button" onClick={() => setMobileSidebarOpen(true)} aria-label="Open navigation"
            className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 md:hidden dark:text-slate-300 dark:hover:bg-slate-800">
            <Menu size={19} />
          </button>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">{titles[view].title}</p>
            <p className="hidden truncate text-xs text-slate-500 sm:block dark:text-slate-400">{titles[view].subtitle}</p>
          </div>
          {view === "chat" && (
            <div className="hidden items-center gap-2 rounded-full bg-indigo-50 px-3 py-1.5 text-xs font-medium text-indigo-700 sm:flex dark:bg-indigo-950 dark:text-indigo-300">
              <Sparkles size={14} /> Private knowledge search
            </div>
          )}
        </header>
        {view === "dashboard" ? (
          <DashboardPage user={user} conversationCount={conversations.length} onAsk={askFromDashboard}
            onOpenDocuments={() => setView("documents")} />
        ) : view === "documents" ? (
          <DocumentPage user={user} />
        ) : view === "users" ? (
          <UserAdminPage currentUser={user} />
        ) : view === "settings" ? (
          <SettingsPage user={user} theme={theme} onThemeChange={setTheme} />
        ) : (
          <>
            <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto px-4 py-8 md:px-10">
              <div className="mx-auto flex max-w-3xl flex-col gap-7">
                {messages.length === 0 ? (
                  <div className="flex min-h-[55vh] flex-col items-center justify-center text-center">
                    <div className="mb-6 flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600">
                      <BookOpen size={25} />
                    </div>
                    <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">How can I help?</h1>
                    <p className="mt-3 max-w-lg text-sm leading-6 text-slate-500 md:text-base">
                      Ask a question about your knowledge base. Answers are generated from documents
                      you have permission to access.
                    </p>
                    <div className="mt-8 grid w-full gap-3 sm:grid-cols-2">
                      {[
                        "What are the key policies in the knowledge base?",
                        "Summarize the most relevant engineering guidance.",
                      ].map((suggestion) => (
                        <button
                          key={suggestion}
                          type="button"
                          onClick={() => setQuestion(suggestion)}
                          className="rounded-xl border border-slate-200 p-4 text-left text-sm text-slate-600 transition hover:border-indigo-300 hover:bg-indigo-50/40"
                        >
                          {suggestion}
                        </button>
                      ))}
                    </div>
                  </div>
                ) : messages.map((message) => (
                  <article key={message.id} className="space-y-4">
                    <div className="ml-auto max-w-[90%] rounded-2xl rounded-br-sm bg-slate-100 px-4 py-3 text-sm leading-6 md:max-w-[80%]">
                      {message.question}
                    </div>
                    <div className="max-w-[95%] text-sm leading-7 md:max-w-[90%]">
                      {message.pending ? (
                        message.streamedAnswer ? (
                          <div>
                            <MarkdownAnswer content={message.streamedAnswer} />
                            <span className="ml-2 inline-flex items-center gap-1 text-xs text-slate-400">
                              <LoaderCircle size={13} className="animate-spin" />
                              {message.status === "generating" ? "Writing…" : "Searching…"}
                            </span>
                            <SourceCards sources={message.streamedSources ?? []} />
                          </div>
                        ) : (
                          <div className="flex items-center gap-2 text-slate-500" role="status">
                            <LoaderCircle size={17} className="animate-spin" />
                            Searching your documents and preparing an answer…
                          </div>
                        )
                      ) : message.error ? (
                        <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-800 dark:border-rose-900 dark:bg-rose-950/50 dark:text-rose-200">
                          <p>{message.error}</p>
                          {message.userMessageId && <button type="button" onClick={() => retryMessage(message)}
                            className="mt-3 inline-flex items-center gap-2 rounded-lg border border-rose-300 px-3 py-1.5 text-xs font-semibold hover:bg-rose-100 dark:border-rose-800 dark:hover:bg-rose-900">
                            <RotateCcw size={13} /> Retry response
                          </button>}
                        </div>
                      ) : message.result ? (
                        <>
                          <MarkdownAnswer content={message.result.answer} />
                          <SourceCards sources={message.result.sources.length
                            ? message.result.sources
                            : message.streamedSources ?? []} />
                        </>
                      ) : null}
                    </div>
                  </article>
                ))}
              </div>
            </div>

            <div className="border-t border-slate-100 px-4 pb-5 pt-4 md:px-10">
              <form onSubmit={ask} className="mx-auto max-w-3xl">
                {requestError && <p role="alert" className="mb-2 text-sm text-rose-700">{requestError}</p>}
                <div className="flex items-end gap-3 rounded-2xl border border-slate-300 bg-white p-2 shadow-sm transition focus-within:border-indigo-400 focus-within:ring-4 focus-within:ring-indigo-50">
                  <textarea
                    rows={1}
                    value={question}
                    onChange={(event) => setQuestion(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" && !event.shiftKey) {
                        event.preventDefault()
                        void ask()
                      }
                    }}
                    placeholder="Ask a question about your documents…"
                    aria-label="Question"
                    className="max-h-40 min-h-11 flex-1 resize-y bg-transparent px-3 py-3 text-sm outline-none placeholder:text-slate-400"
                  />
                  <button
                    type="submit"
                    disabled={!question.trim() || messages.some((message) => message.pending)}
                    aria-label="Send question"
                    className="mb-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-600 text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                  >
                    <ArrowUp size={19} />
                  </button>
                </div>
                <p className="mt-2 text-center text-xs text-slate-400">
                  AI responses can be inaccurate. Review the cited source documents.
                </p>
              </form>
            </div>
          </>
        )}
      </main>
    </div>
  )
}

export default Index

function toMessages(detail: ConversationDetail): Message[] {
  const result: Message[] = []
  for (const item of detail.messages) {
    if (item.role === "USER") {
      result.push({ id: item.id, userMessageId: item.id, question: item.content })
      continue
    }
    const previous = result[result.length - 1]
    if (!previous) continue
    previous.result = {
      answer: item.content,
      inScope: true,
      category: "conversation",
      sourceChunkIds: item.sources.map((source) => source.chunkId),
      sourceDocumentIds: [...new Set(item.sources.map((source) => source.documentId))],
      sources: item.sources,
    }
  }
  const unanswered = result[result.length - 1]
  if (unanswered && !unanswered.result) {
    unanswered.error = "The previous response did not complete."
  }
  return result
}

function SourceCards({ sources }: { sources: SourceCitation[] }) {
  if (!sources.length) return null
  return <div className="mt-4 border-t border-slate-100 pt-3">
    <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Sources</p>
    <div className="grid gap-2">
      {sources.map((source, index) => <div key={source.chunkId} className="rounded-xl border border-slate-200 bg-slate-50 p-3">
        <div className="flex items-center justify-between gap-3">
          <span className="truncate text-xs font-semibold text-slate-700">[{index + 1}] {source.documentName}</span>
          <span className="shrink-0 text-[11px] text-slate-400">{Math.round(source.score * 100)}% match</span>
        </div>
        <p className="mt-1 line-clamp-3 text-xs leading-5 text-slate-600">{source.excerpt}</p>
        <p className="mt-1 text-[10px] text-slate-400">
          Chunk {source.chunkId.slice(0, 8)}
          {source.page !== null ? ` · page ${source.page}` : ""}
          {source.section ? ` · ${source.section}` : ""}
        </p>
      </div>)}
    </div>
  </div>
}
