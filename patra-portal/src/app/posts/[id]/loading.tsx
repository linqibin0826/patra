import { TopNav } from "@/components/portal/TopNav";

function Block({ className }: { className?: string }) {
  return <div className={`rounded-lg bg-paper-200 shimmer ${className ?? ""}`} />;
}

/// 经验页加载中：面包屑、主栏（眉题、期刊名、署名、时间线、定义表）与侧栏期刊卡的骨架
export default function Loading() {
  return (
    <>
      <TopNav />
      <main aria-busy="true">
        <div className="border-b border-border-default">
          <div className="mx-auto h-11 max-w-page px-gutter" />
        </div>
        <div
          className="mx-auto grid max-w-page grid-cols-[minmax(0,1fr)_340px] items-start gap-14 px-gutter pt-8 max-[1100px]:gap-10 max-[980px]:grid-cols-1"
          aria-hidden
        >
          <div className="flex min-w-0 flex-col gap-4">
            <Block className="h-4 w-48" />
            <Block className="h-11 w-4/5" />
            <Block className="h-6 w-2/5" />
            <Block className="mt-4 h-24" />
            <Block className="h-16" />
          </div>
          <div className="flex flex-col gap-4 max-[980px]:hidden">
            <Block className="h-44" />
          </div>
        </div>
      </main>
    </>
  );
}
