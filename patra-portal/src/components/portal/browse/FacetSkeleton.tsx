import { cn } from "@/lib/utils";

const BLOCK = "rounded-sm bg-paper-200 shimmer";

/**
 * 筛选侧栏骨架占位（RSC 纯渲染）：几组标题 + 若干行（纸面微光），与 facet 侧栏布局对齐。
 * 默认与 FilterPanel 侧栏一致：窄屏隐藏（筛选收进抽屉）、md 起显示、宽 w-56；文献页传 `w-60` 覆盖。
 */
export function FacetSkeleton({ className }: { className?: string }) {
  const groups = [4, 5, 3, 4];

  return (
    <aside aria-hidden="true" className={cn("hidden w-56 shrink-0 flex-col md:flex", className)}>
      {groups.map((count, groupIdx) => (
        <div
          // biome-ignore lint/suspicious/noArrayIndexKey: 骨架占位无业务 key，index 是正确选择
          key={groupIdx}
          className="flex flex-col gap-3 border-b border-border-subtle py-4 last:border-0"
        >
          <div className={`h-4 w-1/2 ${BLOCK}`} />
          {Array.from({ length: count }).map((_, rowIdx) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: 骨架占位无业务 key，index 是正确选择
            <div key={rowIdx} className="flex items-center gap-2.5">
              <div className={`size-4 ${BLOCK}`} />
              <div className={`h-3.5 flex-1 ${BLOCK}`} />
            </div>
          ))}
        </div>
      ))}
    </aside>
  );
}
