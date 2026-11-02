import { TrackedArticle } from "@/components/analytics/TrackedArticle";
import { TrackedList } from "@/components/analytics/TrackedList";
import { LibraryStatusProvider } from "@/components/library/LibraryStatusProvider";
import { PaperStats } from "@/components/library/PaperStats";
import { SaveButton } from "@/components/library/SaveButton";
import { BrowsePagination } from "@/components/portal/browse/BrowsePagination";
import { PaperListItem } from "@/components/portal/papers/PaperListItem";
import { PapersEmptyResult, papersEmptyKind } from "@/components/portal/papers/PapersEmptyResult";
import { newId } from "@/lib/analytics/ids";
import { readPageFacts } from "@/lib/analytics/server/context";
import { isBrowseState, searchPerformedProperties } from "@/lib/analytics/server/search-properties";
import { trackServerEvent } from "@/lib/analytics/server/send";
import { fetchPaperStats } from "@/lib/interaction/stats";
import { fetchPublicationSearch } from "@/lib/portal-api/publication-search";
import type { PageResult, Paper, PaperSearchQuery } from "@/types/portal";

interface PaperResultsProps {
  query: PaperSearchQuery;
  /** 精确定位模式下 page 顶层已查过一次，直接复用 */
  prefetched?: PageResult<Paper>;
}

/**
 * 文献检索结果区（async RSC）：取 search → 列表 + 分页，或空态。
 * 关键词检索时标题高亮命中片段（仅标题，本版不做全文匹配）。每条标题行右端是收藏按钮，状态一屏一批取；
 * 收藏人数与讨论数也一屏一批取（服务端，取不到就没有这一行）。
 */
export async function PaperResults({ query, prefetched }: PaperResultsProps) {
  const page = prefetched ?? (await fetchPublicationSearch(query));
  // 埋点：一次检索一条 Search Performed（含无结果）；无任何条件的浏览不算。searchId 也是这一页结果列表的 list_id
  const searchId = newId();
  if (!isBrowseState(query)) {
    trackServerEvent(
      {
        name: "search_performed",
        properties: searchPerformedProperties(query, searchId, page.total),
      },
      await readPageFacts(),
      { path: "/papers", type: "search" },
    );
  }
  const kind = papersEmptyKind(query, page);
  if (kind) {
    return <PapersEmptyResult kind={kind} query={query} />;
  }

  const keys = page.items.map((paper) => paper.id);
  const stats = await fetchPaperStats(keys);
  return (
    <div className="flex flex-col gap-6">
      <LibraryStatusProvider keys={keys}>
        <TrackedList listType="search" listId={searchId}>
          <ul aria-label="文献列表" className="flex flex-col">
            {page.items.map((paper, i) => (
              <li key={paper.id}>
                <TrackedArticle
                  articleId={paper.id}
                  position={(query.page - 1) * page.pageSize + i + 1}
                >
                  <PaperListItem
                    paper={paper}
                    highlight={query.q || undefined}
                    action={<SaveButton paperKey={paper.id} title={paper.title} variant="icon" />}
                    stats={
                      <PaperStats
                        paperKey={paper.id}
                        stats={stats?.get(paper.id) ?? null}
                        className="mt-1"
                      />
                    }
                  />
                </TrackedArticle>
              </li>
            ))}
          </ul>
        </TrackedList>
      </LibraryStatusProvider>
      <BrowsePagination page={query.page} total={page.total} pageSize={page.pageSize} unit="篇" />
    </div>
  );
}
