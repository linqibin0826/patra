import { JournalCoverCard } from "@/components/portal/JournalCoverCard";
import type { VenueBrowse } from "@/types/portal";

interface JournalGridProps {
  items: VenueBrowse[];
}

/// 期刊网格列数：移动 2 列 → 平板 3 列 → 宽屏 4 列（骨架共用，保证同构）
export const JOURNAL_GRID =
  "grid list-none grid-cols-2 gap-x-5 gap-y-10 p-0 sm:grid-cols-3 xl:grid-cols-4";

/**
 * 期刊浏览网格（RSC 纯渲染）：封面卡排成书架。
 */
export function JournalGrid({ items }: JournalGridProps) {
  return (
    <ul className={JOURNAL_GRID}>
      {items.map((journal) => (
        <li key={journal.id} className="flex">
          <JournalCoverCard journal={journal} className="w-full" />
        </li>
      ))}
    </ul>
  );
}
