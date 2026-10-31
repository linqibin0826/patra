import { type InfiniteData, type QueryClient, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { type NotificationPage, unreadCountsSchema } from "@/lib/notification/inbox-schema";
import { fetchUnread, type UnreadData, useNotificationsEnabled } from "@/lib/notifications/queries";
import { queryKeys } from "@/lib/query-keys";
import { useFreshNotifications } from "@/store/fresh-notifications";
import { useNotificationStreamStore } from "@/store/notification-stream";

/// 门户的推送地址（Route Handler 带上会话令牌连网关的事件流）
export const STREAM_URL = "/api/me/notifications/stream";
/// 拿到非 200 之后多久再试推送（页面可见时）；同轮询间隔
export const STREAM_RETRY_MS = 60_000;

/// 一条 `unread-count` 事件的数据：与未读数接口同形，不对就是 null
function parseCounts(data: unknown) {
  try {
    const parsed = unreadCountsSchema.safeParse(JSON.parse(String(data)));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

/// 开着的、已有数据、眼下没在请求的提醒列表（就是提醒页正在看的那个标签）。正在请求的不打断：首次加载、或用户刚点了
/// 「加载更多」（重取会取消翻页，这次点击就白点了）；下一条推送会补上
const activeLists = {
  queryKey: queryKeys.notificationListAll,
  type: "active",
  predicate: (query: { state: { data: unknown; fetchStatus: string } }) =>
    query.state.data !== undefined && query.state.fetchStatus === "idle",
} as const;

/// 列表里每条提醒的 ID → 排序时间
function occurrences(client: QueryClient): Map<string, { occurredAt: string; read: boolean }> {
  const seen = new Map<string, { occurredAt: string; read: boolean }>();
  for (const query of client.getQueryCache().findAll(activeLists)) {
    const data = query.state.data as InfiniteData<NotificationPage> | undefined;
    for (const item of data?.pages.flatMap((page) => page.items) ?? []) {
      if (!seen.has(item.notificationId)) {
        seen.set(item.notificationId, { occurredAt: item.occurredAt, read: item.read });
      }
    }
  }
  return seen;
}

/// 推送到了：重取开着的提醒列表（TanStack 对无限查询的重取按新数据依次重取所有已加载的页，后几页不丢、不漏），把比重取前
/// 最新一条还新、而且是新出现的或排序时间变了且未读的（有帮助条目当天又有人点、挪上来）记为「新」。「比最新一条还新」挡住
/// 重取途中用户点「加载更多」追加进来的旧条目。重取失败列表照旧（PAP-149 spec 第 5.4 节）
export async function refreshActiveLists(client: QueryClient): Promise<void> {
  if (client.getQueryCache().findAll(activeLists).length === 0) {
    return;
  }
  const before = occurrences(client);
  const newest = Math.max(...[...before.values()].map((item) => Date.parse(item.occurredAt)));
  await client.refetchQueries(activeLists);
  const fresh = [...occurrences(client)]
    .filter(([id, now]) => {
      if (!(Date.parse(now.occurredAt) > newest)) {
        return false;
      }
      const was = before.get(id);
      return was === undefined || (was.occurredAt !== now.occurredAt && !now.read);
    })
    .map(([id]) => id);
  useFreshNotifications.getState().mark(fresh);
}

/// 未读数的实时推送（PAP-149 spec 第 5.3 节）：只在根布局里常驻挂一次。已登录且页面可见时连 `EventSource`，事件写进
/// 未读数缓存（铃铛、菜单、三个标签立即更新）；连着时轮询停。页面隐藏、登出、会话失效时断开（tailnet 直连是 HTTP/1.1，
/// 同一站点最多 6 条连接）。中途断开由浏览器 3 秒后自己重连；拿到非 200 时浏览器不再重连，也看不到状态码：这时立刻
/// 取一次未读数（是 401 就会让当前用户失效，随之断开），60 秒后再试推送。
export function useUnreadStream(): void {
  const client = useQueryClient();
  const enabled = useNotificationsEnabled();

  useEffect(() => {
    if (!enabled || typeof EventSource === "undefined") {
      return;
    }
    const { setLive } = useNotificationStreamStore.getState();
    let source: EventSource | null = null;
    let retry: ReturnType<typeof setTimeout> | undefined;

    const disconnect = () => {
      clearTimeout(retry);
      retry = undefined;
      source?.close();
      source = null;
      setLive(false);
    };

    // 写进缓存之前取消在路上的轮询：它晚回来会用旧数冲掉推送的新数（同 mark-read 的 cancelInFlight）
    const onCounts = async (event: Event) => {
      const counts = parseCounts((event as MessageEvent).data);
      if (!counts) {
        return;
      }
      await client.cancelQueries({ queryKey: queryKeys.notificationUnread });
      client.setQueryData<UnreadData>(queryKeys.notificationUnread, { counts, etag: null });
      void refreshActiveLists(client);
    };

    const connect = () => {
      disconnect();
      const next = new EventSource(STREAM_URL);
      next.addEventListener("open", () => setLive(true));
      next.addEventListener("unread-count", (event) => void onCounts(event));
      next.addEventListener("error", () => {
        setLive(false);
        if (next.readyState !== EventSource.CLOSED) {
          return;
        }
        next.close();
        source = null;
        void client
          .fetchQuery({
            queryKey: queryKeys.notificationUnread,
            queryFn: () => fetchUnread(client),
            staleTime: 0,
          })
          .catch(() => {});
        retry = setTimeout(connect, STREAM_RETRY_MS);
      });
      source = next;
    };

    const onVisibility = () => {
      if (document.visibilityState === "visible") {
        connect();
      } else {
        disconnect();
      }
    };

    if (document.visibilityState === "visible") {
      connect();
    }
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      document.removeEventListener("visibilitychange", onVisibility);
      disconnect();
    };
  }, [enabled, client]);
}
