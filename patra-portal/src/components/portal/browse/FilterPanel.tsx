"use client";

import type { ReactNode } from "react";
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";

interface FilterPanelProps {
  children: ReactNode;
  sheetTitle: ReactNode;
  sheetFooter: ReactNode;
  /** 移动端抽屉方向：期刊页 left，文献页 bottom（hi-fi） */
  side?: "left" | "bottom";
  /** 桌面侧栏宽度 class */
  railClassName?: string;
}

/// 筛选容器：桌面（md+）为左侧常显栏；移动端收进 Sheet 抽屉（开关由 useBrowseFilterUiStore 控制）。
/// 抽屉内容区独立滚动，标题与页脚固定。
export function FilterPanel({
  children,
  sheetTitle,
  sheetFooter,
  side = "left",
  railClassName = "w-56",
}: FilterPanelProps) {
  const sheetOpen = useBrowseFilterUiStore((s) => s.sheetOpen);
  const close = useBrowseFilterUiStore((s) => s.close);

  return (
    <>
      <aside className={cn("hidden shrink-0 md:block", railClassName)}>{children}</aside>

      <Sheet open={sheetOpen} onOpenChange={(open) => !open && close()}>
        <SheetContent
          side={side}
          className={cn(
            "gap-0 p-0 md:hidden",
            side === "left" ? "w-72" : "max-h-[85vh] rounded-t-xl",
          )}
        >
          <SheetHeader className="px-4 pt-4 pb-2">
            <SheetTitle>{sheetTitle}</SheetTitle>
          </SheetHeader>
          <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-2">{children}</div>
          <SheetFooter className="border-t border-(--border-subtle) px-4 py-3">
            {sheetFooter}
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </>
  );
}
