import logo from "../../assets/image.png"
import {
  FileText,
  LogOut,
  MessageSquare,
  Search,
  ShieldCheck,
  Upload,
} from "lucide-react"
import type { CurrentUser } from "../../api"

export type WorkspaceView = "chat" | "documents" | "users"

interface SideBarProps {
  user: CurrentUser
  view: WorkspaceView
  recentQuestions: string[]
  onNavigate: (view: WorkspaceView) => void
  onSelectQuestion: (question: string) => void
  onLogout: () => void
  loggingOut: boolean
}

const SideBar = ({
  user,
  view,
  recentQuestions,
  onNavigate,
  onSelectQuestion,
  onLogout,
  loggingOut,
}: SideBarProps) => {
  const canManageUsers = user.role === "ADMIN" || user.role === "CUSTODIAN"

  const navItems: { id: WorkspaceView; label: string; icon: typeof Search }[] = [
    { id: "chat", label: "Ask your knowledge base", icon: Search },
    { id: "documents", label: "Documents", icon: FileText },
  ]
  if (canManageUsers) navItems.push({ id: "users", label: "Access management", icon: ShieldCheck })

  return (
    <aside className="flex h-full w-64 shrink-0 flex-col border-r border-slate-200 bg-slate-50 text-slate-900">
      <div className="flex items-center gap-3 border-b border-slate-200 px-5 py-5">
        <img src={logo} alt="" className="h-9 w-9 object-contain" />
        <div>
          <p className="font-semibold tracking-wide">EDIFY</p>
          <p className="text-xs text-slate-500">Knowledge workspace</p>
        </div>
      </div>

      <nav className="space-y-1 px-3 py-5" aria-label="Workspace">
        {navItems.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            onClick={() => onNavigate(id)}
            aria-current={view === id ? "page" : undefined}
            className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-sm transition ${
              view === id
                ? "bg-slate-900 font-medium text-white shadow-sm"
                : "text-slate-600 hover:bg-slate-200 hover:text-slate-950"
            }`}
          >
            {id === "documents" ? <Upload size={18} /> : <Icon size={18} />}
            {label}
          </button>
        ))}
      </nav>

      <div className="min-h-0 flex-1 overflow-y-auto px-3">
        <div className="mb-3 flex items-center gap-2 px-3 text-xs font-semibold uppercase tracking-wider text-slate-400">
          <MessageSquare size={14} />
          Recent questions
        </div>
        {recentQuestions.length ? (
          <div className="space-y-1">
            {recentQuestions.map((question, index) => (
              <button
                key={`${question}-${index}`}
                type="button"
                onClick={() => onSelectQuestion(question)}
                title={question}
                className="w-full truncate rounded-lg px-3 py-2 text-left text-sm text-slate-600 hover:bg-slate-200"
              >
                {question}
              </button>
            ))}
          </div>
        ) : (
          <p className="px-3 text-sm text-slate-400">Your recent questions will appear here.</p>
        )}
      </div>

      <div className="border-t border-slate-200 p-3">
        <div className="flex items-center gap-3 rounded-xl px-2 py-2">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-indigo-100 font-semibold text-indigo-700">
            {user.email.charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{user.email}</p>
            <p className="text-xs text-slate-500">{user.role}</p>
          </div>
          <button
            type="button"
            onClick={onLogout}
            disabled={loggingOut}
            title="Sign out"
            aria-label="Sign out"
            className="rounded-lg p-2 text-slate-500 hover:bg-slate-200 hover:text-slate-900 disabled:opacity-50"
          >
            <LogOut size={17} />
          </button>
        </div>
      </div>
    </aside>
  )
}

export default SideBar
