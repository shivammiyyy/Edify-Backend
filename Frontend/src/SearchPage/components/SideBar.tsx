import logo from "../../assets/image.png"
import {
  FileText,
  LayoutDashboard,
  LogOut,
  MessageSquare,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Search,
  ShieldCheck,
  Settings2,
  X,
} from "lucide-react"
import type { ConversationSummary, CurrentUser } from "../../api"

export type WorkspaceView = "dashboard" | "chat" | "documents" | "users" | "settings"

interface SideBarProps {
  user: CurrentUser
  view: WorkspaceView
  conversations: ConversationSummary[]
  activeConversationId: string | null
  collapsed: boolean
  mobileOpen: boolean
  onNavigate: (view: WorkspaceView) => void
  onToggleCollapsed: () => void
  onCloseMobile: () => void
  onNewConversation: () => void
  onSelectConversation: (id: string) => void
  onLogout: () => void
  loggingOut: boolean
}

const SideBar = ({
  user,
  view,
  conversations,
  activeConversationId,
  collapsed,
  mobileOpen,
  onNavigate,
  onToggleCollapsed,
  onCloseMobile,
  onNewConversation,
  onSelectConversation,
  onLogout,
  loggingOut,
}: SideBarProps) => {
  const canManageUsers = user.role === "ADMIN" || user.role === "CUSTODIAN"
  const compact = collapsed && !mobileOpen

  const navItems: { id: WorkspaceView; label: string; icon: typeof Search }[] = [
    { id: "dashboard", label: "Overview", icon: LayoutDashboard },
    { id: "chat", label: "AI assistant", icon: Search },
    { id: "documents", label: "Knowledge", icon: FileText },
  ]
  if (canManageUsers) navItems.push({ id: "users", label: "Access management", icon: ShieldCheck })

  return (
    <>
      {mobileOpen && <button type="button" aria-label="Close navigation" onClick={onCloseMobile}
        className="fixed inset-0 z-40 bg-slate-950/40 md:hidden" />}
      <aside className={`fixed inset-y-0 left-0 z-50 flex h-dvh shrink-0 flex-col border-r border-slate-200 bg-slate-50 text-slate-900 transition-all md:static md:z-auto md:h-full md:translate-x-0 ${
        mobileOpen ? "translate-x-0" : "-translate-x-full"
      } ${collapsed ? "w-64 md:w-[4.5rem]" : "w-64"} ${mobileOpen ? "md:translate-x-0" : ""}`}>
      <div className={`flex items-center border-b border-slate-200 py-5 ${compact ? "justify-center px-2" : "gap-3 px-5"}`}>
        <img src={logo} alt="Edify" className="h-9 w-9 shrink-0 object-contain" />
        {!compact && <div className="min-w-0 flex-1">
          <p className="font-semibold tracking-wide">EDIFY</p>
          <p className="text-xs text-slate-500">Knowledge workspace</p>
        </div>}
        <button type="button" onClick={onCloseMobile} aria-label="Close navigation"
          className="rounded-lg p-2 text-slate-500 hover:bg-slate-200 md:hidden">
          <X size={17} />
        </button>
        {!compact && <button type="button" onClick={onToggleCollapsed} aria-label="Collapse sidebar"
          className="rounded-lg p-2 text-slate-500 hover:bg-slate-200"><PanelLeftClose size={16} /></button>}
        {compact && <button type="button" onClick={onToggleCollapsed} aria-label="Expand sidebar"
          className="rounded-lg p-2 text-slate-500 hover:bg-slate-200"><PanelLeftOpen size={16} /></button>}
        </div>

      <nav className="space-y-1 px-3 py-5" aria-label="Workspace">
        {navItems.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            onClick={() => { onNavigate(id); onCloseMobile() }}
            aria-current={view === id ? "page" : undefined}
            title={compact ? label : undefined}
            className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-sm transition ${compact ? "justify-center" : ""} ${
              view === id
                ? "bg-slate-900 font-medium text-white shadow-sm"
                : "text-slate-600 hover:bg-slate-200 hover:text-slate-950"
            }`}
          >
            <Icon size={18} />
            {!compact && label}
          </button>
        ))}
        <button type="button" onClick={() => { onNavigate("settings"); onCloseMobile() }}
          aria-current={view === "settings" ? "page" : undefined}
          title={compact ? "Settings" : undefined}
          className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-sm transition ${compact ? "justify-center" : ""} ${
            view === "settings" ? "bg-slate-900 font-medium text-white shadow-sm" : "text-slate-600 hover:bg-slate-200 hover:text-slate-950"
          }`}>
          <Settings2 size={18} />{!compact && "Settings"}
        </button>
      </nav>

      <div className="min-h-0 flex-1 overflow-y-auto px-3">
        <button
          type="button"
          onClick={() => { onNewConversation(); onCloseMobile() }}
          title={compact ? "New conversation" : undefined}
          className={`mb-5 flex w-full items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-100 ${compact ? "md:px-2" : ""}`}
        >
          <Plus size={16} />
          {!compact && "New conversation"}
        </button>
        {!compact && <div className="mb-3 flex items-center gap-2 px-3 text-xs font-semibold uppercase tracking-wider text-slate-400">
          <MessageSquare size={14} />
          Conversations
        </div>}
        {conversations.length ? (
          <div className="space-y-1">
            {conversations.slice(0, compact ? 8 : conversations.length).map((conversation) => (
              <button
                key={conversation.id}
                type="button"
                onClick={() => { onSelectConversation(conversation.id); onCloseMobile() }}
                title={conversation.title}
                aria-current={activeConversationId === conversation.id ? "true" : undefined}
                className={`w-full truncate rounded-lg px-3 py-2 text-left text-sm ${compact ? "md:flex md:justify-center" : ""} ${
                  activeConversationId === conversation.id
                    ? "bg-slate-200 font-medium text-slate-900"
                    : "text-slate-600 hover:bg-slate-200"
                }`}
              >
                {compact ? <MessageSquare size={16} /> : conversation.title}
              </button>
            ))}
          </div>
        ) : !compact && (
          <p className="px-3 text-sm text-slate-400">Your conversations will appear here.</p>
        )}
      </div>

      <div className="border-t border-slate-200 p-3">
        <div className={`flex items-center gap-3 rounded-xl px-2 py-2 ${compact ? "justify-center" : ""}`}>
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-indigo-100 font-semibold text-indigo-700">
            {user.email.charAt(0).toUpperCase()}
          </div>
          {!compact && <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{user.email}</p>
            <p className="text-xs text-slate-500">{user.role}</p>
          </div>}
          {!compact && <button
            type="button"
            onClick={onLogout}
            disabled={loggingOut}
            title="Sign out"
            aria-label="Sign out"
            className="rounded-lg p-2 text-slate-500 hover:bg-slate-200 hover:text-slate-900 disabled:opacity-50"
          >
            <LogOut size={17} />
          </button>}
          <button
            type="button"
            onClick={onLogout}
            disabled={loggingOut}
            title="Sign out"
            aria-label="Sign out"
            className={`rounded-lg p-2 text-slate-500 hover:bg-slate-200 hover:text-slate-900 disabled:opacity-50 ${compact ? "" : "hidden"}`}
          >
            <LogOut size={17} />
          </button>
        </div>
      </div>
    </aside>
    </>
  )
}

export default SideBar
