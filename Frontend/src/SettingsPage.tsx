import { Check, Monitor, Moon, ShieldCheck, Sun, UserRound } from "lucide-react"
import type { CurrentUser } from "./api"

export type ThemePreference = "light" | "dark" | "system"

interface SettingsPageProps {
  user: CurrentUser
  theme: ThemePreference
  onThemeChange: (theme: ThemePreference) => void
}

const SettingsPage = ({ user, theme, onThemeChange }: SettingsPageProps) => {
  const options: { id: ThemePreference; label: string; icon: typeof Sun; description: string }[] = [
    { id: "light", label: "Light", icon: Sun, description: "A bright, clear workspace" },
    { id: "dark", label: "Dark", icon: Moon, description: "Comfortable in low light" },
    { id: "system", label: "System", icon: Monitor, description: "Follow your device setting" },
  ]

  return (
    <section className="min-h-0 flex-1 overflow-y-auto bg-slate-50">
      <div className="mx-auto max-w-4xl space-y-6 px-5 py-8 md:px-10 md:py-10">
        <div>
          <p className="text-sm font-medium text-indigo-600">Workspace preferences</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">Settings</h1>
          <p className="mt-2 text-sm text-slate-500">Manage your profile, appearance, and assistant preferences.</p>
        </div>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 md:p-6">
          <div className="flex items-start gap-3">
            <span className="rounded-lg bg-slate-100 p-2 text-slate-600"><UserRound size={18} /></span>
            <div>
              <h2 className="font-semibold text-slate-900">Profile</h2>
              <p className="mt-1 text-sm text-slate-500">Your signed-in workspace identity.</p>
            </div>
          </div>
          <dl className="mt-5 grid gap-4 border-t border-slate-100 pt-4 sm:grid-cols-2">
            <div><dt className="text-xs text-slate-500">Email</dt><dd className="mt-1 text-sm font-medium text-slate-800">{user.email}</dd></div>
            <div><dt className="text-xs text-slate-500">Workspace role</dt><dd className="mt-1 text-sm font-medium text-slate-800">{user.role}</dd></div>
          </dl>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 md:p-6">
          <div className="flex items-start gap-3">
            <span className="rounded-lg bg-slate-100 p-2 text-slate-600"><Sun size={18} /></span>
            <div>
              <h2 className="font-semibold text-slate-900">Appearance</h2>
              <p className="mt-1 text-sm text-slate-500">Choose how Edify looks on this device.</p>
            </div>
          </div>
          <div className="mt-5 grid gap-3 sm:grid-cols-3">
            {options.map(({ id, label, icon: Icon, description }) => (
              <button key={id} type="button" onClick={() => onThemeChange(id)}
                aria-pressed={theme === id}
                className={`rounded-xl border p-4 text-left transition ${
                  theme === id ? "border-indigo-500 bg-indigo-50/50 ring-2 ring-indigo-100" : "border-slate-200 hover:bg-slate-50"
                }`}>
                <span className="flex items-center justify-between">
                  <Icon size={17} className="text-slate-600" />
                  {theme === id && <Check size={16} className="text-indigo-600" />}
                </span>
                <span className="mt-3 block text-sm font-semibold text-slate-800">{label}</span>
                <span className="mt-1 block text-xs text-slate-500">{description}</span>
              </button>
            ))}
          </div>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 md:p-6">
          <div className="flex items-start gap-3">
            <span className="rounded-lg bg-slate-100 p-2 text-slate-600"><ShieldCheck size={18} /></span>
            <div>
              <h2 className="font-semibold text-slate-900">Security</h2>
              <p className="mt-1 text-sm text-slate-500">Your account uses Google sign-in and a protected server session.</p>
            </div>
          </div>
          <p className="mt-4 border-t border-slate-100 pt-4 text-sm leading-6 text-slate-600">
            Document and folder access is enforced using your workspace role and the permissions
            inherited from each document's folder.
          </p>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 md:p-6">
          <div>
            <h2 className="font-semibold text-slate-900">AI preferences</h2>
            <p className="mt-1 text-sm text-slate-500">Answers are grounded in authorized documents and use the workspace-configured model.</p>
          </div>
          <p className="mt-4 border-t border-slate-100 pt-4 text-sm leading-6 text-slate-600">
            Source citations are always included so you can verify generated answers against their documents.
            The AI model is configured by your workspace administrator.
          </p>
        </section>
      </div>
    </section>
  )
}

export default SettingsPage
