import { BrowsePagination } from "@/components/portal/browse/BrowsePagination";
import { JournalEmptyResult } from "@/components/portal/journals/JournalEmptyResult";
import { JournalGrid } from "@/components/portal/journals/JournalGrid";
import { fetchVenuesPage } from "@/lib/portal-api/venues";
import type { VenueBrowseQuery } from "@/types/portal";

/**
 * 期刊浏览结果区（async RSC）。
 * 根据 query 拉取分页数据，渲染命中数 + 网格 + 分页，或空态。
 */
export async function JournalResults({ query }: { query: VenueBrowseQuery }) {
  const page = await fetchVenuesPage(query);

  const hasActiveFilters =
    query.q !== "" ||
    query.subject.length > 0 ||
    query.jcr.length > 0 ||
    query.cas.length > 0 ||
    query.country.length > 0 ||
    query.casTop ||
    query.oa ||
    query.doaj;

  if (page.total === 0) {
    return (
      <JournalEmptyResult kind={hasActiveFilters ? "no-results" : "empty-library"} query={query} />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <p className="text-sm text-[var(--fg-3)]">共 {page.total} 本期刊</p>
      <JournalGrid items={page.items} />
      <BrowsePagination page={query.page} total={page.total} pageSize={page.pageSize} unit="本" />
    </div>
  );
}
