import type { ReactNode } from "react";
import type { JournalMetrics } from "@/lib/portal-api/venue-derive";

function Cell({
  label,
  children,
  accent,
}: {
  label: string;
  children: ReactNode;
  accent?: boolean;
}) {
  return (
    <div className="flex flex-col gap-0.5 bg-paper-50 px-3 py-2.5">
      <span className="font-mono text-[9px] uppercase tracking-[0.05em] text-fg-3">
        {label}
      </span>
      <span
        className={`font-sans text-lg font-semibold leading-tight tabular-nums ${accent ? "text-clay-700" : "text-ink-900"}`}
      >
        {children}
      </span>
    </div>
  );
}

function System({ name, source, children }: { name: string; source: string; children: ReactNode }) {
  return (
    <div>
      <div className="mb-2 flex items-baseline gap-2.5">
        <span className="font-sans text-md font-semibold text-ink-900">{name}</span>
        <span className="font-mono text-[9.5px] uppercase tracking-[0.06em] text-fg-4">
          {source}
        </span>
      </div>
      {children}
    </div>
  );
}

const GRID =
  "grid gap-px overflow-hidden rounded-md border border-border-default bg-border-subtle";

export function RatingTable({ metrics }: { metrics: JournalMetrics }) {
  const { jcr, cas, scopus } = metrics;
  return (
    <div className="flex flex-col gap-[18px]">
      {jcr && (
        <System name="JCR · 期刊引证报告" source="Clarivate">
          <div className={`${GRID} grid-cols-4 max-[540px]:grid-cols-2`}>
            <Cell label="影响因子">
              {jcr.impactFactor != null ? jcr.impactFactor.toFixed(1) : "—"}
            </Cell>
            <Cell label="分区" accent>
              {jcr.quartile ?? "—"}
            </Cell>
            <Cell label="学科百分位">{jcr.percentile != null ? `${jcr.percentile}%` : "—"}</Cell>
            <Cell label="排名">{jcr.rank ?? "—"}</Cell>
          </div>
          {jcr.subject && (
            <p className="mt-2 font-sans text-xs text-fg-3">学科 · {jcr.subject}</p>
          )}
        </System>
      )}
      {cas ? (
        <System name="中科院分区 · CAS" source="中科院文献情报中心">
          <div className={`${GRID} grid-cols-2`}>
            <Cell label="大类" accent>
              {cas.majorCategory ?? "—"} · {cas.majorQuartile ?? "—"}
            </Cell>
            <Cell label="小类" accent>
              {cas.minorSubject ?? "—"} · {cas.minorQuartile ?? "—"}
            </Cell>
          </div>
          {(cas.isTop || cas.isReview) && (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {cas.isTop && (
                <span className="rounded-full border border-clay-200 bg-clay-50 px-2.5 py-0.5 font-sans text-xs font-semibold text-clay-800">
                  Top 期刊
                </span>
              )}
              {cas.isReview && (
                <span className="rounded-full border border-clay-200 bg-clay-50 px-2.5 py-0.5 font-sans text-xs font-semibold text-clay-800">
                  综述期刊
                </span>
              )}
            </div>
          )}
        </System>
      ) : (
        <System name="中科院分区 · CAS" source="CAS">
          <p className="rounded-md border border-dashed border-border-default bg-paper-100 px-3 py-2.5 font-sans text-sm text-fg-3">
            该刊暂无中科院分区数据。
          </p>
        </System>
      )}
      {scopus && (
        <System name="Scopus 指标" source="Elsevier">
          <div className={`${GRID} grid-cols-4 max-[540px]:grid-cols-2`}>
            <Cell label="CiteScore">
              {scopus.citeScore != null ? scopus.citeScore.toFixed(1) : "—"}
            </Cell>
            <Cell label="SJR">{scopus.sjr != null ? scopus.sjr.toFixed(2) : "—"}</Cell>
            <Cell label="SNIP">{scopus.snip != null ? scopus.snip.toFixed(2) : "—"}</Cell>
            <Cell label="百分位">{scopus.percentile != null ? `${scopus.percentile}%` : "—"}</Cell>
          </div>
        </System>
      )}
    </div>
  );
}
