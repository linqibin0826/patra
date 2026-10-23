import { graphemeCount } from "@/lib/grapheme";
import { BYTES_PER_GRAPHEME, utf8Length } from "@/lib/identity/profile-form";
import { cn } from "@/lib/utils";

/// 字数计数「n / max」（简报 §4.5）：按字素计，与后端一致；剩 20% 时 amber，超出时危险色。后端另卡
/// 「字素上限 × 4」字节，表情多时字数没超也可能超字节，同样显示危险色。
export function CharCounter({ value, max }: { value: string; max: number }) {
  const count = graphemeCount(value.trim());
  const over = count > max || utf8Length(value) > max * BYTES_PER_GRAPHEME;
  return (
    <span
      className={cn(
        "font-mono text-2xs tabular-nums",
        over ? "text-status-danger" : count >= max * 0.8 ? "text-amber-700" : "text-fg-3",
      )}
    >
      {count} / {max}
    </span>
  );
}
