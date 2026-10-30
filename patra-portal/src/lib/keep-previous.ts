import type { QueryClient } from "@tanstack/react-query";

/// `placeholderData` 的判断：换 key（切排序）时先留着上一份，但上一份所在的查询已被移出缓存（当前用户变了，按用户
/// 区分的缓存被清掉，`query-client.ts`）就不留：否则会把上一位访客的「我点过没有」「这条是我的」当占位显示、甚至被点
export function keepPreviousWhileCached<T>(
  client: QueryClient,
  previous: T | undefined,
  previousQuery: { queryHash: string } | undefined,
): T | undefined {
  return previousQuery && client.getQueryCache().get(previousQuery.queryHash) === previousQuery
    ? previous
    : undefined;
}
