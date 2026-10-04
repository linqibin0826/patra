import type { YearlyStat } from "@/types/portal";

/// 年发文 / 年被引双柱图（各自按最大值归一）：柱体进入视口时自底生长（scroll-driven，仅 motion-safe），
/// hover 时整组提亮并显示年份标签加粗。
export function TrendChart({ stats }: { stats: YearlyStat[] }) {
  if (stats.length === 0) {
    return null;
  }
  const maxW = Math.max(...stats.map((s) => s.worksCount ?? 0), 1);
  const maxC = Math.max(...stats.map((s) => s.citedByCount ?? 0), 1);
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-5 font-mono text-2xs uppercase tracking-mono text-fg-3">
        <span className="inline-flex items-center gap-1.5">
          <i className="inline-block size-2.5 rounded-[2px] bg-ink-800" /> 年发文量
        </span>
        <span className="inline-flex items-center gap-1.5">
          <i className="inline-block size-2.5 rounded-[2px] bg-clay-400" /> 年被引
        </span>
      </div>
      <div className="grid h-[180px] auto-cols-fr grid-flow-col items-end gap-3 border-b border-border-default pt-2 max-[540px]:gap-1.5">
        {stats.map((s) => (
          <div
            key={s.year}
            className="group/bar flex h-full flex-col items-center justify-end gap-2"
          >
            <div className="reveal-grow flex h-full w-full items-end justify-center gap-[3px] opacity-85 transition-opacity duration-200 group-hover/bar:opacity-100">
              <div
                className="w-3.5 rounded-t-[2px] bg-ink-800 max-[540px]:w-2"
                // 柱高是数据驱动的百分比，只能经 style 传入
                style={{ height: `${Math.round(((s.worksCount ?? 0) / maxW) * 100)}%` }}
                title={`${s.year} · 发文 ${(s.worksCount ?? 0).toLocaleString()}`}
              />
              <div
                className="w-3.5 rounded-t-[2px] bg-clay-400 max-[540px]:w-2"
                style={{ height: `${Math.round(((s.citedByCount ?? 0) / maxC) * 100)}%` }}
                title={`${s.year} · 被引 ${(s.citedByCount ?? 0).toLocaleString()}`}
              />
            </div>
            <span className="font-mono text-3xs tabular-nums text-fg-3 transition-colors group-hover/bar:font-semibold group-hover/bar:text-ink-900">
              {String(s.year).slice(2)}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
