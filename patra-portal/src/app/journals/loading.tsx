import { FacetSkeleton } from "@/components/portal/browse/FacetSkeleton";
import { JournalGridSkeleton } from "@/components/portal/journals/JournalGridSkeleton";
import { TopNav } from "@/components/portal/TopNav";

function Block({ className }: { className?: string }) {
  return <div className={`animate-pulse rounded-lg bg-paper-200 ${className ?? ""}`} />;
}

export default function Loading() {
  return (
    <>
      <TopNav />
      <main aria-busy="true">
        <div className="mx-auto max-w-[1200px] px-6 py-8">
          {/* Head 占位 */}
          <div className="border-b border-border-default pb-6">
            <div className="mb-4 flex items-center gap-1.5">
              <Block className="h-3 w-10" />
              <Block className="h-3 w-3" />
              <Block className="h-3 w-16" />
            </div>
            <Block className="h-3 w-20" />
            <Block className="mt-2 h-9 w-64" />
            <Block className="mt-2 h-4 w-96 max-w-full" />
          </div>

          {/* 检索条占位 */}
          <div className="mt-6 flex flex-col gap-3" aria-hidden>
            <Block className="h-9 w-full" />
            <div className="flex gap-2">
              <Block className="h-8 w-20" />
              <Block className="h-8 w-20" />
              <Block className="h-8 w-20" />
              <Block className="h-8 w-20" />
            </div>
          </div>

          {/* 两栏占位 */}
          <div className="mt-6 flex items-start gap-8" aria-hidden>
            <div className="hidden md:block w-56 shrink-0">
              <FacetSkeleton />
            </div>
            <div className="min-w-0 flex-1">
              <JournalGridSkeleton />
            </div>
          </div>
        </div>
      </main>
    </>
  );
}
