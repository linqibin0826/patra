"use client";

import { SlidersHorizontalIcon } from "lucide-react";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";

/// 移动端「筛选」按钮（md 以上隐藏）：打开筛选抽屉，角标为已选条件数。
export function FilterTrigger({ count }: { count: number }) {
  const open = useBrowseFilterUiStore((s) => s.open);
  return (
    <button
      type="button"
      onClick={open}
      aria-label="筛选"
      className="relative ml-auto flex h-10 shrink-0 items-center gap-1.5 rounded-full border border-border-strong bg-paper-50 px-4 text-sm font-semibold text-fg-1 transition-colors hover:border-ink-500 md:hidden"
    >
      <SlidersHorizontalIcon className="size-3.5" aria-hidden />
      筛选
      {count > 0 && (
        <span className="absolute -top-1.5 -right-1.5 flex size-5 items-center justify-center rounded-full bg-action-primary font-mono text-3xs font-semibold text-fg-on-clay ring-2 ring-bg-canvas">
          {count}
        </span>
      )}
    </button>
  );
}
