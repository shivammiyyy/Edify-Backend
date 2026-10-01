import { useEffect, useRef, useState, type FormEvent } from "react"
import { ArrowUp, BookOpen, LoaderCircle, Sparkles } from "lucide-react"
import { api, type CurrentUser, type RagAnswer } from "../api"
import DocumentPage from "../DocumentPage/Index"
import UserAdminPage from "../UserAdminPage"
import SideBar, { type WorkspaceView } from "./components/SideBar"

interface Message {
  id: number
  question: string
  result?: RagAnswer
  pending?: boolean
  error?: string
}

interface WorkspaceProps {
  user: CurrentUser
  onLogout: () => void
  loggingOut: boolean
}

const Index = ({ user, onLogout, loggingOut }: WorkspaceProps) => {
  const [view, setView] = useState<WorkspaceView>("chat")
  const [question, setQuestion] = useState("")
  const [messages, setMessages] = useState<Message[]>([])
  const [requestError, setRequestError] = useState("")
  const scrollRef = useRef<HTMLDivElement>(null)
  const nextId = useRef(0)

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" })
  }, [messages])

  const ask = async (event?: FormEvent) => {
    event?.preventDefault()
    const text = question.trim()
    if (!text || messages.some((message) => message.pending)) return

    const id = nextId.current++
    setQuestion("")
    setRequestError("")
    setMessages((current) => [...current, { id, question: text, pending: true }])
    try {
      const result = await api.query(text)
      setMessages((current) => current.map((message) =>
        message.id === id ? { ...message, result, pending: false } : message,
      ))
    } catch (error) {
      setMessages((current) => current.map((message) =>
        message.id === id
          ? { ...message, pending: false, error: error instanceof Error ? error.message : "The query failed." }
          : message,
      ))
    }
  }

  const recentQuestions = [...messages].reverse().map((message) => message.question).slice(0, 8)

  return (
    <div className="flex h-dvh min-h-[560px] overflow-hidden bg-white text-slate-900">
      <SideBar
        user={user}
        view={view}
        recentQuestions={recentQuestions}
        onNavigate={setView}
        onSelectQuestion={(value) => { setView("chat"); setQuestion(value) }}
        onLogout={onLogout}
        loggingOut={loggingOut}
      />

      <main className="flex min-w-0 flex-1 flex-col bg-white">
        {view === "documents" ? (
          <DocumentPage user={user} />
        ) : view === "users" ? (
          <UserAdminPage currentUser={user} />
        ) : (
          <>
            <header className="flex items-center justify-between border-b border-slate-100 px-6 py-4 md:px-10">
              <div>
                <p className="text-sm font-semibold">Edify Assistant</p>
                <p className="text-xs text-slate-500">Answers grounded in your accessible documents</p>
              </div>
              <div className="hidden items-center gap-2 rounded-full bg-indigo-50 px-3 py-1.5 text-xs font-medium text-indigo-700 sm:flex">
                <Sparkles size={14} />
                Private knowledge search
              </div>
            </header>

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
                        <div className="flex items-center gap-2 text-slate-500" role="status">
                          <LoaderCircle size={17} className="animate-spin" />
                          Searching your documents and preparing an answer…
                        </div>
                      ) : message.error ? (
                        <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-800">
                          {message.error}
                        </div>
                      ) : message.result ? (
                        <>
                          <p className="whitespace-pre-wrap">{message.result.answer}</p>
                          {message.result.sourceDocumentIds.length > 0 && (
                            <div className="mt-4 border-t border-slate-100 pt-3">
                              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
                                Sources
                              </p>
                              <div className="flex flex-wrap gap-2">
                                {message.result.sourceDocumentIds.map((id) => (
                                  <span key={id} className="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-600">
                                    Document {id.slice(0, 8)}
                                  </span>
                                ))}
                              </div>
                            </div>
                          )}
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
