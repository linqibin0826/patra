import type { ReactNode } from "react";
import { FACT_CELL, FactCell } from "@/components/portal/paper-detail/FactCell";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";
import { deriveFullTextState, deriveFullTextStats } from "@/lib/portal-api/fulltext-derive";
import { deriveEvidence } from "@/lib/portal-api/publication-derive";
import type { PaperDetail } from "@/types/portal";

/**
 * 页头事实条（spec §5.2）：详情里有的几项直接给出；篇幅、图表、参考文献来自全文接口，
 * 由调用方包在 Suspense 里作为 `fullTextFacts` 传入；收藏人数与讨论数来自 interaction（`interactionFacts`，
 * 同样在 Suspense 里），放在最后，晚到也不挤动前面的格子（v0.15 简报 §3.7）。
 */
export function PaperFacts({
  paper,
  fullTextFacts,
  interactionFacts,
}: {
  paper: PaperDetail;
  fullTextFacts?: ReactNode;
  interactionFacts?: ReactNode;
}) {
  return (
    <dl className="m-0 mt-8 grid grid-cols-[repeat(auto-fit,minmax(128px,1fr))] border-y border-border-default">
      <FactCell term="证据等级">{deriveEvidence(paper.evidenceLevel).label}</FactCell>
      <FactCell term="被引">{(paper.citationCount ?? 0).toLocaleString("en-US")}</FactCell>
      <FactCell term="来源">
        <span aria-hidden className="size-1.5 rounded-full bg-slate-500" />
        {paper.source ?? "—"}
      </FactCell>
      {paper.estimatedReadMin != null && (
        <FactCell term="原文阅读">≈ {paper.estimatedReadMin} 分钟</FactCell>
      )}
      {fullTextFacts}
      {interactionFacts}
    </dl>
  );
}

/** 全文三项流式到达前的占位格。 */
export function FactCellsSkeleton() {
  return (
    <>
      {[0, 1, 2].map((i) => (
        <div key={i} aria-hidden className={FACT_CELL}>
          <div className="shimmer h-3 w-10 rounded-sm" />
          <div className="shimmer mt-2 h-4 w-16 rounded-sm" />
        </div>
      ))}
    </>
  );
}

/** 事实条里来自全文接口的三格：篇幅、图表（为 0 的一项省掉）、参考文献；没有可读正文或取不到全文时不渲染。 */
export async function FullTextFacts({ paper }: { paper: PaperDetail }) {
  // 只有页面真的渲染正文时才给（已撤稿的文献即便后端标了有全文也不展示）
  if (deriveFullTextState(paper) !== "full") {
    return null;
  }
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
