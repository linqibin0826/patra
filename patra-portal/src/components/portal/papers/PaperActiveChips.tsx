"use client";

import { ActiveChips } from "@/components/portal/browse/ActiveChips";
import { useBrowseQuery } from "@/components/portal/browse/BrowseQueryProvider";
import { clearAllConditions, derivePaperChips } from "@/lib/portal-api/paper-search";
import { useVenueNamesStore } from "@/store/venue-names";
import type { PaperSearchQuery } from "@/types/portal";

interface PaperActiveChipsProps {
  currentYear: number;
  /** 已选期刊的服务端刊名 */
  venueNames: Readonly<Record<string, string>>;
}

/// 文献页已选条件 chip 行：从乐观 query 推导（操作后即时变化）；期刊刊名依次取服务端名、刚添加的缓存、"期刊 #id"。
export function PaperActiveChips({ currentYear, venueNames }: PaperActiveChipsProps) {
  const { query } = useBrowseQuery<PaperSearchQuery>();
  const rememberedNames = useVenueNamesStore((s) => s.names);
  const chips = derivePaperChips(query, {
    currentYear,
    venueNames: { ...rememberedNames, ...venueNames },
  }).map((chip) => ({
    key: `${chip.group}-${chip.value}`,
    group: chip.group,
    label: chip.label,
    next: chip.next,
  }));
  return <ActiveChips chips={chips} clearAll={clearAllConditions(query)} scrollOnMobile />;
}
