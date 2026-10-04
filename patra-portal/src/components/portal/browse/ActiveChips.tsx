"use client";

import { XIcon } from "lucide-react";
import {
  type BrowseQueryBase,
  useBrowseQuery,
} from "@/components/portal/browse/BrowseQueryProvider";
import { cn } from "@/lib/utils";

export interface ChipItem<Q> {
  key: string;
  group: string;
  label: string;
  /** 移除该 chip 后的查询 */
  next: Q;
}

interface ActiveChipsProps<Q> {
  chips: ChipItem<Q>[];
  /** "清除全部"的目标查询 */
  clearAll: Q;
  /** 移动端单行横向滚动（文献页）；默认换行（期刊页） */
  scrollOnMobile?: boolean;
}

/// 已选条件 chip 行：单个移除 / 清除全部，经导航钩子跳转。无 chip 时不渲染。
/// 每个 chip 以入场淡入出现，移除按钮 hover 时 × 旋转 90°。
export function ActiveChips<Q extends BrowseQueryBase>({
  chips,
  clearAll,
  scrollOnMobile = false,
}: ActiveChipsProps<Q>) {
  const { navigate } = useBrowseQuery<Q>();
  if (chips.length === 0) return null;

  return (
    <ul
      aria-label="已选筛选条件"
      className={cn(
        "flex flex-wrap items-center gap-2",
        scrollOnMobile && "max-md:flex-nowrap max-md:overflow-x-auto max-md:pb-1",
      )}
    >
      {chips.map((chip) => (
        <li
          key={chip.key}
          className="anim-fade inline-flex shrink-0 items-center gap-2 rounded-full border border-clay-200 bg-clay-50 py-1 pr-1 pl-3 text-xs font-medium text-clay-800 [--dur-reveal:400ms]"
        >
          <span className="font-mono text-3xs uppercase tracking-mono text-clay-700">
            {chip.group}
          </span>
          <span className="max-w-[16rem] truncate">{chip.label}</span>
          <button
            type="button"
            aria-label={`移除 ${chip.label}`}
            onClick={() => navigate(chip.next)}
            className="group/x flex size-5 items-center justify-center rounded-full text-clay-700 transition-colors hover:bg-clay-100 hover:text-clay-900"
          >
            <XIcon className="size-3 transition-transform duration-300 ease-out-expo motion-safe:group-hover/x:rotate-90" />
          </button>
        </li>
      ))}
      <li className="shrink-0">
        <button
          type="button"
          onClick={() => navigate(clearAll)}
          className="link-draw ml-1 pb-px text-xs text-fg-2 transition-colors hover:text-clay-700"
        >
          清除全部
        </button>
      </li>
    </ul>
  );
}
