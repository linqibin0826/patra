import { JournalFilters } from "@/components/portal/journals/JournalFilters";
import { toFilters } from "@/lib/portal-api/venue-browse";
import { fetchVenuesFacets } from "@/lib/portal-api/venues";
import type { VenueBrowseQuery } from "@/types/portal";

/**
 * 期刊筛选面板服务端包装（async RSC）。
 * 拉取 facets 后渲染客户端 JournalFilters。
 */
export async function JournalFiltersServer({ query }: { query: VenueBrowseQuery }) {
  const facets = await fetchVenuesFacets(toFilters(query));
  return <JournalFilters facets={facets} />;
}
