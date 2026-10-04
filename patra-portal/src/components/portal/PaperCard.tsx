import { ArrowRight, Quote } from "lucide-react";
import Link from "next/link";
import { Fragment, type ReactNode } from "react";
import { AISummaryBadge } from "@/components/portal/AISummaryBadge";
import { BookmarkButton } from "@/components/portal/paper-detail/BookmarkButton";
import { RichInlineText } from "@/components/portal/RichInlineText";
import { sourceDotClass } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";
import type { Paper } from "@/types/portal";

interface PaperCardProps {
  paper: Paper;
  /** lead：首页头条（大标题 + 摘要片段）；default：编号列表条目 */
  variant?: "lead" | "default";
  /** 1 起的序号，显示为两位数 */
  index?: number;
}

/// 首页文献流条目（编辑排版，无卡片框）：顶部发丝线 hover 时由陶土色自左生长、标题下划线绘出。
/// 来源行（序号 · 来源 · DOI）→ 衬线标题 → 刊名 · 年份 · 作者 → [头条摘要] → [AI 速读] → 阅读详情 / 收藏 / 引用数。
export function PaperCard({ paper, variant = "default", index }: PaperCardProps) {
  const lead = variant === "lead";
  const visibleAuthors = paper.authors.slice(0, 2);
  const remaining = paper.authors.length - visibleAuthors.length;

  // 元信息两行：刊名独占一行（过长截断），年份 · 作者第二行——避免长刊名把分隔符挤到行首
  const byline: { key: string; node: ReactNode }[] = [];
  if (paper.year != null) {
    byline.push({ key: "year", node: <span className="tabular-nums">{paper.year}</span> });
  }
  if (paper.authors.length > 0) {
    byline.push({
      key: "authors",
      node: (
        <span>
          {visibleAuthors.join(", ")}
          {remaining > 0 && ` 等 ${remaining} 位作者`}
        </span>
      ),
    });
  }

  return (
    <article
      className={cn(
        "group/paper relative flex h-full flex-col border-t border-border-default before:absolute before:-top-px before:left-0 before:h-0.5 before:w-0 before:bg-clay-500 before:transition-[width] before:duration-700 before:ease-out-expo hover:before:w-full",
        lead ? "gap-4 pt-6" : "gap-3 pt-5 pb-7",
      )}
    >
      <div className="flex min-w-0 items-center gap-3 font-mono text-2xs text-fg-3">
        {index != null && (
          <span className="font-medium text-accent-strong">{String(index).padStart(2, "0")}</span>
        )}
        <span className="inline-flex shrink-0 items-center gap-1.5 font-medium text-fg-2">
          <span
            aria-hidden
            className={`inline-block size-1.5 rounded-full ${sourceDotClass(paper.source)}`}
          />
          {paper.source}
        </span>
        {paper.doi && (
          <span data-doi className="truncate">
            {paper.doi}
          </span>
        )}
        {paper.kind && (
          <span className="ml-auto shrink-0 uppercase tracking-caps max-sm:hidden">
            {paper.kind}
          </span>
        )}
      </div>

      <h3
        className={cn(
          "font-serif font-medium tracking-tight text-pretty text-ink-900",
          lead ? "text-display-3 leading-heading" : "text-xl leading-snug",
        )}
      >
        <Link
          href={`/papers/${paper.id}`}
          className="link-draw pb-0.5 group-hover/paper:[background-size:100%_1px]"
        >
          <RichInlineText text={paper.title} />
        </Link>
      </h3>

      {(paper.journal || byline.length > 0) && (
        <div className="flex min-w-0 flex-col gap-1 text-xs text-fg-2">
          {paper.journal && (
            <span className="truncate font-serif text-md text-ink-700 italic">{paper.journal}</span>
          )}
          {byline.length > 0 && (
            <span className="flex flex-wrap items-baseline gap-x-2">
              {byline.map((item, i) => (
                <Fragment key={item.key}>
                  {i > 0 && (
                    <span aria-hidden className="text-ink-300">
                      ·
                    </span>
                  )}
                  {item.node}
                </Fragment>
              ))}
            </span>
          )}
        </div>
      )}

      {lead && paper.abstractSnippet && (
        <p className="line-clamp-5 max-w-[62ch] font-serif text-lg leading-relaxed text-pretty text-fg-2">
          {paper.abstractSnippet}
        </p>
      )}

      {paper.aiSummary && (
        <AISummaryBadge aiSummary={paper.aiSummary} estimatedReadMin={paper.estimatedReadMin} />
      )}

      <div className={cn("flex items-center gap-2 pt-1 text-sm", !lead && "mt-auto")}>
        <Link
          href={`/papers/${paper.id}`}
          className="group/more inline-flex items-center gap-1 font-medium text-ink-900"
        >
          阅读详情
          <ArrowRight
            className="size-3.5 transition-transform duration-300 ease-out-expo motion-safe:group-hover/more:translate-x-0.5"
            strokeWidth={1.75}
            aria-hidden
          />
        </Link>
        <span className="ml-auto inline-flex items-center gap-1.5 font-mono text-xs tabular-nums text-fg-3">
          <Quote className="size-3.5" strokeWidth={1.5} aria-hidden />
          {paper.cites ?? 0} 引用
        </span>
        <BookmarkButton paperId={paper.id} compact />
      </div>
    </article>
  );
}
