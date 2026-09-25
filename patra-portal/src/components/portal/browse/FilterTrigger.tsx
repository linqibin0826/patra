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
      className="relative ml-auto flex shrink-0 items-center gap-1.5 rounded-md border border-(--border-default) bg-paper-50 px-3 py-1.5 text-sm font-semibold text-(--fg-1) transition-colors hover:bg-paper-200 md:hidden"
    >
      <SlidersHorizontalIcon className="size-3.5" />
      筛选
      {count > 0 && (
        <span
          data-testid="filter-badge"
          className="absolute -top-1.5 -right-1.5 flex size-4 items-center justify-center rounded-full bg-clay-500 text-[10px] font-semibold text-white"
        >
          {count}
        </span>
      )}
    </button>
  );
}
