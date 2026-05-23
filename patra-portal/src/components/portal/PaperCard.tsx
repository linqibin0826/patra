import { Bookmark, BookOpen, MessageSquare } from "lucide-react";
import { AISummaryBadge } from "@/components/portal/AISummaryBadge";
import type { Paper } from "@/types/portal";

interface PaperCardProps {
  paper: Paper;
}

export function PaperCard({ paper }: PaperCardProps) {
  const visibleAuthors = paper.authors.slice(0, 3);
  const remaining = paper.authorCount - visibleAuthors.length;

  return (
    <article className="flex flex-col gap-3 border-b border-border-subtle py-5">
      <div className="flex items-baseline gap-3 font-mono text-2xs uppercase tracking-caps text-fg-3">
        <span className="text-ink-900">{paper.journal}</span>
        <span>·</span>
        <span>{paper.year}</span>
        <span>·</span>
        <span>{paper.kind}</span>
        {paper.minutesAgo !== undefined && (
          <>
            <span>·</span>
            <span>{paper.minutesAgo} 分钟前</span>
          </>
        )}
      </div>
      <h3 className="font-serif text-lg font-medium leading-snug tracking-tight text-ink-900">
        {paper.title}
      </h3>
      <p className="text-sm leading-snug text-fg-2">
        {visibleAuthors.join("、")}
        {remaining > 0 && ` 等 ${paper.authorCount} 位作者`}
      </p>
      <AISummaryBadge summary={paper.ai} />
      <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-fg-3">
        <span data-doi className="font-mono">
          DOI {paper.doi}
        </span>
        <span data-pmid className="font-mono">
          PMID {paper.pmid}
        </span>
        <span>来源 {paper.source}</span>
        <span>阅读 {paper.readMin} 分钟</span>
      </div>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-fg-2">
        <button
          type="button"
          disabled
          aria-disabled="true"
          title="功能即将上线"
          className="inline-flex items-center gap-1.5 text-fg-2 hover:text-ink-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <BookOpen className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden /> 详情
        </button>
        <button
          type="button"
          disabled
          aria-disabled="true"
          title="功能即将上线"
          className="inline-flex items-center gap-1.5 text-fg-2 hover:text-ink-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Bookmark className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden /> 收藏 {paper.bookmarks}
        </button>
        <button
          type="button"
          disabled
          aria-disabled="true"
          title="功能即将上线"
          className="inline-flex items-center gap-1.5 text-fg-2 hover:text-ink-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <MessageSquare className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden /> 引用 {paper.cites}
        </button>
      </div>
    </article>
  );
}
