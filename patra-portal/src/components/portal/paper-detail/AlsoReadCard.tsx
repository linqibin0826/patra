import { Tag } from "lucide-react";
import Link from "next/link";
import { RichInlineText } from "@/components/portal/RichInlineText";
import type { AlsoReadItem } from "@/lib/analytics/insight-schema";
import { sourceDotClass } from "@/lib/portal-ui";
import { publishedYear } from "@/lib/published-date";
import type { Paper } from "@/types/portal";

/// 「看过这篇的人还看了」里的一张卡片（简报 v0.18 §3.6）：来源 · 年份 · 类型 → 衬线标题（三行截断）→ 刊名 · 第一作者
/// 「等」；补位的多一行原因。不放收藏按钮与计数，保持轻
export function AlsoReadCard({ paper, item }: { paper: Paper; item: AlsoReadItem }) {
  const year = publishedYear(paper);
  const [firstAuthor] = paper.authors;
  // 补位原因：主题词没给就不写这一行；同期刊没给刊名时用卡片自己的刊名
  const reason =
    item.source === "SAME_MESH"
      ? item.meshTerm && { label: "同主题词", value: item.meshTerm, serif: true }
      : item.source === "SAME_VENUE"
        ? (item.venueName ?? paper.journal) && {
            label: "同一期刊",
            value: item.venueName ?? paper.journal ?? "",
            serif: false,
          }
        : null;
  return (
    <article className="flex h-full flex-col gap-2.5 border-t border-border-default pt-4">
      <div className="flex min-w-0 items-center gap-2 font-mono text-2xs text-fg-3">
        <span className="inline-flex shrink-0 items-center gap-1.5 font-medium text-fg-2">
          <span
            aria-hidden
            className={`inline-block size-1.5 rounded-full ${sourceDotClass(paper.source)}`}
          />
          {paper.source}
        </span>
        {year && <span className="tabular-nums">{year}</span>}
        {paper.kind && <span className="truncate uppercase tracking-caps">{paper.kind}</span>}
      </div>
      <h3 className="font-serif text-lg leading-snug font-medium text-ink-900">
        <Link href={`/papers/${paper.id}`} className="link-draw line-clamp-3">
          <RichInlineText text={paper.title} />
        </Link>
      </h3>
      <div className="flex min-w-0 flex-wrap items-baseline gap-x-2 text-xs text-fg-2">
        {paper.journal && (
          <span className="truncate font-serif text-md text-ink-700 italic">{paper.journal}</span>
        )}
        {paper.journal && firstAuthor && (
          <span aria-hidden className="text-ink-300">
            ·
          </span>
        )}
        {firstAuthor && <span>{paper.authors.length > 1 ? `${firstAuthor} 等` : firstAuthor}</span>}
      </div>
      {reason && (
        <p className="mt-auto inline-flex items-center gap-1.5 text-xs text-fg-3">
          <Tag className="size-3.5" strokeWidth={1.75} aria-hidden="true" />
          {reason.label}{" "}
          <span className={reason.serif ? "font-serif text-fg-2" : undefined}>{reason.value}</span>
        </p>
      )}
    </article>
  );
}
