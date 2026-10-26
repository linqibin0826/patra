import { useQuery } from "@tanstack/react-query";
import type { LibraryEntry } from "@/lib/interaction/library";
import type { ItemStatus, LibraryOverview, Page } from "@/lib/interaction/schema";
import type { LibrarySort } from "@/lib/library/params";
import { queryKeys } from "@/lib/query-keys";

/// 门户自己的 Route Handler 的响应：非 2xx 抛错（查询进入 error，界面显示降级）
async function readOk<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`${res.url} 失败：${res.status}`);
  }
  return (await res.json()) as T;
}

/// 批量「我的状态」（`POST /api/me/library/status`）
export async function fetchLibraryStatus(keys: readonly string[]): Promise<ItemStatus[]> {
  const res = await fetch("/api/me/library/status", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ keys }),
  });
  return (await readOk<{ items: ItemStatus[] }>(res)).items;
}

/// 文献库概览（侧栏、收藏浮层的收藏夹清单）。失败不重试：直接显示降级，「重试」由用户点
export function useLibraryOverview(options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: queryKeys.libraryOverview,
    queryFn: async () => readOk<LibraryOverview>(await fetch("/api/me/library")),
    retry: false,
    enabled: options.enabled ?? true,
  });
}

/// 文献库一页条目：视图为 `all` / `unfiled` / 收藏夹 ID
export function useLibraryItems(view: string, sort: LibrarySort, page: number) {
  return useQuery({
    queryKey: queryKeys.libraryItems(view, sort, page),
    queryFn: async () => {
      const query = new URLSearchParams({ view, sort, page: String(page) });
      return readOk<Page<LibraryEntry>>(await fetch(`/api/me/library/items?${query}`));
    },
    retry: false,
  });
}
