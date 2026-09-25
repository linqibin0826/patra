import { FacetSkeleton } from "@/components/portal/browse/FacetSkeleton";
import { PaperListSkeleton } from "@/components/portal/papers/PaperListSkeleton";
import { TopNav } from "@/components/portal/TopNav";

function Block({ className }: { className?: string }) {
  return <div className={`animate-pulse rounded-lg bg-paper-200 ${className ?? ""}`} />;
}

/** /papers 首次进入的整页骨架（页头 / 检索条 / facet / 列表）。 */
export default function Loading() {
  return (
    <>
      <TopNav />
      <main aria-busy="true">
        <div className="mx-auto max-w-[1200px] px-6 py-8">
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

          <div className="mt-6 flex flex-col gap-3" aria-hidden>
            <div className="flex gap-1">
              <Block className="h-7 w-20" />
              <Block className="h-7 w-14" />
              <Block className="h-7 w-12" />
              <Block className="h-7 w-12" />
            </div>
            <Block className="h-11 w-full" />
            <Block className="h-5 w-56" />
          </div>

          <div className="mt-6 flex items-start gap-8" aria-hidden>
            <FacetSkeleton className="hidden w-60 shrink-0 md:flex" />
            <div className="min-w-0 flex-1">
              <PaperListSkeleton />
            </div>
          </div>
        </div>
      </main>
    </>
  );
}
