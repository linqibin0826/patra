import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

const TONE = {
  clay: "border-clay-200 bg-clay-50 text-clay-800",
  neutral: "border-border-default bg-paper-50 text-fg-2",
} as const;

const SIZE = {
  /** 贴在正文旁的小标签：「第一作者」「通讯」 */
  sm: "px-2 py-px text-3xs font-semibold",
  /** 独立成行的胶囊：MeSH 主题词、「Top 期刊」「综述期刊」 */
  md: "px-3 py-1 text-sm font-medium",
} as const;

interface PillProps {
  children: ReactNode;
  /** clay：需要强调的身份（通讯作者、主要主题词、Top 期刊）；neutral：其余 */
  tone?: keyof typeof TONE;
  size?: keyof typeof SIZE;
  title?: string;
  /** 只放外边距、对齐等布局类 */
  className?: string;
}

/// 只读圆角胶囊（无衬线）：身份、分级、主题词这类短标签。不可点击；可点击的条件用 FilterChip，
/// 等宽的来源 / 类型 / 开放获取标签用 Tag 的写法。
export function Pill({ children, tone = "neutral", size = "md", title, className }: PillProps) {
  return (
    <span
      title={title}
      className={cn(
        "inline-flex items-center gap-1 rounded-full border font-sans",
        TONE[tone],
        SIZE[size],
        className,
      )}
    >
      {children}
    </span>
  );
}
