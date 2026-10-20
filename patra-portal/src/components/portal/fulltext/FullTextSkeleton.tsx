import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { cn } from "@/lib/utils";

function Bar({ className }: { className?: string }) {
  return <div className={cn("shimmer rounded-sm bg-paper-200", className)} />;
}

/** 全文加载中（简报 §3.6）：区块头（统计行占位）+ 三组「标题条 + 4 行段落条」。 */
export function FullTextSkeleton() {
  return (
    <article id="fulltext" aria-label="全文" aria-busy="true" className="scroll-mt-[45px]">
      <SectionEyebrow className="mb-5">全文</SectionEyebrow>
      <div aria-hidden className="flex flex-col">
        <Bar className="mb-4 h-3 w-72 max-w-full" />
        <Bar className="h-11" />
        {[0, 1, 2].map((group) => (
          <div key={group} className="mt-10 flex max-w-[36em] flex-col gap-3">
            <Bar className="mb-2 h-6 w-1/3" />
            <Bar className="h-4" />
            <Bar className="h-4" />
            <Bar className="h-4" />
            <Bar className="h-4 w-2/3" />
          </div>
        ))}
      </div>
    </article>
  );
}

/** 目录卡加载中：5 条骨架条。 */
export function TocSkeleton() {
  return (
    <div aria-busy="true" className="flex flex-col gap-3 py-1">
      {[0, 1, 2, 3, 4].map((i) => (
        <Bar key={i} className={cn("h-3.5", i % 2 === 0 ? "w-3/4" : "w-1/2")} />
      ))}
    </div>
  );
}

/** 桌面目录卡加载中：卡头「目录」+ 5 条骨架条。 */
export function TocCardSkeleton() {
  return (
    <div className="rounded-xl border border-border-default bg-paper-50 p-5">
      <div className="mb-3 font-mono text-2xs uppercase tracking-mono text-fg-3">目录</div>
      <TocSkeleton />
    </div>
  );
}
