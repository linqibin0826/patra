"use client";

import type { MouseEvent } from "react";
import type { TocEntry } from "@/lib/portal-api/fulltext-derive";
import { cn } from "@/lib/utils";

/** 目录列表（桌面目录卡与移动端目录面板共用）：一级章节，当前章节下展开二级标题，末尾固定参考文献、许可与出处。 */
export function TocList({
  entries,
  activeId,
  hasReferences,
  large = false,
  onNavigate,
}: {
  entries: TocEntry[];
  activeId: string | null;
  hasReferences: boolean;
  /** 移动端面板：触控尺寸更大的行 */
  large?: boolean;
  onNavigate?: (event: MouseEvent<HTMLAnchorElement>, id: string) => void;
}) {
  const item = (id: string, label: string, sub = false) => (
    <li key={id}>
      <a
        href={`#${id}`}
        aria-current={activeId === id ? "location" : undefined}
        onClick={onNavigate ? (event) => onNavigate(event, id) : undefined}
        className={cn(
          "relative block py-[7px] pl-3.5 font-sans leading-snug text-fg-3 hover:text-fg-1",
          large ? "py-[11px] text-md" : "text-sm",
          sub && (large ? "pl-7 text-sm" : "pl-7 text-xs"),
          "aria-[current=location]:font-medium aria-[current=location]:text-fg-1",
          "aria-[current=location]:before:absolute aria-[current=location]:before:inset-y-1.5 aria-[current=location]:before:-left-px aria-[current=location]:before:w-0.5 aria-[current=location]:before:rounded-[1px] aria-[current=location]:before:bg-clay-500",
        )}
      >
        {label}
      </a>
    </li>
  );

  return (
    <ul className="m-0 list-none border-l border-border-subtle p-0">
      {entries.map((entry) => {
        const open = activeId === entry.id || entry.children.some((child) => child.id === activeId);
        return [
          item(entry.id, entry.label),
          ...(open ? entry.children.map((child) => item(child.id, child.label, true)) : []),
        ];
      })}
      <li aria-hidden className="mt-1.5 border-t border-dashed border-border-subtle" />
      {hasReferences && item("references", "参考文献")}
      {item("license", "许可与出处")}
    </ul>
  );
}

/** 目录里所有可高亮的锚点，按文档顺序。 */
export function tocIds(entries: TocEntry[]): string[] {
  return entries.flatMap((entry) => [entry.id, ...entry.children.map((child) => child.id)]);
}
