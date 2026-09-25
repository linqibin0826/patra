import { PaperFilters } from "@/components/portal/papers/PaperFilters";
import { fetchPublicationFacets } from "@/lib/portal-api/publication-search";
import { fetchVenueTitles } from "@/lib/portal-api/venues";
import type { PaperSearchQuery } from "@/types/portal";

/**
 * 文献 facet 栏服务端包装（async RSC）：并行取 facets（与信息行共用一次请求）与已选期刊刊名。
 */
export async function PaperFiltersServer({
  query,
  currentYear,
}: {
  query: PaperSearchQuery;
  currentYear: number;
}) {
  const [facets, venueNames] = await Promise.all([
    fetchPublicationFacets(query),
    fetchVenueTitles(query.venue),
  ]);
  return <PaperFilters facets={facets} currentYear={currentYear} venueNames={venueNames} />;
}
