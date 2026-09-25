import Image from "next/image";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/// 区块小标题（品牌叶 + 大写标签），首页区块、浏览页页头、详情页各段统一复用。
/// `className` 用于调整外边距（如紧接标题时传 `mb-0`）。
export function SectionEyebrow({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <p
      className={cn(
        "mb-3.5 flex items-center gap-2 font-sans text-2xs font-semibold uppercase tracking-caps text-fg-3",
        className,
      )}
    >
      <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={4} height={14} />
      {children}
    </p>
  );
}
