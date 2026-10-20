"use client";

import { useEffect, useMemo, useRef } from "react";
import { PdfDownloadLink } from "@/components/portal/fulltext/PdfDownloadLink";
import { TocList, tocIds } from "@/components/portal/fulltext/TocList";
import type { TocEntry } from "@/lib/portal-api/fulltext-derive";
import { useActiveHeading } from "@/lib/use-active-heading";
import { remainingMinutes, useReadingProgress } from "@/lib/use-reading-progress";

export interface FullTextTocProps {
  entries: TocEntry[];
  /** 「声明与附注」的项数；0 时不列 */
  declarationCount: number;
  /** 参考文献条数；0 时不列 */
  referenceCount: number;
  readMin: number | null;
  paperId: string;
  hasPdf: boolean;
  pdfSizeBytes: string | null;
  licenseText: string;
  fetchedDate: string | null;
}

/** 卡头的阅读时长：还没读到正文时是全文时长，读到正文后是剩余分钟，正文读完后说一声。 */
function readingTime(readMin: number, progress: { body: number; started: boolean }): string {
  if (!progress.started) return `≈ ${readMin} 分钟`;
  if (progress.body >= 1) return "正文已读完";
  return `剩 ≈ ${remainingMinutes(readMin, progress.body)} 分钟`;
}

/**
 * 桌面侧栏的目录卡（简报 §3.2）：单独吸顶，从全文区开始陪到页底；当前章节高亮并展开二级标题；
 * 目录项超过卡片可用高度时卡内滚动。卡底是下载 PDF 与许可 · 抓取日期。
 */
export function FullTextToc({
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
  const ids = useMemo(() => tocIds(entries), [entries]);
  const activeId = useActiveHeading(ids);
  const progress = useReadingProgress();
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
        {readMin != null && <span className="normal-case">{readingTime(readMin, progress)}</span>}
      </div>
      <div ref={listRef} className="relative min-h-0 overflow-y-auto overscroll-contain">
        <TocList
          entries={entries}
          activeId={activeId}
          declarationCount={declarationCount}
          referenceCount={referenceCount}
        />
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
