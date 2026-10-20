"use client";

import type { MouseEvent } from "react";
import type { TocEntry } from "@/lib/portal-api/fulltext-derive";
import { cn } from "@/lib/utils";

/** 目录尾项的锚点（文末三个区块），按页面顺序。 */
const TAIL_IDS = ["declarations", "references", "record"] as const;

/**
 * 目录列表（桌面目录卡与移动端目录面板共用）：一级章节带序号，当前章节下展开二级标题；
 * 虚线下固定「声明与附注 N 项」「参考文献 N」「文献档案与许可 ↓」（spec §5.15）。
 */
export function TocList({
  entries,
  activeId,
  declarationCount,
  referenceCount,
  large = false,
  onNavigate,
}: {
  entries: TocEntry[];
  activeId: string | null;
  declarationCount: number;
  referenceCount: number;
  /** 移动端面板：触控尺寸更大的行 */
  large?: boolean;
  onNavigate?: (event: MouseEvent<HTMLAnchorElement>, id: string) => void;
}) {
  const current = "aria-[current=location]:font-medium aria-[current=location]:text-fg-1";
  const marker =
    "aria-[current=location]:before:absolute aria-[current=location]:before:inset-y-1.5 aria-[current=location]:before:-left-px aria-[current=location]:before:w-0.5 aria-[current=location]:before:rounded-[1px] aria-[current=location]:before:bg-clay-500";

  const item = (entry: TocEntry, sub = false) => (
    <li key={entry.id}>
      <a
        href={`#${entry.id}`}
        aria-current={activeId === entry.id ? "location" : undefined}
        onClick={onNavigate ? (event) => onNavigate(event, entry.id) : undefined}
        className={cn(
          "relative flex gap-3 py-[7px] pl-3.5 font-sans leading-snug text-fg-3 hover:text-fg-1",
          large ? "py-[11px] text-md" : "text-sm",
          sub && (large ? "pl-7 text-sm" : "pl-7 text-xs"),
          current,
          marker,
        )}
      >
        {entry.number && (
          <span aria-hidden className="shrink-0 pt-px font-mono text-2xs tabular-nums">
            {entry.number}
          </span>
        )}
        {entry.label}
      </a>
    </li>
  );

  const tail = (id: string, label: string, meta: string) => (
    <li key={id}>
      <a
        href={`#${id}`}
        aria-current={activeId === id ? "location" : undefined}
        onClick={onNavigate ? (event) => onNavigate(event, id) : undefined}
        className={cn(
          "relative flex justify-between gap-3 py-[7px] pl-3.5 font-sans leading-snug text-fg-2 hover:text-fg-1",
          large ? "py-[11px] text-md" : "text-sm",
          current,
          marker,
        )}
      >
        <span>{label}</span>{" "}
        <span className="font-mono text-2xs text-fg-3 tabular-nums">{meta}</span>
      </a>
    </li>
  );

  return (
    <ul className="m-0 list-none border-l border-border-subtle p-0">
      {entries.map((entry) => {
        const open = activeId === entry.id || entry.children.some((child) => child.id === activeId);
        return [item(entry), ...(open ? entry.children.map((child) => item(child, true)) : [])];
      })}
      <li aria-hidden className="mt-1.5 border-t border-dashed border-border-subtle" />
      {declarationCount > 0 && tail("declarations", "声明与附注", `${declarationCount} 项`)}
      {referenceCount > 0 && tail("references", "参考文献", `${referenceCount}`)}
      {tail("record", "文献档案与许可", "↓")}
    </ul>
  );
}

/** 目录里所有可高亮的锚点，按文档顺序（章节在前，文末三个区块在后）。 */
export function tocIds(entries: TocEntry[]): string[] {
  return [
    ...entries.flatMap((entry) => [entry.id, ...entry.children.map((child) => child.id)]),
    ...TAIL_IDS,
  ];
}
