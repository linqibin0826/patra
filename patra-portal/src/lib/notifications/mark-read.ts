import {
  type InfiniteData,
  type QueryClient,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import { useCallback } from "react";
import { toast } from "sonner";
import type { Notification, NotificationPage, UnreadCounts } from "@/lib/notification/inbox-schema";
import type { UnreadData } from "@/lib/notifications/queries";
import { tabOf } from "@/lib/notifications/row";
import { queryKeys } from "@/lib/query-keys";

type ListData = InfiniteData<NotificationPage>;

/// 已缓存的三个标签里，`match` 命中的提醒都标为已读
function markInLists(client: QueryClient, match: (n: Notification) => boolean): void {
  client.setQueriesData<ListData>({ queryKey: queryKeys.notificationListAll }, (data) =>
    data
      ? {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            items: page.items.map((n) => (!n.read && match(n) ? { ...n, read: true } : n)),
          })),
        }
      : data,
  );
}

/// 用新的计数覆盖未读数。ETag 置空：下一次轮询不带条件头，拿回与新计数对应的 ETag（不自己拼，不依赖后端的格式）
function setCounts(client: QueryClient, counts: UnreadCounts): void {
  client.setQueryData<UnreadData>(queryKeys.notificationUnread, { counts, etag: null });
}

/// 写进缓存之前取消在路上的轮询与列表请求：它们晚回来会用旧数据冲掉刚写进去的结果（PAP-124 交接 §5）。
/// 还没有数据的列表（第一次加载）不取消：取消会让它回到没有数据的空闲态，骨架一直转、没有东西再触发重取
function cancelInFlight(client: QueryClient): Promise<void> {
  return client.cancelQueries({
    queryKey: queryKeys.notifications,
    predicate: (query) =>
      query.queryKey[1] === queryKeys.notificationUnread[1] || query.state.data !== undefined,
  });
}

/// 写操作遇到 401（会话在页面开着时过期）：让当前用户失效，导航栏换成「登录」、提醒页去登录页
function onSignedOut(client: QueryClient, res: Response): void {
  if (res.status === 401) {
    void client.invalidateQueries({ queryKey: queryKeys.currentUser });
  }
}

/// 这条提醒减掉之后的未读数：减它所在标签的数，都不低于 0；回复与 @ 还减铃铛的 `total`，有帮助不计入 `total`
function minusOne(counts: UnreadCounts, n: Notification): UnreadCounts {
  const tab = tabOf(n.type);
  return {
    ...counts,
    [tab]: Math.max(0, counts[tab] - 1),
    total: tab === "helpful" ? counts.total : Math.max(0, counts.total - 1),
  };
}

/// 点一行时的单条已读：未读才发；立即在界面上标为已读、未读数减 1，同时请求（`keepalive`：跳转、新标签页打开都不打断），
/// 不等它就跳转。成功后用返回的计数覆盖；404（已清理或不是自己的）与其他失败都不回滚——人已经跳走了——只把未读数标为
/// 过期重取一次：推送连着时没有轮询来纠正（PAP-149）
export function useMarkRead(): (notification: Notification) => void {
  const client = useQueryClient();
  return useCallback(
    (notification: Notification) => {
      if (notification.read) {
        return;
      }
      void cancelInFlight(client);
      markInLists(client, (n) => n.notificationId === notification.notificationId);
      const previous = client.getQueryData<UnreadData>(queryKeys.notificationUnread);
      if (previous) {
        setCounts(client, minusOne(previous.counts, notification));
      }
      void fetch(`/api/me/notifications/${notification.notificationId}/read`, {
        method: "PUT",
        keepalive: true,
      })
        .then(async (res) => {
          onSignedOut(client, res);
          if (res.ok) {
            const counts = (await res.json()) as UnreadCounts;
            await cancelInFlight(client);
            setCounts(client, counts);
          } else {
            void client.invalidateQueries({ queryKey: queryKeys.notificationUnread });
          }
        })
        .catch(() => void client.invalidateQueries({ queryKey: queryKeys.notificationUnread }));
    },
    [client],
  );
}

/// 改之前的缓存：失败时原样放回
interface Snapshot {
  unread: UnreadData | undefined;
  lists: [readonly unknown[], ListData | undefined][];
}

/// 全部已读（三个标签一起）：乐观清零；成功后用返回的计数并提示「已全部标为已读」；失败时圆点与计数恢复，
/// `failed` 为真（页面顶部提示条，不出 toast）
export function useMarkAllRead(): { markAll: () => void; pending: boolean; failed: boolean } {
  const client = useQueryClient();
  const mutation = useMutation<UnreadCounts, Error, void, Snapshot>({
    mutationFn: async () => {
      const res = await fetch(`/api/me/notifications/read`, { method: "PUT" });
      onSignedOut(client, res);
      if (!res.ok) {
        throw new Error(`全部已读失败：${res.status}`);
      }
      return (await res.json()) as UnreadCounts;
    },
    onMutate: async () => {
      await cancelInFlight(client);
      const snapshot: Snapshot = {
        unread: client.getQueryData<UnreadData>(queryKeys.notificationUnread),
        lists: client.getQueriesData<ListData>({ queryKey: queryKeys.notificationListAll }),
      };
      markInLists(client, () => true);
      if (snapshot.unread) {
        setCounts(client, { total: 0, replies: 0, mentions: 0, helpful: 0 });
      }
      return snapshot;
    },
    onError: (_error, _variables, snapshot) => {
      if (!snapshot) {
        return;
      }
      client.setQueryData(queryKeys.notificationUnread, snapshot.unread);
      for (const [key, data] of snapshot.lists) {
        client.setQueryData(key, data);
      }
      // 放回的是点之前的数，期间推送可能来过更新的：重取一次
      void client.invalidateQueries({ queryKey: queryKeys.notificationUnread });
    },
    onSuccess: async (counts) => {
      await cancelInFlight(client);
      setCounts(client, counts);
      toast.success("已全部标为已读");
    },
  });
  return {
    markAll: () => mutation.mutate(),
    pending: mutation.isPending,
    failed: mutation.isError,
  };
}
