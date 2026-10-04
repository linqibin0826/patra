import { JOURNAL_GRID } from "@/components/portal/journals/JournalGrid";

const BLOCK = "rounded-sm bg-paper-200 shimmer";

/**
 * 期刊网格骨架占位（RSC 纯渲染）：与 JournalGrid 同布局，12 张封面卡形占位（纸面微光）。
 */
export function JournalGridSkeleton() {
  return (
    <div aria-hidden="true" className={JOURNAL_GRID}>
      {Array.from({ length: 12 }).map((_, i) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: 骨架占位无业务 key，index 是正确选择
        <div key={i} className="flex flex-col">
          <div className={`aspect-[3/4] ${BLOCK}`} />
          <div className="mt-4 flex flex-col gap-2">
            <div className={`h-3.5 w-full ${BLOCK}`} />
            <div className={`h-3.5 w-2/3 ${BLOCK}`} />
            <div className={`mt-2 h-6 w-1/3 ${BLOCK}`} />
          </div>
        </div>
      ))}
    </div>
  );
}
