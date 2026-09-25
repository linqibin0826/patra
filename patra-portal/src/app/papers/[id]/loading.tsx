import { TopNav } from "@/components/portal/TopNav";

function Block({ className }: { className?: string }) {
  return <div className={`animate-pulse rounded-lg bg-paper-200 ${className ?? ""}`} />;
}

export default function Loading() {
  return (
    <>
      <TopNav />
      <main aria-busy="true">
        <div className="border-b border-border-default">
          <div className="mx-auto h-11 max-w-[1200px] px-6" />
        </div>
        <div
          className="mx-auto grid max-w-[1200px] grid-cols-[minmax(0,1fr)_340px] items-start gap-10 px-6 pt-8 max-[980px]:grid-cols-1"
          aria-hidden
        >
          <div className="flex min-w-0 flex-col gap-7">
            <div className="flex flex-col gap-3">
              <Block className="h-5 w-24" />
              <Block className="h-9 w-11/12" />
              <Block className="h-9 w-2/3" />
              <Block className="mt-2 h-4 w-3/4" />
              <Block className="mt-2 h-9 w-48" />
            </div>
            <Block className="h-32" />
            <Block className="h-16" />
            <Block className="h-16" />
            <Block className="h-16" />
          </div>
          <div className="flex flex-col gap-4 max-[980px]:hidden">
            <Block className="h-36" />
            <Block className="h-36" />
            <Block className="h-44" />
          </div>
        </div>
      </main>
    </>
  );
}
