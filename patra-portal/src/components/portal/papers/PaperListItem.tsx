import Link from "next/link";
import { Fragment, type ReactNode } from "react";
import { EvidenceBadge } from "@/components/portal/EvidenceBadge";
import { RichInlineText } from "@/components/portal/RichInlineText";
import { snippetText } from "@/lib/portal-api/publication-derive";
import { sourceDotClass } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";
import type { Paper } from "@/types/portal";

interface PaperListItemProps {
  paper: Paper;
  /** 检索词：标题中的命中片段高亮 */
  highlight?: string;
}

/**
 * 文献检索页列表项（RSC）：PaperCard 的紧凑列表变体。
 * 来源行（来源 · DOI · 类型）→ 衬线标题（两行截断，可高亮）→ 期刊 · 年份 · 作者 → 证据徽章 → 摘要两行。
 * hover 时左侧页边一道陶土细线自上而下生长、标题下划线绘出。
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
          className="link-draw max-w-full truncate pb-px font-serif text-md text-ink-700 italic hover:text-clay-700 max-md:basis-full"
        >
          {paper.journal}
        </Link>
      ) : (
        <span className="max-w-full truncate font-serif text-md text-ink-700 italic max-md:basis-full">
          {paper.journal}
        </span>
      ),
    });
  }
  if (paper.year !== null) {
    meta.push({ key: "year", node: <span className="tabular-nums">{paper.year}</span> });
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

  return (
    <article className="group/item relative flex flex-col gap-2 border-b border-border-subtle py-6 before:absolute before:top-6 before:bottom-6 before:-left-5 before:w-0.5 before:origin-top before:scale-y-0 before:rounded-full before:bg-clay-500 motion-safe:before:transition-transform before:duration-500 before:ease-out-expo hover:before:scale-y-100 max-md:before:hidden">
      <div className="flex min-w-0 items-center gap-2.5 font-mono text-2xs text-fg-3">
        <span className="inline-flex shrink-0 items-center gap-1.5 font-medium text-fg-2">
          <span
            aria-hidden
            className={`inline-block size-1.5 rounded-full ${sourceDotClass(paper.source)}`}
          />
          {paper.source}
        </span>
        {paper.doi && <span className="truncate">{paper.doi}</span>}
        {paper.kind && (
          <span className="ml-auto shrink-0 uppercase tracking-caps max-sm:hidden">
            {paper.kind}
          </span>
        )}
      </div>

      {/* 两行截断放在链接里面：截断容器的 overflow: hidden 若在链接外层，会裁掉链接的键盘焦点环 */}
      <h3 className="font-serif text-xl leading-snug font-medium tracking-tight text-pretty text-ink-900">
        <Link href={`/papers/${paper.id}`} className="group/title block">
          <span className="line-clamp-2">
            <span className="link-draw pb-0.5 group-hover/item:[background-size:100%_1px] group-focus-visible/title:[background-size:100%_1px]">
              <RichInlineText text={paper.title} highlight={highlight} />
            </span>
          </span>
        </Link>
      </h3>

      {meta.length > 0 && (
        <div className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-1 text-xs text-fg-2">
          {meta.map((item, index) => (
            <Fragment key={item.key}>
              {index > 0 && (
                // 窄屏刊名独占一行，紧随其后的分隔符会落到下一行行首，隐藏之
                <span
                  aria-hidden="true"
                  className={cn(
                    "text-ink-300",
                    meta[index - 1]?.key === "journal" && "max-md:hidden",
                  )}
                >
                  ·
                </span>
              )}
              {item.node}
            </Fragment>
          ))}
        </div>
      )}

      {paper.evidenceLevel.derived && (
        <div className="flex flex-wrap items-center gap-2">
          <EvidenceBadge level={paper.evidenceLevel} compact />
        </div>
      )}

      {paper.abstractSnippet && (
        <p className="mt-1 line-clamp-2 max-w-[72ch] font-serif text-md leading-relaxed text-fg-2">
          {snippetText(paper.abstractSnippet)}
        </p>
      )}
    </article>
  );
}
