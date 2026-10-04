import type { MetricCard } from "@/lib/portal-api/venue-derive";
import { cn } from "@/lib/utils";

/// 影响力速览单卡（纯展示）：等宽标签 + 大号衬线数字（lining / tabular）。accent=true 为 clay 高亮（IF 卡）。
export function MetricBadge({ card }: { card: MetricCard }) {
  return (
    <div
      className={cn(
        "flex flex-col gap-2.5 rounded-lg border p-5",
        card.accent ? "border-clay-200 bg-clay-50" : "border-border-default bg-paper-50",
      )}
    >
      <span className="font-mono text-2xs uppercase tracking-mono text-fg-3">{card.label}</span>
      <span
        className={cn(
          "font-serif text-5xl leading-none font-medium tracking-tight lining-nums tabular-nums max-[540px]:text-4xl",
          card.accent ? "text-clay-800" : "text-ink-900",
        )}
      >
        {card.value}
      </span>
      {card.sub && <span className="font-sans text-xs text-fg-3">{card.sub}</span>}
    </div>
  );
}
