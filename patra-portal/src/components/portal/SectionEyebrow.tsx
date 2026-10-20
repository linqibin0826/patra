import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/// 区块小标题（大写标签），首页区块、浏览页页头、详情页各段统一复用。
/// - 默认以 clay-600 短竖条起头；传 `index`（如 "02"）时改为「序号 + 短线」，用于首页编号区块。
/// - `tone="inverse"` 用于墨色区块。`className` 用于调整外边距（如紧接标题时传 `mb-0`）。
/// - `as="h2"` 用于它本身就是区块标题的地方（文献详情的「摘要 · ABSTRACT」），带 `id` 供 aria-labelledby。
export function SectionEyebrow({
  children,
  className,
  index,
  tone = "paper",
  as: Tag = "p",
  id,
}: {
  children: ReactNode;
  className?: string;
  index?: string;
  tone?: "paper" | "inverse";
  as?: "p" | "h2";
  id?: string;
}) {
  const inverse = tone === "inverse";
  return (
    <Tag
      id={id}
      className={cn(
        "mb-3.5 flex items-center gap-2 font-sans text-2xs font-semibold uppercase tracking-caps",
        inverse ? "text-fg-inverse-3" : "text-fg-3",
        className,
      )}
    >
      {index ? (
        <>
          <span
            className={cn(
              "font-mono font-medium tracking-mono",
              inverse ? "text-accent-on-inverse" : "text-accent-strong",
            )}
          >
            {index}
          </span>
          <span aria-hidden className="h-px w-6 bg-current opacity-50" />
        </>
      ) : (
        <span aria-hidden className="h-3.5 w-1 rounded-[1px] bg-clay-600" />
      )}
      {children}
    </Tag>
  );
}
