"use client";

import { ListTree } from "lucide-react";
import { type MouseEvent, useMemo, useRef, useState } from "react";
import type { FullTextTocProps } from "@/components/portal/fulltext/FullTextToc";
import { PdfDownloadLink } from "@/components/portal/fulltext/PdfDownloadLink";
import { TocList, tocIds } from "@/components/portal/fulltext/TocList";
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { useActiveHeading } from "@/lib/use-active-heading";

/**
 * 移动端目录（简报 §3.3）：面包屑右端的「目录」药丸按钮，点开从底部升起目录面板（最高 70% 视口）。
 * 点目录项先收起面板（解除滚动锁定、焦点回到按钮），收起完成后再跳到章节并更新地址栏。
 */
export function FullTextTocSheet({
  entries,
  declarationCount,
  referenceCount,
  readMin,
  paperId,
  hasPdf,
  pdfSizeBytes,
  licenseText,
  fetchedDate,
}: FullTextTocProps) {
  const [open, setOpen] = useState(false);
  const pending = useRef<string | null>(null);
  const ids = useMemo(() => tocIds(entries), [entries]);
  const activeId = useActiveHeading(ids);

  const navigate = (event: MouseEvent<HTMLAnchorElement>, id: string) => {
    event.preventDefault();
    pending.current = id;
    setOpen(false);
  };

  const jumpAfterClose = (nextOpen: boolean) => {
    const id = pending.current;
    pending.current = null;
    if (nextOpen || !id) return;
    window.history.pushState(null, "", `#${id}`);
    document.getElementById(id)?.scrollIntoView();
  };

  return (
    <Sheet open={open} onOpenChange={setOpen} onOpenChangeComplete={jumpAfterClose}>
      <SheetTrigger className="ml-auto inline-flex h-11 shrink-0 items-center gap-1.5 rounded-full px-3 font-sans text-sm font-medium text-fg-1 hover:bg-paper-200 min-[980px]:hidden">
        <ListTree size={15} aria-hidden />
        目录
      </SheetTrigger>
      <SheetContent
        side="bottom"
        className="max-h-[70vh] gap-0 overflow-y-auto rounded-t-xl border-border-default bg-bg-elevated px-5 pt-2 pb-6 text-fg-1"
      >
        <div aria-hidden className="mx-auto mt-1 mb-3 h-1 w-9 rounded-full bg-paper-400" />
        <div className="mb-2 flex items-center gap-3 pr-10">
          <SheetTitle className="font-mono text-2xs font-normal uppercase tracking-mono text-fg-3">
            目录
          </SheetTitle>
          {readMin != null && (
            <span className="font-mono text-2xs tracking-mono text-fg-3">≈ {readMin} 分钟</span>
          )}
        </div>
        <TocList
          entries={entries}
          activeId={activeId}
          declarationCount={declarationCount}
          referenceCount={referenceCount}
          large
          onNavigate={navigate}
        />
        <div className="mt-4 flex flex-col gap-2.5 border-t border-border-subtle pt-4">
          {hasPdf && <PdfDownloadLink paperId={paperId} sizeBytes={pdfSizeBytes} block />}
          <p className="m-0 font-mono text-2xs tracking-mono text-fg-3">
            {[licenseText, fetchedDate && `抓取于 ${fetchedDate}`].filter(Boolean).join(" · ")}
          </p>
        </div>
      </SheetContent>
    </Sheet>
  );
}
