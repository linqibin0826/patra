import type { ReactNode } from "react";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";
import { deriveFullTextStats } from "@/lib/portal-api/fulltext-derive";
import { deriveEvidence } from "@/lib/portal-api/publication-derive";
import type { PaperDetail } from "@/types/portal";

const CELL =
  "border-r border-border-subtle py-3.5 pr-4 pl-4 first:pl-0 last:border-r-0 max-[540px]:border-r-0 max-[540px]:pl-0";

/** 事实条的一格：等宽小标 + 数值。 */
export function FactCell({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className={CELL}>
      <dt className="font-mono text-3xs tracking-mono text-fg-3">{term}</dt>
      <dd className="m-0 mt-1.5 flex items-center gap-1.5 font-sans text-md font-medium tabular-nums text-fg-1">
        {children}
      </dd>
    </div>
  );
}

/**
 * 页头事实条（spec §5.2）：详情里有的几项直接给出；篇幅、图表、参考文献来自全文接口，
 * 由调用方包在 Suspense 里作为 `fullTextFacts` 传入，排在最后。
 */
export function PaperFacts({
  paper,
  fullTextFacts,
}: {
  paper: PaperDetail;
  fullTextFacts?: ReactNode;
}) {
  return (
    <dl className="m-0 mt-8 grid grid-cols-[repeat(auto-fit,minmax(128px,1fr))] border-y border-border-default">
      <FactCell term="证据等级">{deriveEvidence(paper.evidenceLevel).label}</FactCell>
      <FactCell term="被引">{(paper.citationCount ?? 0).toLocaleString("en-US")}</FactCell>
      <FactCell term="收藏">{(paper.bookmarks ?? 0).toLocaleString("en-US")}</FactCell>
      <FactCell term="来源">
        <span aria-hidden className="size-1.5 rounded-full bg-slate-500" />
        {paper.source ?? "—"}
      </FactCell>
      {paper.estimatedReadMin != null && (
        <FactCell term="原文阅读">≈ {paper.estimatedReadMin} 分钟</FactCell>
      )}
      {fullTextFacts}
    </dl>
  );
}

/** 全文三项流式到达前的占位格。 */
export function FactCellsSkeleton() {
  return (
    <>
      {[0, 1, 2].map((i) => (
        <div key={i} aria-hidden className={CELL}>
          <div className="shimmer h-3 w-10 rounded-sm" />
          <div className="shimmer mt-2 h-4 w-16 rounded-sm" />
        </div>
      ))}
    </>
  );
}

/** 事实条里来自全文接口的三格：篇幅、图表（为 0 的一项省掉）、参考文献；取不到全文时不渲染。 */
export async function FullTextFacts({ paper }: { paper: PaperDetail }) {
  const fullText = await fetchPublicationFullText(paper.id).catch(() => null);
  const body = fullText?.body;
  if (!fullText || !body) {
    return null;
  }
  const stats = deriveFullTextStats(body);
  const figures = [
    stats.figures > 0 && `${stats.figures} 图`,
    stats.tables > 0 && `${stats.tables} 表`,
  ]
    .filter(Boolean)
    .join(" · ");
  return (
    <>
      {fullText.wordCount != null && (
        <FactCell term="篇幅">{fullText.wordCount.toLocaleString("en-US")} 词</FactCell>
      )}
      {figures && <FactCell term="图表">{figures}</FactCell>}
      {stats.references > 0 && <FactCell term="参考文献">{stats.references} 篇</FactCell>}
    </>
  );
}
