import { ArrowLeftRight, Bookmark, ChevronRight, MessageSquare, Share2 } from "lucide-react";
import { AISummaryBadge } from "@/components/portal/AISummaryBadge";
import type { Paper, PaperSource } from "@/types/portal";

interface PaperCardProps {
  paper: Paper;
}

const SOURCE_DOT_CLASS: Record<PaperSource, string> = {
  PubMed: "bg-emerald-500",
  "Europe PMC": "bg-clay-500",
  Crossref: "bg-sky-500",
};

export function PaperCard({ paper }: PaperCardProps) {
  const visibleAuthors = paper.authors.slice(0, 2);
  const remaining = paper.authorCount - visibleAuthors.length;

  return (
    <article className="flex flex-col gap-3.5 rounded-lg border border-border-default bg-paper-50 p-5">
      <div className="flex items-center justify-between gap-3">
        <span className="inline-flex items-center gap-1.5 font-mono text-2xs font-medium text-fg-2">
          <span
            aria-hidden
            className={`inline-block h-1.5 w-1.5 rounded-full ${SOURCE_DOT_CLASS[paper.source]}`}
          />
          {paper.source}
        </span>
        <span data-doi className="font-mono text-2xs text-fg-3">
          {paper.doi}
        </span>
      </div>

      <h3 className="font-serif text-lg font-medium leading-snug tracking-tight text-ink-900">
        {paper.title}
      </h3>

      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-fg-3">
        <span className="font-serif italic text-ink-700">{paper.journal}</span>
        <span>·</span>
        <span>{paper.year}</span>
        <span>·</span>
        <span className="text-fg-2">
          {visibleAuthors.join(", ")}
          {remaining > 0 && ` 等 ${remaining} 位作者`}
        </span>
        <span className="ml-auto rounded-sm border border-border-subtle bg-paper-100 px-1.5 py-0.5 font-mono text-2xs uppercase tracking-caps text-fg-3">
          {paper.kind}
        </span>
      </div>

      <AISummaryBadge summary={paper.ai} readMin={paper.readMin} />

      <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-sm text-fg-2">
        <button
          type="button"
          disabled
          aria-disabled="true"
          title="功能即将上线"
          className="inline-flex items-center gap-1 text-fg-2 hover:text-ink-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          详情
          <ChevronRight className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden />
        </button>
        <button
          type="button"
          disabled
          aria-disabled="true"
          title="功能即将上线"
          className="inline-flex items-center gap-1.5 text-fg-2 hover:text-ink-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Bookmark className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden />
          收藏 {paper.bookmarks}
        </button>
        <button
          type="button"
          disabled
          aria-disabled="true"
          title="功能即将上线"
          className="inline-flex items-center gap-1.5 text-fg-2 hover:text-ink-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <MessageSquare className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden />
          评论
        </button>
        <button
          type="button"
          disabled
          aria-disabled="true"
          aria-label="分享"
          title="功能即将上线"
          className="inline-flex items-center gap-1.5 text-fg-2 hover:text-ink-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Share2 className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden />
        </button>
        <span className="ml-auto inline-flex items-center gap-1.5 font-mono text-xs text-fg-3">
          <ArrowLeftRight className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden />
          {paper.cites} 引用
        </span>
      </div>
    </article>
  );
}
