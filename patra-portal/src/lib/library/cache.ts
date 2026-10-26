import type { QueryClient } from "@tanstack/react-query";
import type { LibraryEntry } from "@/lib/interaction/library";
import type { ItemStatus, LibraryItem, Page } from "@/lib/interaction/schema";
import { queryKeys } from "@/lib/query-keys";

/// 改所有屏里这篇文献的「我的状态」：同一篇可能同时在几个批次里（首页头条与列表、公开页与详情页）。
/// 收藏、移除、勾选收藏夹、改备注之后调，交互只改缓存，不刷新页面（决策 H）
export function patchItemStatus(
  client: QueryClient,
  key: string,
  patch: Partial<Omit<ItemStatus, "key">>,
): void {
  client.setQueriesData<ItemStatus[]>({ queryKey: queryKeys.libraryStatusAll }, (old) =>
    old?.some((status) => status.key === key)
      ? old.map((status) => (status.key === key ? { ...status, ...patch } : status))
      : old,
  );
}

/// 文献库的概览与条目页整体失效：正在看的视图会自己重取，别的视图下次进来时重取
export function invalidateLibrary(client: QueryClient): Promise<void> {
  return client.invalidateQueries({ queryKey: queryKeys.library });
}

/// 文献库条目页的缓存前缀
const ITEM_PAGES = ["library", "items"] as const;

/// 从所有已缓存的条目页里去掉这些条目（移除、移出当前收藏夹之后，先在界面上去掉，再等失效重取）
export function dropEntries(client: QueryClient, itemIds: readonly string[], view?: string): void {
  const gone = new Set(itemIds);
  client.setQueriesData<Page<LibraryEntry>>(
    { queryKey: view === undefined ? ITEM_PAGES : [...ITEM_PAGES, view] },
    (old) =>
      old && {
        ...old,
        total: Math.max(
          0,
          old.total - old.items.filter((entry) => gone.has(entry.item.itemId)).length,
        ),
        items: old.items.filter((entry) => !gone.has(entry.item.itemId)),
      },
  );
}

/// 改所有已缓存条目页里这一条（备注、所在收藏夹）
export function patchEntry(
  client: QueryClient,
  itemId: string,
  patch: Partial<Omit<LibraryItem, "itemId">>,
): void {
  client.setQueriesData<Page<LibraryEntry>>({ queryKey: ITEM_PAGES }, (old) =>
    old?.items.some((entry) => entry.item.itemId === itemId)
      ? {
          ...old,
          items: old.items.map((entry) =>
            entry.item.itemId === itemId ? { ...entry, item: { ...entry.item, ...patch } } : entry,
          ),
        }
      : old,
  );
}
