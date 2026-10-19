"use client";

import { type ReactNode, useRef, useState } from "react";
import { btnSecondary } from "@/lib/portal-ui";
import { useScrollFade } from "@/lib/use-scroll-fade";
import { cn } from "@/lib/utils";

export interface TableBlockCell {
  content: ReactNode;
  colspan: number;
  rowspan: number;
  header: boolean;
}

export type TableBlockRow = TableBlockCell[];

interface TableBlockProps {
  anchorId: string | undefined;
  /** 原文标签，如 `Table 2` */
  label: string | null;
  title: ReactNode;
  /** 表题纯文本：给读屏的 caption（title 里可能有带 id 的引用，不能渲染两遍） */
  titleText: string;
  head: TableBlockRow[];
  body: TableBlockRow[];
  /** 表注（table-wrap-foot），没有时为 null */
  footnotes: ReactNode;
}

/** 正文行超过这个数才折叠；折叠时显示前 COLLAPSED_ROWS 行（简报 §3.4「长表」）。 */
const COLLAPSE_THRESHOLD = 12;
const COLLAPSED_ROWS = 10;

function renderCell(cell: TableBlockCell, key: number, inHead: boolean): ReactNode {
  const span = {
    colSpan: cell.colspan > 1 ? cell.colspan : undefined,
    rowSpan: cell.rowspan > 1 ? cell.rowspan : undefined,
  };
  if (inHead) {
    return (
      <th key={key} scope="col" {...span}>
        {cell.content}
      </th>
    );
  }
  if (cell.header) {
    return (
      <th key={key} scope="row" {...span}>
        {cell.content}
      </th>
    );
  }
  return (
    <td key={key} {...span}>
      {cell.content}
    </td>
  );
}

/**
 * 正文表格：表题在上、表注在下；宽表盒内横滚、首列吸左、右缘渐隐；
 * 长表只显示前 10 行，「展开全部 N 行」/「收起」（收起时滚回表题）。不做页内纵向滚动。
 */
export function TableBlock({
  anchorId,
  label,
  title,
  titleText,
  head,
  body,
  footnotes,
}: TableBlockProps) {
  const collapsible = body.length > COLLAPSE_THRESHOLD;
  const [expanded, setExpanded] = useState(false);
  const collapsed = collapsible && !expanded;
  const blockRef = useRef<HTMLDivElement>(null);
  const scroll = useScrollFade<HTMLDivElement>();

  const collapse = () => {
    setExpanded(false);
    blockRef.current?.scrollIntoView?.({ block: "start" });
  };

  return (
    <div ref={blockRef} id={anchorId} className="my-8 scroll-mt-[45px]">
      {/* 表题在表上方（期刊惯例）、不随宽表横滚；读屏从 caption 读到同样的内容 */}
      <div aria-hidden className="mb-3 max-w-[40em]">
        {label && (
          <span className="block font-mono text-2xs font-medium uppercase tracking-caps text-fg-3">
            {label}
          </span>
        )}
        <span className="mt-1 block font-serif text-md leading-normal font-medium text-fg-1">
          {title}
        </span>
      </div>
      <div className="relative overflow-hidden rounded-lg border border-border-default bg-paper-50">
        <div
          ref={scroll.ref}
          data-fade-end={scroll.fadeEnd || undefined}
          className="overflow-x-auto data-fade-end:[mask-image:linear-gradient(to_right,black_calc(100%-40px),transparent)]"
        >
          <table className="ft-table">
            <caption className="sr-only">{[label, titleText].filter(Boolean).join(" ")}</caption>
            {head.length > 0 && (
              <thead>
                {head.map((row, r) => (
                  // biome-ignore lint/suspicious/noArrayIndexKey: 静态表格行（不增删不重排）
                  <tr key={r}>{row.map((c, i) => renderCell(c, i, true))}</tr>
                ))}
              </thead>
            )}
            <tbody>
              {body.map((row, r) => (
                // biome-ignore lint/suspicious/noArrayIndexKey: 静态表格行（不增删不重排）
                <tr key={r} hidden={collapsed && r >= COLLAPSED_ROWS}>
                  {row.map((c, i) => renderCell(c, i, false))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {collapsed && (
          <div
            aria-hidden
            className="pointer-events-none absolute inset-x-0 bottom-0 h-18 bg-linear-to-b from-paper-50/0 to-paper-50"
          />
        )}
      </div>
      {scroll.fadeEnd && (
        <p className="m-0 mt-2 hidden font-mono text-3xs tracking-mono text-fg-3 max-[540px]:block">
          左右滑动查看完整表格
        </p>
      )}
      {collapsible && (
        <div className="mt-3">
          <button
            type="button"
            aria-expanded={expanded}
            onClick={expanded ? collapse : () => setExpanded(true)}
            className={cn(btnSecondary, "px-4 py-1.5 text-sm")}
          >
            {expanded ? "收起" : `展开全部 ${body.length} 行`}
          </button>
        </div>
      )}
      {footnotes && (
        <div className="mt-2.5 max-w-[48em] font-sans text-xs leading-normal text-fg-3 [&_p]:m-0 [&_p+p]:mt-1">
          {footnotes}
        </div>
      )}
    </div>
  );
}
