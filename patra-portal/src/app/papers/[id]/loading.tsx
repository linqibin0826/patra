import { TopNav } from "@/components/portal/TopNav";

function Block({ className }: { className?: string }) {
  return <div className={`rounded-lg bg-paper-200 shimmer ${className ?? ""}`} />;
}

export default function Loading() {
  return (
    <>
      <TopNav />
      <main aria-busy="true">
        <div className="border-b border-border-default">
          <div className="mx-auto h-11 max-w-page px-gutter" />
        </div>
        <div className="mx-auto max-w-page px-gutter pt-14 max-[980px]:pt-8" aria-hidden>
          <div className="flex flex-col gap-3">
            <Block className="h-5 w-24" />
            <Block className="h-11 w-11/12 max-w-[20em]" />
            <Block className="h-11 w-2/3 max-w-[14em]" />
            <Block className="mt-2 h-6 w-3/4 max-w-[34em]" />
            <Block className="mt-2 h-4 w-1/2" />
          </div>
          <Block className="mt-8 h-16" />
          <Block className="mt-6 h-11 w-80 max-w-full" />
        </div>
        <div
          className="mx-auto grid max-w-page grid-cols-[minmax(0,1fr)_340px] items-start gap-14 px-gutter pt-16 max-[1100px]:gap-10 max-[980px]:grid-cols-1"
          aria-hidden
        >
          <div className="@container/main flex min-w-0 flex-col gap-6">
            <Block className="h-4 w-32" />
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="grid grid-cols-[128px_minmax(0,1fr)] gap-x-8 gap-y-2 @max-[580px]/main:grid-cols-1"
              >
                <Block className="h-4 w-20" />
                <Block className="h-24 max-w-[36em]" />
              </div>
            ))}
          </div>
          <div className="flex flex-col gap-4 max-[980px]:hidden">
            <Block className="h-40" />
            <Block className="h-36" />
          </div>
        </div>
      </main>
    </>
  );
}
