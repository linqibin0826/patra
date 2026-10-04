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

/// 筛选容器：桌面（md+）为左侧吸顶栏（超出视口时栏内独立滚动）；移动端收进 Sheet 抽屉（开关由 useBrowseFilterUiStore 控制）。
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
      <aside
        className={cn(
          "sticky top-20 -ml-2 hidden max-h-[calc(100dvh-6rem)] shrink-0 overflow-y-auto overscroll-contain pr-3 pl-2 [scrollbar-width:thin] md:block",
          railClassName,
        )}
      >
        {children}
      </aside>

      <Sheet open={sheetOpen} onOpenChange={(open) => !open && close()}>
        <SheetContent
          side={side}
          className={cn(
            "gap-0 bg-bg-canvas p-0 md:hidden",
            // 同一 data 变体才能经 tailwind-merge 覆盖 Sheet 默认的 data-[side=left]:w-3/4
            side === "left" ? "data-[side=left]:w-80" : "max-h-[85vh] rounded-t-2xl",
          )}
        >
          <SheetHeader className="border-b border-border-subtle px-5 pt-5 pb-4">
            <SheetTitle className="font-serif text-2xl font-medium tracking-tight">
              {sheetTitle}
            </SheetTitle>
          </SheetHeader>
          <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-2">{children}</div>
          <SheetFooter className="border-t border-border-subtle bg-bg-elevated px-5 py-4">
            {sheetFooter}
          </SheetFooter>
        </SheetContent>
      </Sheet>
    </>
  );
}
