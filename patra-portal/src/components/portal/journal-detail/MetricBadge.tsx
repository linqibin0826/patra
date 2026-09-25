import type { MetricCard } from "@/lib/portal-api/venue-derive";

/// 影响力速览单卡（纯展示）。accent=true 为 clay 高亮（IF 卡）。
export function MetricBadge({ card }: { card: MetricCard }) {
  return (
    <div
      className={`flex flex-col gap-1 rounded-md border p-4 ${card.accent ? "border-clay-200 bg-clay-50" : "border-border-default bg-paper-50"}`}
    >
      <span className="font-mono text-3xs uppercase tracking-mono text-fg-3">{card.label}</span>
      <span
        className={`font-serif text-3xl font-medium leading-[1.05] tracking-tight tabular-nums ${card.accent ? "text-clay-800" : "text-ink-900"}`}
      >
        {card.value}
      </span>
      {card.sub && <span className="font-sans text-xs text-fg-3">{card.sub}</span>}
    </div>
  );
}
