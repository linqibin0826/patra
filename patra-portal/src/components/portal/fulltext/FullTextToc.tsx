"use client";

import { useEffect, useMemo, useRef } from "react";
import { PdfDownloadLink } from "@/components/portal/fulltext/PdfDownloadLink";
import { TocList, tocIds } from "@/components/portal/fulltext/TocList";
import type { TocEntry } from "@/lib/portal-api/fulltext-derive";
import { useActiveHeading } from "@/lib/use-active-heading";

export interface FullTextTocProps {
  entries: TocEntry[];
  hasReferences: boolean;
  readMin: number | null;
  paperId: string;
  hasPdf: boolean;
  pdfSizeBytes: string | null;
  licenseText: string;
  fetchedDate: string | null;
}

/**
 * 桌面侧栏的目录卡（简报 §3.2）：单独吸顶，从全文区开始陪到页底；当前章节高亮并展开二级标题；
 * 目录项超过卡片可用高度时卡内滚动。卡底是下载 PDF 与许可 · 抓取日期。
 */
export function FullTextToc({
  entries,
  hasReferences,
  readMin,
  paperId,
  hasPdf,
  pdfSizeBytes,
  licenseText,
  fetchedDate,
}: FullTextTocProps) {
  const ids = useMemo(() => tocIds(entries), [entries]);
  const activeId = useActiveHeading(ids);
  const listRef = useRef<HTMLDivElement>(null);

  // 目录比卡片高时，当前项滚进卡内可见区（只滚卡片自己，不带动页面）
  useEffect(() => {
    const box = listRef.current;
    const link = activeId
      ? box?.querySelector<HTMLElement>(`a[href="#${CSS.escape(activeId)}"]`)
      : null;
    if (!box || !link) return;
    const top = link.offsetTop - box.offsetTop;
    if (top < box.scrollTop || top + link.offsetHeight > box.scrollTop + box.clientHeight) {
      box.scrollTop = top - box.clientHeight / 3;
    }
  }, [activeId]);

  return (
    <nav
      aria-label="目录"
      className="flex max-h-[calc(100vh-134px-24px)] flex-col rounded-xl border border-border-default bg-paper-50 p-5"
    >
      <div className="mb-3 flex items-center justify-between font-mono text-2xs uppercase tracking-mono text-fg-3">
        <span>目录</span>
        {readMin != null && <span className="normal-case">≈ {readMin} 分钟</span>}
      </div>
      <div ref={listRef} className="relative min-h-0 overflow-y-auto overscroll-contain">
        <TocList entries={entries} activeId={activeId} hasReferences={hasReferences} />
      </div>
      <div className="mt-4 flex flex-col gap-2.5 border-t border-border-subtle pt-4">
        {hasPdf && <PdfDownloadLink paperId={paperId} sizeBytes={pdfSizeBytes} block />}
        <p className="m-0 font-mono text-2xs tracking-mono text-fg-3">
          {[licenseText, fetchedDate && `抓取于 ${fetchedDate}`].filter(Boolean).join(" · ")}
        </p>
      </div>
    </nav>
  );
}
