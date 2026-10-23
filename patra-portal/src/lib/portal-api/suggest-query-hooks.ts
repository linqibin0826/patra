import { useQuery } from "@tanstack/react-query";
import type { MeshSuggestion, OrganizationSuggestion } from "@/lib/portal-api/suggest";
import { queryKeys } from "@/lib/query-keys";

/// 请求门户自己的建议接口 `/api/{endpoint}/suggest`，取 `items`；非 2xx 时抛错
async function fetchItems<T>(endpoint: "organizations" | "mesh", q: string): Promise<T[]> {
  const res = await fetch(`/api/${endpoint}/suggest?q=${encodeURIComponent(q)}`);
  if (!res.ok) {
    throw new Error(`候选加载失败：${res.status}`);
  }
  return ((await res.json()) as { items: T[] }).items;
}

/// 机构候选（浏览器端，经 `/api/organizations/suggest`）：`q` 是已归一、够长的输入，空串时不请求
export function useOrganizationSuggestQuery(q: string) {
  return useQuery({
    queryKey: queryKeys.organizationSuggest(q),
    queryFn: () => fetchItems<OrganizationSuggestion>("organizations", q),
    enabled: q.length > 0,
  });
}

/// MeSH 主题词候选（浏览器端，经 `/api/mesh/suggest`）：`q` 为空串时不请求
export function useMeshSuggestQuery(q: string) {
  return useQuery({
    queryKey: queryKeys.meshSuggest(q),
    queryFn: () => fetchItems<MeshSuggestion>("mesh", q),
    enabled: q.length > 0,
  });
}
