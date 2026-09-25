import Image from "next/image";
import type { ReactNode } from "react";

/// 区块小标题（品牌叶 + 大写标签），详情页多处复用。
export function SectionEyebrow({ children }: { children: ReactNode }) {
  return (
    <p className="mb-3.5 flex items-center gap-2 font-sans text-2xs font-semibold uppercase tracking-caps text-fg-3">
      <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={4} height={14} />
      {children}
    </p>
  );
}
