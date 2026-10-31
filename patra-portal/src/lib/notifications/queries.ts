import {
  type QueryClient,
  useInfiniteQuery,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import type {
  NotificationPage,
  NotificationTab,
  UnreadCounts,
} from "@/lib/notification/inbox-schema";
import { queryKeys } from "@/lib/query-keys";

/// 未读数在缓存里的样子：ETag 跟着数存，下一次轮询带成 `If-None-Match`；为 null 时下一次不带（第一次、刚被已读覆盖过）
export interface UnreadData {
  counts: UnreadCounts;
  etag: string | null;
}

/// 轮询间隔：60 秒（release spec 决策 D）
export const POLL_INTERVAL_MS = 60_000;

/// 提醒入口是否该出现、未读数是否该取：已登录，且不是冷静期账号（冷静期会被带去取消注销页）
export function useNotificationsEnabled(): boolean {
  const state = useCurrentUser();
  return state.status === "authenticated" && state.user.status !== "PENDING_DELETION";
}

/// 取一次未读数。浏览器不插手条件请求（`no-store`），JS 看到的就是门户回的真实状态码；304 时原样返回缓存里的那个对象：
/// 同一个引用，读未读数的组件不重新渲染。401 说明会话在页面开着时过期：让当前用户失效，导航栏随之换成「登录」
async function fetchUnread(client: QueryClient): Promise<UnreadData> {
  const previous = client.getQueryData<UnreadData>(queryKeys.notificationUnread);
  const res = await fetch(`/api/me/notifications/unread-count`, {
    cache: "no-store",
    headers: previous?.etag ? { "If-None-Match": previous.etag } : {},
  });
  if (res.status === 304 && previous) {
    return previous;
  }
  if (res.status === 401) {
    void client.invalidateQueries({ queryKey: queryKeys.currentUser });
  }
  if (res.status !== 200) {
    throw new Error(`未读数加载失败：${res.status}`);
  }
  return { counts: (await res.json()) as UnreadCounts, etag: res.headers.get("ETag") };
}

/// 未读数的轮询：只在 TopNav 里调一次（一个计时器、一路请求）。每 60 秒一次，标签页在后台时停；切回页面立即取一次
/// （不管是否过期）；出错不重试，显示不可用，下一次轮询自动恢复。`notifyOnChangeProps: []`：一个属性都不读的观察者
/// TanStack 默认按「任何变化都通知」处理，不写这一项 TopNav 每次轮询都会重渲染
export function useUnreadCountsPolling(): void {
  const client = useQueryClient();
  useQuery({
    queryKey: queryKeys.notificationUnread,
    queryFn: () => fetchUnread(client),
    enabled: useNotificationsEnabled(),
    refetchInterval: POLL_INTERVAL_MS,
    refetchOnWindowFocus: "always",
    retry: false,
    notifyOnChangeProps: [],
  });
}

/// 读未读数（铃铛、菜单项、列表页标签）：同一个 key，不轮询、不聚焦重取，只订阅缓存。`counts` 为 undefined 是还没回来
export function useUnreadCounts(): { counts: UnreadCounts | undefined; unavailable: boolean } {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: queryKeys.notificationUnread,
    queryFn: () => fetchUnread(client),
    enabled: useNotificationsEnabled(),
    select: (data) => data.counts,
    refetchOnWindowFocus: false,
    retry: false,
  });
  return { counts: query.data, unavailable: query.isError };
}

/// 只在出错时随窗口聚焦重取（同 PAP-124 的讨论区）：interaction 恢复后切回页面列表自动恢复
const refetchWhenFailed = (query: { state: { status: string } }): "always" | false =>
  query.state.status === "error" ? "always" : false;

/// 一个标签下的提醒列表（每页 20 条，游标翻页）。每次进页面都先出缓存、后台再取新的；不随未读数变化自动刷新。
/// 401 让当前用户失效（会话在页面开着时过期）
export function useNotificationList(tab: NotificationTab) {
  const client = useQueryClient();
  return useInfiniteQuery({
    queryKey: queryKeys.notificationList(tab),
    queryFn: async ({ pageParam }) => {
      const query = new URLSearchParams({ tab });
      if (pageParam) {
        query.set("cursor", pageParam);
      }
      const res = await fetch(`/api/me/notifications?${query}`);
      if (res.status === 401) {
        void client.invalidateQueries({ queryKey: queryKeys.currentUser });
      }
      if (!res.ok) {
        throw new Error(`提醒列表加载失败：${res.status}`);
      }
      return (await res.json()) as NotificationPage;
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled: useNotificationsEnabled(),
    staleTime: 0,
    retry: false,
    refetchOnWindowFocus: refetchWhenFailed,
  });
}
