import { Suspense } from "react";
import { BrowseHead } from "@/components/portal/browse/BrowseHead";
import { FacetSkeleton } from "@/components/portal/browse/FacetSkeleton";
import { PendingRegion } from "@/components/portal/browse/PendingRegion";
import { Footer } from "@/components/portal/Footer";
import { JournalActiveChips } from "@/components/portal/journals/JournalActiveChips";
import { JournalFiltersServer } from "@/components/portal/journals/JournalFiltersServer";
import { JournalGridSkeleton } from "@/components/portal/journals/JournalGridSkeleton";
import { JournalResults } from "@/components/portal/journals/JournalResults";
import { JournalSearchSortControls } from "@/components/portal/journals/JournalSearchSortControls";
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
            <BrowseHead
              crumb="期刊浏览"
              eyebrow="按期刊浏览"
              title="浏览全部期刊"
              description="Patra 追踪的同行评审期刊——按刊名检索，按影响因子 / 中科院分区 / 被引排序，按学科与分区收敛。"
            />
            <div className="mt-6 flex flex-col gap-4">
              <JournalSearchSortControls />
              <JournalActiveChips />
            </div>
            {/* 两栏：桌面 filter 侧栏 + 结果区 */}
            <div className="mt-6 flex items-start gap-8">
              {/* 筛选面板：无 key——导航时保留旧面板平滑换计数，移动 sheet 不重挂 */}
              <Suspense fallback={<FacetSkeleton />}>
                <JournalFiltersServer query={query} />
              </Suspense>
              {/* 结果区：导航进行中先变淡；带 key——任意 query 变（含翻页）强制出骨架网格 */}
              <div className="min-w-0 flex-1">
                <PendingRegion>
                  <Suspense
                    key={serializeVenueBrowseQuery(query)}
                    fallback={<JournalGridSkeleton />}
                  >
                    <JournalResults query={query} />
                  </Suspense>
                </PendingRegion>
              </div>
            </div>
          </JournalsQueryProvider>
        </div>
      </main>
      <Footer />
    </>
  );
}
