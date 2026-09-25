import Link from "next/link";
import { Fragment, type ReactNode } from "react";
import { EvidenceBadge } from "@/components/portal/EvidenceBadge";
import { RichInlineText } from "@/components/portal/RichInlineText";
import { sourceDotClass } from "@/lib/portal-ui";
import type { Paper } from "@/types/portal";

interface PaperListItemProps {
  paper: Paper;
  /** 检索词：标题中的命中片段高亮 */
  highlight?: string;
}

/**
 * 文献检索页列表项（RSC）：PaperCard 的紧凑列表变体。
 * 来源行 → 衬线标题（两行截断，可高亮）→ 期刊 · 年份 · 作者 → 类型 / 证据徽章 → 摘要两行。
 * 不放收藏 / 评论 / 引用与被引数（本版边界 D，被引数据全 0）。
 */
export function PaperListItem({ paper, highlight }: PaperListItemProps) {
  const shownAuthors = paper.authors.slice(0, 2);
  const moreAuthors = paper.authors.length - shownAuthors.length;

  const meta: { key: string; node: ReactNode }[] = [];
  if (paper.journal) {
    meta.push({
      key: "journal",
      node: paper.venueId ? (
        <Link
          href={`/journals/${paper.venueId}`}
          className="max-w-full truncate font-serif italic text-ink-700 hover:text-clay-700"
        >
          {paper.journal}
        </Link>
      ) : (
        <span className="max-w-full truncate font-serif italic text-ink-700">{paper.journal}</span>
      ),
    });
  }
  if (paper.year !== null) {
    meta.push({ key: "year", node: <span>{paper.year}</span> });
  }
  if (shownAuthors.length > 0) {
    meta.push({
      key: "authors",
      node: (
        <span>
          {shownAuthors.join(", ")}
          {moreAuthors > 0 && ` 等 ${moreAuthors} 位作者`}
        </span>
      ),
    });
  }

  const showBadges = paper.kind !== null || paper.evidenceLevel.derived;

  return (
    <article className="flex flex-col gap-1.5 border-b border-border-subtle py-4">
      <div className="flex min-w-0 items-center gap-2.5 font-mono text-2xs text-fg-2">
        <span className="inline-flex shrink-0 items-center gap-1.5">
          <span
            aria-hidden
            className={`inline-block size-1.5 rounded-full ${sourceDotClass(paper.source)}`}
          />
          {paper.source}
        </span>
        {paper.doi && <span className="truncate text-fg-3">{paper.doi}</span>}
      </div>

      <h3 className="line-clamp-2 font-serif text-xl font-medium leading-snug tracking-tight text-ink-900">
        <Link href={`/papers/${paper.id}`} className="hover:text-clay-700">
          <RichInlineText text={paper.title} highlight={highlight} />
        </Link>
      </h3>

      {meta.length > 0 && (
        <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-xs text-fg-2">
          {meta.map((item, index) => (
            <Fragment key={item.key}>
              {index > 0 && <span aria-hidden="true">·</span>}
              {item.node}
            </Fragment>
          ))}
        </div>
      )}

      {showBadges && (
        <div className="flex flex-wrap items-center gap-2">
          {paper.kind && (
            <span className="rounded-sm border border-border-subtle bg-paper-100 px-1.5 py-0.5 font-mono text-2xs uppercase tracking-caps text-fg-3">
              {paper.kind}
            </span>
          )}
          <EvidenceBadge level={paper.evidenceLevel} compact />
        </div>
      )}

      {paper.abstractSnippet && (
        <p className="line-clamp-2 text-base leading-relaxed text-fg-2">{paper.abstractSnippet}</p>
      )}
    </article>
  );
}
