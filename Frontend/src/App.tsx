import { useCallback, useEffect, useState } from "react"
import { LoaderCircle, RefreshCw } from "lucide-react"
import { ApiError, api, signInUrl, type CurrentUser } from "./api"
import { Index as LandingPage } from "./LandingPage/Index"
import Workspace from "./SearchPage/Index"
import "./App.css"

function App() {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [loggingOut, setLoggingOut] = useState(false)

  const loadSession = useCallback(async () => {
    setLoading(true)
    setError("")
    try {
      setUser(await api.me())
    } catch (cause) {
      setUser(null)
      if (!(cause instanceof ApiError && cause.status === 401)) {
        setError(cause instanceof Error ? cause.message : "Could not connect to the service.")
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let active = true
    api.me().then((currentUser) => {
      if (active) setUser(currentUser)
    }).catch((cause: unknown) => {
      if (!active) return
      setUser(null)
      if (!(cause instanceof ApiError && cause.status === 401)) {
        setError(cause instanceof Error ? cause.message : "Could not connect to the service.")
      }
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [])

  const signIn = () => { window.location.assign(signInUrl) }

  const signOut = async () => {
    setLoggingOut(true)
    setError("")
    try {
      await api.logout()
      setUser(null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not sign out.")
    } finally {
      setLoggingOut(false)
    }
  }

  if (loading) {
    return (
      <main className="flex min-h-dvh items-center justify-center gap-3 bg-slate-50 text-sm text-slate-500">
        <LoaderCircle className="animate-spin" size={19} /> Connecting to your workspace…
      </main>
    )
  }

  if (user) {
    return (
      <>
        {error && <div role="alert" className="fixed right-4 top-4 z-[60] max-w-md rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800 shadow-lg">{error}</div>}
        <Workspace user={user} onLogout={() => void signOut()} loggingOut={loggingOut} />
      </>
    )
  }

  return (
    <>
      {error && (
        <div role="alert" className="fixed inset-x-4 top-4 z-[60] mx-auto flex max-w-2xl items-center justify-between gap-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800 shadow-lg">
          <span>{error}. Make sure the backend is running, then try again.</span>
          <button type="button" onClick={() => void loadSession()} className="inline-flex shrink-0 items-center gap-2 font-medium">
            <RefreshCw size={15} />Retry
          </button>
        </div>
      )}
      <LandingPage onSignIn={signIn} loading={false} />
    </>
  )
}

export default App
