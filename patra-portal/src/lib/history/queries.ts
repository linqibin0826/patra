import { type InfiniteData, type QueryClient, useInfiniteQuery } from "@tanstack/react-query";
import type { HistoryPage } from "@/lib/analytics/history-schema";
import { queryKeys } from "@/lib/query-keys";

/// 门户路由回 401：会话在页面开着时过期
export class SignedOutError extends Error {
  constructor() {
    super("会话已失效");
    this.name = "SignedOutError";
  }
}

/// 我的阅读历史，一页 30 条、游标翻页。删一条、清空只改缓存不重拉（重拉会让已加载的页错位）；窗口回到前台也不重拉
export function useReadingHistory() {
  return useInfiniteQuery({
    queryKey: queryKeys.readingHistory,
    queryFn: async ({ pageParam }): Promise<HistoryPage> => {
      const query = pageParam ? `?${new URLSearchParams({ cursor: pageParam })}` : "";
      const res = await fetch(`/api/me/reading-history${query}`);
      if (res.status === 401) {
        throw new SignedOutError();
      }
      if (!res.ok) {
        throw new Error(`阅读历史加载失败：${res.status}`);
      }
      return (await res.json()) as HistoryPage;
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    retry: false,
    refetchOnWindowFocus: false,
  });
}

/// 删掉一条之后：从已加载的各页里去掉它
export function removeHistoryEntry(client: QueryClient, articleId: string): void {
  client.setQueryData<InfiniteData<HistoryPage, string | null>>(queryKeys.readingHistory, (data) =>
    data
      ? {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            items: page.items.filter((item) => item.articleId !== articleId),
          })),
        }
      : data,
  );
}

/// 清空之后：只留一页空的，开关状态不变
export function markHistoryCleared(client: QueryClient): void {
  client.setQueryData<InfiniteData<HistoryPage, string | null>>(queryKeys.readingHistory, (data) =>
    data
      ? {
          pages: [
            {
              recordingEnabled: data.pages[0]?.recordingEnabled ?? true,
              items: [],
              nextCursor: null,
            },
          ],
          pageParams: [null],
        }
      : data,
  );
}
