import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/// 小胶囊的语气（画板 `.pt-chipst`）：`success` 已验证、`warning` 未验证、`accent` 本机、`outline` 仅你可见
const TONES = {
  success: "bg-moss-50 text-moss-700",
  warning: "bg-amber-50 text-amber-700",
  accent: "bg-clay-50 text-clay-700",
  outline: "border border-border-default text-fg-3",
} as const;

/// 状态小胶囊：22px 高、圆角、小号中粗字。只是标注，不可点。
export function StatusPill({
  tone,
  children,
  className,
}: {
  tone: keyof typeof TONES;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex h-5.5 shrink-0 items-center rounded-full px-2 font-sans text-xs font-medium whitespace-nowrap",
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}
