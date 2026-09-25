import { PaperActiveChips } from "@/components/portal/papers/PaperActiveChips";
import { fetchVenueTitles } from "@/lib/portal-api/venues";
import type { PaperSearchQuery } from "@/types/portal";

/**
 * 文献 chip 行服务端包装（async RSC）：查已选期刊刊名（与筛选栏共用 React cache）后交给 client chip 行。
 */
export async function PaperActiveChipsServer({
  query,
  currentYear,
}: {
  query: PaperSearchQuery;
  currentYear: number;
}) {
  const venueNames = await fetchVenueTitles(query.venue);
  return <PaperActiveChips currentYear={currentYear} venueNames={venueNames} />;
}
