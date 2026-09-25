import { deriveEvidence, type EvidenceTone } from "@/lib/portal-api/publication-derive";
import { cn } from "@/lib/utils";
import type { EvidenceLevel } from "@/types/portal";

const TONE_CLASS: Record<EvidenceTone, string> = {
  moss: "bg-moss-50 text-moss-500 border-[color-mix(in_oklab,var(--moss-500)_30%,transparent)]",
  amber:
    "bg-amber-50 text-amber-500 border-[color-mix(in_oklab,var(--amber-500)_30%,transparent)]",
  slate:
    "bg-slate-50 text-slate-500 border-[color-mix(in_oklab,var(--slate-500)_30%,transparent)]",
  muted: "border-dashed border-border-default bg-paper-200 text-fg-3",
};
const RUNG_CLASS = ["h-[7px]", "h-[10px]", "h-[12px]", "h-[14px]", "h-[16px]"];
const COMPACT_RUNG_CLASS = ["h-[4px]", "h-[6px]", "h-[8px]", "h-[10px]", "h-[11px]"];
/// 证据等级徽章，仅成功分级时渲染（语料 84% 未分级，徽章只在有信息量时出现；未分级仅在速览行以文字呈现）。
/// 默认为详情页大徽章；compact 为文献列表用的单行小徽章（阶梯 + 中文名）。
export function EvidenceBadge({
  level,
  compact = false,
}: {
  level: EvidenceLevel;
  compact?: boolean;
}) {
  const ev = deriveEvidence(level);
  if (!ev.derived) {
    return null;
  }
  if (compact) {
    return (
      <span
        className={cn(
          "inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 font-sans text-[11.5px] font-semibold",
          TONE_CLASS[ev.tone],
        )}
        title={`证据等级 · ${ev.label}`}
      >
        <span aria-hidden className="inline-flex h-[11px] items-end gap-0.5">
          {COMPACT_RUNG_CLASS.map((hc, i) => (
            <span
              key={hc}
              className={cn(
                "w-[3px] rounded-[1px] bg-current",
                hc,
                i < ev.lit ? "opacity-100" : "opacity-30",
              )}
            />
          ))}
        </span>
        {ev.label}
      </span>
    );
  }
  return (
    <span
      className={`inline-flex items-center gap-2.5 rounded-md border px-3 py-[7px] ${TONE_CLASS[ev.tone]}`}
      title={`证据等级 · ${ev.label}`}
    >
      <span aria-hidden className="inline-flex h-4 items-end gap-0.5">
        {RUNG_CLASS.map((hc, i) => (
          <span
            key={hc}
            className={`w-1 rounded-[1px] bg-current ${hc} ${i < ev.lit ? "opacity-100" : "opacity-30"}`}
          />
        ))}
      </span>
      <span className="flex flex-col leading-[1.15]">
        <span className="font-sans text-sm font-semibold">{ev.label}</span>
        <span className="font-mono text-[9px] uppercase tracking-[0.06em] opacity-70">
          证据等级 · {ev.en}
        </span>
      </span>
      <span className="rounded-[3px] border border-current px-1.5 py-px font-mono text-[9px] uppercase tracking-[0.04em] opacity-50">
        衍生
      </span>
    </span>
  );
}
