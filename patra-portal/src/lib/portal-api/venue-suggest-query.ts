import { useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query-keys";
import type { VenueSuggestion } from "@/types/portal";

async function fetchVenueSuggest(q: string): Promise<VenueSuggestion[]> {
  const res = await fetch(`/api/venues/suggest?q=${encodeURIComponent(q)}`);
  if (!res.ok) {
    throw new Error(`期刊候选加载失败：${res.status}`);
  }
  return (await res.json()) as VenueSuggestion[];
}

/// 期刊候选查询（浏览器端，经 `/api/venues/suggest` 中转）：q 为空时不请求，按 q 缓存。
export function useVenueSuggestQuery(q: string) {
  return useQuery({
    queryKey: queryKeys.venueSuggest(q),
    queryFn: () => fetchVenueSuggest(q),
    enabled: q.length > 0,
  });
}

/// 已缓存候选里的刊名（id → 刊名）：刚从候选添加的期刊在服务端刊名到达前，靠它即时显示刊名。
/// 选中时候选结果必然在缓存中；导航完成后服务端刊名接管，缓存过期后回退也不受影响。
export function useSuggestedVenueNames(): Record<string, string> {
  const client = useQueryClient();
  const names: Record<string, string> = {};
  for (const [, data] of client.getQueriesData<VenueSuggestion[]>({
    queryKey: queryKeys.venueSuggestAll,
  })) {
    for (const venue of data ?? []) {
      names[venue.id] = venue.name;
    }
  }
  return names;
}
