import { QueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query-keys";

/// 当前用户的标识：已登录是用户 ID，未登录是 null，还没取回是 undefined
type UserIdentity = string | null | undefined;

/// 读当前用户缓存里的用户 ID
function identityOf(data: unknown): UserIdentity {
  if (data === undefined) {
    return undefined;
  }
  if (data === null || typeof data !== "object" || !("userId" in data)) {
    return null;
  }
  return String((data as { userId: unknown }).userId);
}

/// 当前用户一变（退出、换号、注销、会话失效）就清掉按用户区分的缓存：文献库与「我的状态」的 key 不带用户 ID，
/// 留着会在 60 秒内把上一个人的收藏、收藏夹名、备注给下一个人看；讨论区的「这条是我发的」、联想里排除自己同理。
/// 第一次取回当前用户之前的缓存不动。
function clearUserScopedCacheOnIdentityChange(client: QueryClient) {
  let known: UserIdentity;
  client.getQueryCache().subscribe((event) => {
    if (event.type !== "updated" || event.query.queryKey[0] !== queryKeys.currentUser[0]) {
      return;
    }
    const next = identityOf(event.query.state.data);
    if (next === undefined || next === known) {
      return;
    }
    const previous = known;
    known = next;
    if (previous === undefined) {
      return;
    }
    client.removeQueries({ queryKey: queryKeys.library });
    client.removeQueries({ queryKey: queryKeys.libraryStatusAll });
    client.removeQueries({ queryKey: queryKeys.discussion });
    client.removeQueries({ queryKey: queryKeys.notifications });
    client.removeQueries({ queryKey: queryKeys.experience });
  });
}

/// 门户的 QueryClient：数据 60 秒内不过期、聚焦不重取、失败重试一次；当前用户变化时清掉按用户区分的缓存
export function createQueryClient(): QueryClient {
  const client = new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000,
        refetchOnWindowFocus: false,
        retry: 1,
      },
    },
  });
  clearUserScopedCacheOnIdentityChange(client);
  return client;
}
