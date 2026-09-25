import { BrowsePagination } from "@/components/portal/browse/BrowsePagination";
import { PaperListItem } from "@/components/portal/papers/PaperListItem";
import { PapersEmptyResult, papersEmptyKind } from "@/components/portal/papers/PapersEmptyResult";
import { fetchPublicationSearch } from "@/lib/portal-api/publication-search";
import type { PageResult, Paper, PaperSearchQuery } from "@/types/portal";

interface PaperResultsProps {
  query: PaperSearchQuery;
  /** 精确定位模式下 page 顶层已查过一次，直接复用 */
  prefetched?: PageResult<Paper>;
}

/**
 * 文献检索结果区（async RSC）：取 search → 列表 + 分页，或空态。
 * 关键词检索时标题高亮命中片段（仅标题，本版不做全文匹配）。
 */
export async function PaperResults({ query, prefetched }: PaperResultsProps) {
  const page = prefetched ?? (await fetchPublicationSearch(query));
  const kind = papersEmptyKind(query, page);
  if (kind) {
    return <PapersEmptyResult kind={kind} query={query} />;
  }

  return (
    <div className="flex flex-col gap-6">
      <ul aria-label="文献列表" className="flex flex-col">
        {page.items.map((paper) => (
          <li key={paper.id}>
            <PaperListItem paper={paper} highlight={query.q || undefined} />
          </li>
        ))}
      </ul>
      <BrowsePagination page={query.page} total={page.total} pageSize={page.pageSize} unit="篇" />
    </div>
  );
}
