import { redirect } from "next/navigation";
import { Suspense } from "react";
import { BrowseHead } from "@/components/portal/browse/BrowseHead";
import { FacetSkeleton } from "@/components/portal/browse/FacetSkeleton";
import { PendingRegion } from "@/components/portal/browse/PendingRegion";
import { Footer } from "@/components/portal/Footer";
import { PaperActiveChipsServer } from "@/components/portal/papers/PaperActiveChipsServer";
import { PaperFiltersServer } from "@/components/portal/papers/PaperFiltersServer";
import { PaperListSkeleton } from "@/components/portal/papers/PaperListSkeleton";
import { PaperResults } from "@/components/portal/papers/PaperResults";
import { PaperSearchBar } from "@/components/portal/papers/PaperSearchBar";
import { PapersInfoRow } from "@/components/portal/papers/PapersInfoRow";
import { PapersQueryProvider } from "@/components/portal/papers/PapersQueryProvider";
import { TopNav } from "@/components/portal/TopNav";
import {
  currentPortalYear,
  isExactLookup,
  parsePaperSearchQuery,
  serializePaperSearchQuery,
} from "@/lib/portal-api/paper-search";
import { fetchPublicationSearch } from "@/lib/portal-api/publication-search";
import type { PageResult, Paper } from "@/types/portal";

export default async function PapersPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const query = parsePaperSearchQuery(await searchParams);
  const currentYear = currentPortalYear();

  // 精确定位（PMID / DOI）：先查一次，实际返回 1 条就直跳详情；未命中时复用这次结果渲染空态。
  // app/papers/loading.tsx 使直开链接先输出骨架再由 Next 注入跳转（非 307），接受（spec 决策 7）。
  let prefetched: PageResult<Paper> | undefined;
  if (isExactLookup(query)) {
    prefetched = await fetchPublicationSearch(query);
    const [only] = prefetched.items;
    if (prefetched.items.length === 1 && only) {
      redirect(`/papers/${only.id}`);
    }
  }

  return (
    <>
      <TopNav />
      <main>
        <div className="mx-auto max-w-[1200px] px-6 py-8">
          <PapersQueryProvider query={query}>
            <BrowseHead
              crumb="文献浏览"
              eyebrow="按文献浏览"
              title="浏览全部文献"
              description="Patra 收录的医学文献——按关键词、PMID、DOI 或作者检索，按年份 / 文献类型 / 证据等级 / 期刊收敛，点任一篇查看完整元信息与摘要。"
            />
            <div className="mt-6 flex flex-col gap-3">
              <PaperSearchBar />
              <Suspense
                fallback={
                  <div aria-hidden="true" className="h-5 w-56 animate-pulse rounded bg-paper-200" />
                }
              >
                <PapersInfoRow query={query} />
              </Suspense>
              <Suspense fallback={null}>
                <PaperActiveChipsServer query={query} currentYear={currentYear} />
              </Suspense>
            </div>
            {/* 两栏：桌面 facet 侧栏 + 结果区 */}
            <div className="mt-6 flex items-start gap-8">
              {/* facet：无 key——导航时保留旧面板平滑换计数，移动抽屉不重挂 */}
              <Suspense fallback={<FacetSkeleton className="hidden w-60 shrink-0 md:flex" />}>
                <PaperFiltersServer query={query} currentYear={currentYear} />
              </Suspense>
              {/* 结果区：导航进行中先变淡；带 key——任意 query 变（含翻页）出列表骨架 */}
              <div className="min-w-0 flex-1">
                <PendingRegion>
                  <Suspense key={serializePaperSearchQuery(query)} fallback={<PaperListSkeleton />}>
                    <PaperResults query={query} prefetched={prefetched} />
                  </Suspense>
                </PendingRegion>
              </div>
            </div>
          </PapersQueryProvider>
        </div>
      </main>
      <Footer />
    </>
  );
}
