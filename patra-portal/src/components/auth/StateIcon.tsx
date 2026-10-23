import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

/// 各语气的底色与图标色（画板 `.pt-state-ic`）
const TONES = {
  success: "bg-status-success-bg text-status-success-ink",
  danger: "bg-status-danger-bg text-status-danger",
  warning: "bg-status-warning-bg text-status-warning-ink",
  info: "bg-status-info-bg text-status-info",
} as const;

/// 专注布局里标题上方的 44px 圆形状态图标：落地页的成功 / 无效 / 冲突、取消注销页。纯装饰，读屏跳过。
export function StateIcon({ icon: Icon, tone }: { icon: LucideIcon; tone: keyof typeof TONES }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "mb-5 inline-flex size-11 items-center justify-center rounded-full",
        TONES[tone],
      )}
    >
      <Icon className="size-5.5" strokeWidth={1.75} />
    </span>
  );
}
