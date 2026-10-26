/// 文献库与公开收藏夹页的地址参数（服务端、客户端共用）

/// 排序：加入时间（默认，最新在前）/ 出版日期（最新在前）/ 期刊（A→Z）
export const LIBRARY_SORTS = ["added", "published", "journal"] as const;
export type LibrarySort = (typeof LIBRARY_SORTS)[number];

/// `?sort=` → 排序，不认识的回落加入时间
export function parseLibrarySort(value: unknown): LibrarySort {
  return LIBRARY_SORTS.find((sort) => sort === value) ?? "added";
}

/// `?page=` → 页码，不是正整数时回落 1
export function parsePage(value: unknown): number {
  return typeof value === "string" && /^[1-9]\d{0,5}$/.test(value) ? Number(value) : 1;
}

/// 实体 ID（收藏夹、条目、文献、用户）：Snowflake 正整数，无前导零，最多 19 位
export function isEntityId(value: string): boolean {
  return /^[1-9]\d{0,18}$/.test(value);
}

/// 文献库、公开收藏夹页的地址：默认排序与第 1 页不写进查询串
export function libraryHref(pathname: string, sort: LibrarySort, page: number): string {
  const params = new URLSearchParams();
  if (sort !== "added") {
    params.set("sort", sort);
  }
  if (page > 1) {
    params.set("page", String(page));
  }
  const query = params.toString();
  return query ? `${pathname}?${query}` : pathname;
}
