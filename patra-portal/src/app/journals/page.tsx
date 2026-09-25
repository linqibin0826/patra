import { Suspense } from "react";
import { Footer } from "@/components/portal/Footer";
import { JournalActiveChips } from "@/components/portal/journals/JournalActiveChips";
import { JournalFilterSkeleton } from "@/components/portal/journals/JournalFilterSkeleton";
import { JournalFiltersServer } from "@/components/portal/journals/JournalFiltersServer";
import { JournalGridSkeleton } from "@/components/portal/journals/JournalGridSkeleton";
import { JournalResults } from "@/components/portal/journals/JournalResults";
import { JournalSearchSortControls } from "@/components/portal/journals/JournalSearchSortControls";
import { JournalsBrowseHead } from "@/components/portal/journals/JournalsBrowseHead";
import { JournalsQueryProvider } from "@/components/portal/journals/JournalsQueryProvider";
import { TopNav } from "@/components/portal/TopNav";
import { parseVenueBrowseQuery, serializeVenueBrowseQuery } from "@/lib/portal-api/venue-browse";

export default async function JournalsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const query = parseVenueBrowseQuery(await searchParams);

  return (
    <>
      <TopNav />
      <main>
        <div className="mx-auto max-w-[1200px] px-6 py-8">
          <JournalsQueryProvider query={query}>
            <JournalsBrowseHead />
            <div className="mt-6 flex flex-col gap-4">
              <JournalSearchSortControls />
              <JournalActiveChips />
            </div>
            {/* 两栏：桌面 filter 侧栏 + 结果区 */}
            <div className="mt-6 flex items-start gap-8">
              {/* 筛选面板：无 key——导航时保留旧面板平滑换计数，移动 sheet 不重挂 */}
              <Suspense fallback={<JournalFilterSkeleton />}>
                <JournalFiltersServer query={query} />
              </Suspense>
              {/* 结果区：带 key——任意 query 变（含翻页）强制出骨架网格 */}
              <div className="min-w-0 flex-1">
                <Suspense key={serializeVenueBrowseQuery(query)} fallback={<JournalGridSkeleton />}>
                  <JournalResults query={query} />
                </Suspense>
              </div>
            </div>
          </JournalsQueryProvider>
        </div>
      </main>
      <Footer />
    </>
  );
}
