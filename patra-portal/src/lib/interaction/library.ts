import "server-only";
import { attachPapers, type WithPaper } from "@/lib/interaction/attach-papers";
import { READ_TIMEOUT_MS, type Read, toRead } from "@/lib/interaction/read";
import { callInteraction } from "@/lib/interaction/request";
import {
  type ItemStatus,
  type LibraryItem,
  type LibraryOverview,
  libraryItemSchema,
  libraryOverviewSchema,
  type Page,
  pageSchema,
  statusResponseSchema,
} from "@/lib/interaction/schema";
import { isEntityId, type LibrarySort } from "@/lib/library/params";

export { isEntityId, parseLibrarySort, parsePage } from "@/lib/library/params";

/// 「我的状态」的超时：一屏的收藏按钮等它，3 秒没回就显示「收藏暂不可用」。简报 §3.17 建议 1.5 秒、具体由工程 spec 定：
/// mini 上实测平时 0.1 ~ 1 秒、偶有 2 秒以上的毛刺，1.5 秒会把正常的慢请求误报成不可用
const STATUS_TIMEOUT_MS = 3_000;

/// 每页篇数（简报 §3.10）
export const LIBRARY_PAGE_SIZE = 50;

/// 文献库的视图：全部、未分类、某个收藏夹
export type LibraryView = "all" | "unfiled" | { collectionId: string };

export type LibraryEntry = WithPaper<LibraryItem>;

/// 批量「我的状态」（≤ 100 个文献 ID，调用方已去重）
export async function fetchLibraryStatus(keys: string[]): Promise<Read<ItemStatus[]>> {
  const path = "/me/library/status";
  const reply = await callInteraction(path, {
    method: "POST",
    json: { type: "publication", keys },
    timeoutMs: STATUS_TIMEOUT_MS,
  });
  const read = toRead(reply, statusResponseSchema, path);
  return read.kind === "ok" ? { kind: "ok", data: read.data.items } : read;
}

/// 文献库概览：全部 / 未分类的篇数与本人全部收藏夹
export async function fetchLibraryOverview(): Promise<Read<LibraryOverview>> {
  const path = "/me/library";
  return toRead(
    await callInteraction(path, { timeoutMs: READ_TIMEOUT_MS }),
    libraryOverviewSchema,
    path,
  );
}

/// 文献库一页条目，配好卡片。收藏夹视图里收藏夹不是自己的或不存在：`not-found`
export async function fetchLibraryPage(
  view: LibraryView,
  sort: LibrarySort,
  page: number,
): Promise<Read<Page<LibraryEntry>>> {
  const paging = `sort=${sort}&page=${page}&size=${LIBRARY_PAGE_SIZE}`;
  let path: string;
  if (typeof view === "string") {
    path = `/me/library/items?view=${view}&${paging}`;
  } else if (isEntityId(view.collectionId)) {
    path = `/me/collections/${view.collectionId}/items?${paging}`;
  } else {
    return { kind: "not-found" };
  }
  const read = toRead(
    await callInteraction(path, { timeoutMs: READ_TIMEOUT_MS }),
    pageSchema(libraryItemSchema),
    path,
  );
  return read.kind === "ok" ? attachPapers(read.data) : read;
}
