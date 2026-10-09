import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode } from "react"
import { X } from "lucide-react"

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger"

export function Button({ variant = "primary", className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
}) {
  const variants: Record<ButtonVariant, string> = {
    primary: "bg-slate-900 text-white hover:bg-slate-800 disabled:bg-slate-300",
    secondary: "border border-slate-300 bg-white text-slate-700 hover:bg-slate-50",
    ghost: "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
    danger: "bg-rose-600 text-white hover:bg-rose-700 disabled:bg-slate-300",
  }
  return <button {...props} className={`inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-60 ${variants[variant]} ${className}`} />
}

export function Input({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none placeholder:text-slate-400 focus:border-indigo-400 ${className}`} />
}

export function Card({ className = "", ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div {...props} className={`rounded-2xl border border-slate-200 bg-white ${className}`} />
}

export function Badge({ tone = "neutral", className = "", children }: {
  tone?: "neutral" | "success" | "warning" | "danger" | "info"
  className?: string
  children: ReactNode
}) {
  const tones = {
    neutral: "bg-slate-100 text-slate-600",
    success: "bg-emerald-50 text-emerald-700",
    warning: "bg-amber-50 text-amber-700",
    danger: "bg-rose-50 text-rose-700",
    info: "bg-indigo-50 text-indigo-700",
  }
  return <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${tones[tone]} ${className}`}>{children}</span>
}

export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden="true" className={`animate-pulse rounded-lg bg-slate-100 ${className}`} />
}

export function EmptyState({ title, description, action }: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return <div className="rounded-2xl border border-dashed border-slate-300 px-5 py-12 text-center">
    <p className="font-medium text-slate-700">{title}</p>
    {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
    {action && <div className="mt-4">{action}</div>}
  </div>
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
    <span>{message}</span>
    {onRetry && <Button type="button" variant="secondary" onClick={onRetry}>Try again</Button>}
  </div>
}

export function Toast({ message, tone = "success", onDismiss }: {
  message: string
  tone?: "success" | "error"
  onDismiss?: () => void
}) {
  return <div role={tone === "error" ? "alert" : "status"}
    className={`flex items-center justify-between gap-3 rounded-xl border px-4 py-3 text-sm ${
      tone === "error" ? "border-rose-200 bg-rose-50 text-rose-800" : "border-emerald-200 bg-emerald-50 text-emerald-800"
    }`}>
    <span>{message}</span>
    {onDismiss && <button type="button" aria-label="Dismiss" onClick={onDismiss} className="rounded-md p-1 hover:bg-black/5"><X size={15} /></button>}
  </div>
}

export function Modal({ open, title, onClose, children }: {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
}) {
  if (!open) return null
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4" onMouseDown={(event) => {
    if (event.target === event.currentTarget) onClose()
  }}>
    <section role="dialog" aria-modal="true" aria-label={title} className="max-h-[90dvh] w-full max-w-xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl md:p-6">
      <div className="mb-4 flex items-center justify-between gap-4">
        <h2 className="font-semibold text-slate-900">{title}</h2>
        <button type="button" onClick={onClose} aria-label="Close dialog" className="rounded-lg p-2 text-slate-500 hover:bg-slate-100"><X size={17} /></button>
      </div>
      {children}
    </section>
  </div>
}

export function Dialog(props: Parameters<typeof Modal>[0]) {
  return <Modal {...props} />
}

export function Tabs({ value, onChange, items, label }: {
  value: string
  onChange: (value: string) => void
  items: { value: string; label: string }[]
  label: string
}) {
  return <div role="tablist" aria-label={label} className="flex gap-1 border-b border-slate-200">
    {items.map((item) => <button key={item.value} type="button" role="tab" aria-selected={value === item.value}
      onClick={() => onChange(item.value)}
      className={`border-b-2 px-3 py-2 text-sm ${value === item.value ? "border-indigo-600 font-medium text-indigo-700" : "border-transparent text-slate-500 hover:text-slate-800"}`}>
      {item.label}
    </button>)}
  </div>
}

export function Table({ children, className = "", ...props }: HTMLAttributes<HTMLTableElement>) {
  return <div className="overflow-x-auto rounded-xl border border-slate-200">
    <table {...props} className={`w-full border-collapse text-left text-sm ${className}`}>{children}</table>
  </div>
}
