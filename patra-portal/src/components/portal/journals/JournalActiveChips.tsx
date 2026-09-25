"use client";

import { ActiveChips } from "@/components/portal/browse/ActiveChips";
import { useBrowseQuery } from "@/components/portal/browse/BrowseQueryProvider";
import { deriveActiveChips, parseVenueBrowseQuery } from "@/lib/portal-api/venue-browse";
import type { VenueBrowseQuery } from "@/types/portal";

/// 清除全部 = 默认查询（回到 /journals，与原行为一致）
const CLEARED = parseVenueBrowseQuery({});

/// 期刊浏览页已选筛选 chip 行：从乐观 query 推导，操作后即时变化。无已选筛选时不渲染。
export function JournalActiveChips() {
  const { query } = useBrowseQuery<VenueBrowseQuery>();
  const chips = deriveActiveChips(query).map((chip) => ({
    key: `${chip.group}-${chip.value}`,
    group: chip.group,
    label: chip.label,
    next: chip.next,
  }));
  return <ActiveChips chips={chips} clearAll={CLEARED} />;
}
