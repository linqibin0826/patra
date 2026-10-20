"use client";

import { type ReactNode, useId, useRef, useState } from "react";
import { RailRow } from "@/components/portal/RailRow";
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
  head: TableBlockRow[];
  body: TableBlockRow[];
  /** 表注（table-wrap-foot），没有时为 null */
  footnotes: ReactNode;
  /** 各列是否右对齐（`numericColumns` 判出的数字列） */
  numeric?: boolean[];
}

/** 正文行超过这个数才折叠；折叠时显示前 COLLAPSED_ROWS 行（简报 §3.4「长表」）。 */
const COLLAPSE_THRESHOLD = 12;
const COLLAPSED_ROWS = 10;

function renderCell(
  cell: TableBlockCell,
  key: number,
  inHead: boolean,
  isNumeric: boolean,
): ReactNode {
  const span = {
    colSpan: cell.colspan > 1 ? cell.colspan : undefined,
    rowSpan: cell.rowspan > 1 ? cell.rowspan : undefined,
    "data-numeric": isNumeric && cell.colspan === 1 ? "" : undefined,
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

/** 一行单元格按列位渲染：列位按 colspan 累加，数字列的单元格带 `data-numeric`（右对齐）。 */
function renderRow(row: TableBlockRow, inHead: boolean, numeric: boolean[]): ReactNode[] {
  let col = 0;
  return row.map((c, i) => {
    const node = renderCell(c, i, inHead, numeric[col] ?? false);
    col += c.colspan;
    return node;
  });
}

/**
 * 正文表格（spec §5.10）：三线表；表题在上挂标签栏（标签格放原文标签），表注在下；
 * 数字列右对齐；宽表在内容格里横滚、首列吸左、右缘渐隐；
 * 长表只显示前 10 行，「展开全部 N 行」/「收起」（收起时滚回表题）。不做页内纵向滚动。
 */
export function TableBlock({
  anchorId,
  label,
  title,
  head,
  body,
  footnotes,
  numeric = [],
}: TableBlockProps) {
  const collapsible = body.length > COLLAPSE_THRESHOLD;
  const [expanded, setExpanded] = useState(false);
  const collapsed = collapsible && !expanded;
  const blockRef = useRef<HTMLDivElement>(null);
  const scroll = useScrollFade<HTMLDivElement>();
  const titleId = useId();
  const labelId = useId();

  const collapse = () => {
    setExpanded(false);
    blockRef.current?.scrollIntoView?.({ block: "start" });
  };

  return (
    <div ref={blockRef} id={anchorId} className="my-10 scroll-mt-[45px]">
      {/* 表题在表上方（期刊惯例）、不随宽表横滚；表格用 aria-labelledby 以标签 + 表题为名称，
          表题里的引用链接只渲染一次，读屏与键盘都能用 */}
      <RailRow
        className="mb-3.5 items-baseline"
        label={
          label && (
            <span
              id={labelId}
              className="font-mono text-2xs font-medium tracking-mono text-clay-700"
            >
              {label}
            </span>
          )
        }
      >
        <span id={titleId} className="block font-serif text-md leading-normal text-fg-2">
          {title}
        </span>
      </RailRow>
      <RailRow>
        <div className="relative overflow-hidden">
          <div
            ref={scroll.ref}
            data-fade-end={scroll.fadeEnd || undefined}
            className="overflow-x-auto data-fade-end:[mask-image:linear-gradient(to_right,black_calc(100%-40px),transparent)]"
          >
            <table className="ft-table" aria-labelledby={label ? `${labelId} ${titleId}` : titleId}>
              {head.length > 0 && (
                <thead>
                  {head.map((row, r) => (
                    // biome-ignore lint/suspicious/noArrayIndexKey: 静态表格行（不增删不重排）
                    <tr key={r}>{renderRow(row, true, numeric)}</tr>
                  ))}
                </thead>
              )}
              <tbody>
                {body.map((row, r) => (
                  // biome-ignore lint/suspicious/noArrayIndexKey: 静态表格行（不增删不重排）
                  <tr key={r} hidden={collapsed && r >= COLLAPSED_ROWS}>
                    {renderRow(row, false, numeric)}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {collapsed && (
            <div
              aria-hidden
              className="pointer-events-none absolute inset-x-0 bottom-0 h-18 bg-linear-to-b from-bg-canvas/0 to-bg-canvas"
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
      </RailRow>
    </div>
  );
}
