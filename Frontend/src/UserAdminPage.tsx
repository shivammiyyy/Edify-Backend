import { useCallback, useEffect, useState } from "react"
import { LoaderCircle, ShieldCheck, Users } from "lucide-react"
import { api, type CurrentUser, type Role, type UserItem } from "./api"

interface UserAdminPageProps {
  currentUser: CurrentUser
}

const roles: Role[] = ["READ", "WRITE", "CUSTODIAN", "ADMIN"]

const UserAdminPage = ({ currentUser }: UserAdminPageProps) => {
  const [users, setUsers] = useState<UserItem[]>([])
  const [loading, setLoading] = useState(true)
  const [updatingId, setUpdatingId] = useState("")
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")
  const isAdmin = currentUser.role === "ADMIN"

  const loadUsers = useCallback(async () => {
    setLoading(true)
    setError("")
    try {
      setUsers(await api.users())
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to load users.")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let active = true
    api.users().then((items) => {
      if (active) setUsers(items)
    }).catch((cause: unknown) => {
      if (active) setError(cause instanceof Error ? cause.message : "Unable to load users.")
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [])

  const changeRole = async (user: UserItem, role: Role) => {
    if (role === user.role) return
    setUpdatingId(user.id)
    setError("")
    setNotice("")
    try {
      const updated = await api.setRole(user.id, role)
      setUsers((current) => current.map((item) => item.id === updated.id ? updated : item))
      setNotice(`Access updated for ${updated.email}.`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to update user access.")
      await loadUsers()
    } finally {
      setUpdatingId("")
    }
  }

  return (
    <section className="min-h-0 flex-1 overflow-y-auto">
      <header className="border-b border-slate-100 px-6 py-6 md:px-10">
        <h1 className="text-2xl font-semibold tracking-tight">Access management</h1>
        <p className="mt-1 text-sm text-slate-500">
          {isAdmin ? "Manage workspace roles and permissions." : "Grant or revoke WRITE access for users."}
        </p>
      </header>
      <div className="mx-auto max-w-5xl px-5 py-7 md:px-10">
        {error && <p role="alert" className="mb-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{error}</p>}
        {notice && <p role="status" className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</p>}
        <div className="mb-4 flex items-center gap-2 text-sm text-slate-500"><Users size={17} />Registered users</div>
        {loading ? (
          <div className="flex items-center gap-2 py-10 text-sm text-slate-500"><LoaderCircle size={18} className="animate-spin" /> Loading users…</div>
        ) : (
          <div className="overflow-hidden rounded-2xl border border-slate-200">
            {users.map((user, index) => {
              const protectedSystemAdmin = user.systemAdmin
              const canChange = isAdmin
                ? !protectedSystemAdmin
                : user.role === "READ" || user.role === "WRITE"
              const choices = isAdmin
                ? roles
                : user.role === "CUSTODIAN" || user.role === "ADMIN"
                  ? [user.role]
                  : roles.slice(0, 2)

              return (
                <article key={user.id} className={`flex flex-wrap items-center gap-4 px-4 py-4 md:px-5 ${index ? "border-t border-slate-100" : ""}`}>
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-indigo-50 font-semibold text-indigo-700">
                    {(user.displayName || user.email).charAt(0).toUpperCase()}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{user.displayName || user.email}</p>
                    <p className="truncate text-xs text-slate-500">{user.email}</p>
                  </div>
                  {protectedSystemAdmin ? (
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-violet-50 px-3 py-1.5 text-xs font-semibold text-violet-700">
                      <ShieldCheck size={14} />System admin
                    </span>
                  ) : (
                    <label className="flex items-center gap-2 text-xs text-slate-500">
                      Role
                      <select value={user.role} disabled={!canChange || updatingId === user.id}
                        onChange={(event) => void changeRole(user, event.target.value as Role)}
                        aria-label={`Role for ${user.email}`}
                        className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-800 outline-none focus:border-indigo-400 disabled:bg-slate-100 disabled:text-slate-500">
                        {choices.map((role) => <option key={role} value={role}>{role}</option>)}
                      </select>
                      {updatingId === user.id && <LoaderCircle size={15} className="animate-spin" />}
                    </label>
                  )}
                </article>
              )
            })}
            {users.length === 0 && <p className="px-5 py-10 text-center text-sm text-slate-500">No users found.</p>}
          </div>
        )}
        <p className="mt-4 text-xs leading-5 text-slate-400">
          New sign-ins start with READ access. CUSTODIAN can grant WRITE to standard users;
          only ADMIN can assign CUSTODIAN or ADMIN roles. The system admin cannot be changed here.
        </p>
      </div>
    </section>
  )
}

export default UserAdminPage
