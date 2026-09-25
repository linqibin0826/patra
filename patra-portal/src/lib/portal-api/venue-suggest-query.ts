import { useQuery } from "@tanstack/react-query";
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
