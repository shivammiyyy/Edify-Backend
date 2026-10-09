import { Fragment, type ReactNode } from "react"

interface MarkdownAnswerProps {
  content: string
}

const MarkdownAnswer = ({ content }: MarkdownAnswerProps) => {
  const lines = content.replace(/\r\n?/g, "\n").split("\n")
  const blocks: ReactNode[] = []
  let paragraph: string[] = []
  let code: string[] = []
  let list: { ordered: boolean; items: string[] } | null = null
  let inCode = false
  let codeLanguage = ""

  const flushParagraph = () => {
    if (!paragraph.length) return
    blocks.push(<p key={`p-${blocks.length}`} className="my-2 leading-7">{inline(paragraph.join(" "))}</p>)
    paragraph = []
  }
  const flushList = () => {
    if (!list) return
    const Tag = list.ordered ? "ol" : "ul"
    blocks.push(<Tag key={`list-${blocks.length}`} className={`my-3 space-y-1 pl-6 ${list.ordered ? "list-decimal" : "list-disc"}`}>
      {list.items.map((item, index) => <li key={index} className="pl-1">{inline(item)}</li>)}
    </Tag>)
    list = null
  }

  lines.forEach((line, index) => {
    const fence = line.match(/^```(\S*)\s*$/)
    if (fence) {
      flushParagraph()
      flushList()
      if (inCode) {
        blocks.push(<pre key={`code-${blocks.length}`} className="my-3 overflow-x-auto rounded-xl bg-slate-950 p-4 text-xs leading-6 text-slate-100">
          <code data-language={codeLanguage || undefined}>{code.join("\n")}</code>
        </pre>)
        code = []
        inCode = false
        codeLanguage = ""
      } else {
        inCode = true
        codeLanguage = fence[1]
      }
      return
    }
    if (inCode) {
      code.push(line)
      return
    }

    if (!line.trim()) {
      flushParagraph()
      flushList()
      return
    }

    const heading = line.match(/^(#{1,6})\s+(.+)$/)
    if (heading) {
      flushParagraph()
      flushList()
      const level = Math.min(heading[1].length, 4)
      const className = level <= 2
        ? "mb-2 mt-5 text-lg font-semibold tracking-tight"
        : "mb-2 mt-4 text-base font-semibold"
      blocks.push(<h3 key={`h-${index}`} className={className}>{inline(heading[2])}</h3>)
      return
    }

    const quote = line.match(/^>\s?(.*)$/)
    if (quote) {
      flushParagraph()
      flushList()
      blocks.push(<blockquote key={`q-${index}`} className="my-3 border-l-2 border-indigo-300 pl-4 text-slate-600 dark:text-slate-300">
        {inline(quote[1])}
      </blockquote>)
      return
    }

    const item = line.match(/^\s*(?:(\d+)\.|[-*+])\s+(.+)$/)
    if (item) {
      flushParagraph()
      const ordered = Boolean(item[1])
      if (list && list.ordered !== ordered) flushList()
      if (!list) list = { ordered, items: [] }
      list.items.push(item[2])
      return
    }

    flushList()
    paragraph.push(line.trim())
  })

  if (inCode) {
    blocks.push(<pre key={`code-${blocks.length}`} className="my-3 overflow-x-auto rounded-xl bg-slate-950 p-4 text-xs leading-6 text-slate-100">
      <code data-language={codeLanguage || undefined}>{code.join("\n")}</code>
    </pre>)
  }
  flushParagraph()
  flushList()

  return <div className="markdown-answer break-words">{blocks.map((block, index) => <Fragment key={index}>{block}</Fragment>)}</div>
}

const inlinePattern = /(`[^`]+`|\*\*[^*]+\*\*|__[^_]+__|\*[^*]+\*|_[^_]+_|\[[^\]]+\]\([^)]+\))/g

function inline(text: string): ReactNode[] {
  const parts = text.split(inlinePattern)
  return parts.map((part, index) => {
    if (part.startsWith("`") && part.endsWith("`")) {
      return <code key={index} className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[0.9em] text-indigo-700 dark:bg-slate-800 dark:text-indigo-300">{part.slice(1, -1)}</code>
    }
    if ((part.startsWith("**") && part.endsWith("**")) || (part.startsWith("__") && part.endsWith("__"))) {
      return <strong key={index} className="font-semibold">{part.slice(2, -2)}</strong>
    }
    if ((part.startsWith("*") && part.endsWith("*")) || (part.startsWith("_") && part.endsWith("_"))) {
      return <em key={index}>{part.slice(1, -1)}</em>
    }
    const link = part.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
    if (link) {
      const safeHref = safeUrl(link[2])
      if (safeHref) return <a key={index} href={safeHref} target="_blank" rel="noreferrer" className="font-medium text-indigo-600 underline underline-offset-2 dark:text-indigo-400">{link[1]}</a>
      return <span key={index}>{link[1]}</span>
    }
    return <Fragment key={index}>{part}</Fragment>
  })
}

function safeUrl(value: string): string | null {
  try {
    const url = new URL(value)
    return url.protocol === "https:" || url.protocol === "http:" ? url.href : null
  } catch {
    return null
  }
}

export default MarkdownAnswer
