import "server-only";
import { dehydrate, QueryClient } from "@tanstack/react-query";
import { cache } from "react";
import {
  fetchLibraryOverview,
  fetchLibraryPage,
  type LibraryView,
} from "@/lib/interaction/library";
import type { LibrarySort } from "@/lib/library/params";
import { queryKeys } from "@/lib/query-keys";

/// 文献库概览，同一次渲染里（布局、页面、`generateMetadata`）只请求一次
export const overviewForRequest = cache(() => fetchLibraryOverview());

/// 视图参数（地址里的字符串）→ 读函数要的视图
export function libraryViewOf(view: string): LibraryView {
  return view === "all" || view === "unfiled" ? view : { collectionId: view };
}

/// 文献库首屏数据：服务端取到的放进一个临时 QueryClient，脱水后交给客户端（`HydrationBoundary`）。
/// 取不到的不放：客户端挂载后自己再请求一次，再失败才显示错误块，「重试」就是重取查询
export async function prefetchLibrary(options: {
  overview?: boolean;
  items?: { view: string; sort: LibrarySort; page: number };
}) {
  const client = new QueryClient();
  const [overview, items] = await Promise.all([
    options.overview ? overviewForRequest() : null,
    options.items
      ? fetchLibraryPage(libraryViewOf(options.items.view), options.items.sort, options.items.page)
      : null,
  ]);
  if (overview?.kind === "ok") {
    client.setQueryData(queryKeys.libraryOverview, overview.data);
  }
  if (options.items && items?.kind === "ok") {
    const { view, sort, page } = options.items;
    client.setQueryData(queryKeys.libraryItems(view, sort, page), items.data);
  }
  return dehydrate(client);
}

/// 地址的查询串（只留文献库认的 `sort`、`page`），登录后回到原处用
export function libraryPath(path: string, search: Record<string, string | string[] | undefined>) {
  const params = new URLSearchParams();
  for (const name of ["sort", "page"]) {
    const value = search[name];
    if (typeof value === "string") {
      params.set(name, value);
    }
  }
  const query = params.toString();
  return query ? `${path}?${query}` : path;
}
