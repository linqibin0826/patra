import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface RailRowProps {
  /** 标签格内容（摘要标签、章节号、图表编号、参考文献序号）；没有时标签格留空占位 */
  label?: ReactNode;
  /** 内容从标签格左缘横跨到正文右缘（128 + 32 + 612 = 772px），不再有标签格 */
  wide?: boolean;
  as?: "div" | "li" | "header";
  className?: string;
  labelClassName?: string;
  children: ReactNode;
}

/**
 * 标签栏的一行（spec §5.4）：左边 128px 标签格，间距 32px，右边内容格。
 * 祖先要带 `@container/main`：main 容器窄于 580px（128 + 32 + 420）时改成单列，标签在上、内容在下。
 */
export function RailRow({
  label,
  wide = false,
  as: Tag = "div",
  className,
  labelClassName,
  children,
}: RailRowProps) {
  return (
    <Tag
      className={cn(
        "grid grid-cols-[128px_minmax(0,1fr)] gap-x-8 gap-y-2 @max-[580px]/main:grid-cols-1",
        className,
      )}
    >
      {wide ? (
        <div className="col-span-full min-w-0 max-w-[772px]">{children}</div>
      ) : (
        <>
          {label ? (
            <div className={cn("min-w-0", labelClassName)}>{label}</div>
          ) : (
            <div aria-hidden="true" className="@max-[580px]/main:hidden" />
          )}
          <div className="min-w-0">{children}</div>
        </>
      )}
    </Tag>
  );
}
