import { graphemeCount } from "@/lib/grapheme";
import { cn } from "@/lib/utils";

/// 字数计数「n / max」（简报 §4.5）：按字素计，与后端一致；剩 20% 时 amber，超出时危险色。
export function CharCounter({ value, max }: { value: string; max: number }) {
  const count = graphemeCount(value.trim());
  return (
    <span
      className={cn(
        "font-mono text-2xs tabular-nums",
        count > max ? "text-status-danger" : count >= max * 0.8 ? "text-amber-700" : "text-fg-3",
      )}
    >
      {count} / {max}
    </span>
  );
}
