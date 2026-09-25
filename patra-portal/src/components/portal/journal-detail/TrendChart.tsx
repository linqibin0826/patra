import type { YearlyStat } from "@/types/portal";

export function TrendChart({ stats }: { stats: YearlyStat[] }) {
  if (stats.length === 0) {
    return null;
  }
  const maxW = Math.max(...stats.map((s) => s.worksCount ?? 0), 1);
  const maxC = Math.max(...stats.map((s) => s.citedByCount ?? 0), 1);
  return (
    <div className="flex flex-col gap-3.5">
      <div className="flex items-center gap-4 font-mono text-2xs uppercase tracking-[0.05em] text-fg-3">
        <span className="inline-flex items-center gap-1.5">
          <i className="inline-block h-2.5 w-2.5 rounded-[2px] bg-ink-800" /> 年发文量
        </span>
        <span className="inline-flex items-center gap-1.5">
          <i className="inline-block h-2.5 w-2.5 rounded-[2px] bg-clay-400" /> 年被引
        </span>
      </div>
      <div className="grid h-[150px] auto-cols-fr grid-flow-col items-end gap-3.5 border-b border-border-default pt-2">
        {stats.map((s) => (
          <div key={s.year} className="flex h-full flex-col items-center justify-end gap-1.5">
            <div className="flex h-full w-full items-end justify-center gap-[3px]">
              <div
                className="w-3.5 rounded-t-[2px] bg-ink-800"
                style={{ height: `${Math.round(((s.worksCount ?? 0) / maxW) * 100)}%` }}
                title={`${s.year} · 发文 ${(s.worksCount ?? 0).toLocaleString()}`}
              />
              <div
                className="w-3.5 rounded-t-[2px] bg-clay-400"
                style={{ height: `${Math.round(((s.citedByCount ?? 0) / maxC) * 100)}%` }}
                title={`${s.year} · 被引 ${(s.citedByCount ?? 0).toLocaleString()}`}
              />
            </div>
            <span className="font-mono text-[10px] tabular-nums text-fg-3">
              {String(s.year).slice(2)}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
