import { EmptyState } from "@/components/portal/browse/EmptyState";
import {
  clearAllConditions,
  hasSearchConditions,
  isExactLookup,
  papersHref,
} from "@/lib/portal-api/paper-search";
import type { PageResult, Paper, PaperSearchQuery } from "@/types/portal";

export type PapersEmptyKind =
  | "no-match"
  | "filter-only"
  | "exact-miss"
  | "empty-library"
  | "page-out-of-range";

/** 由查询与结果判定空态类型；有条目时为 null。 */
export function papersEmptyKind(
  query: PaperSearchQuery,
  page: Pick<PageResult<Paper>, "total" | "items">,
): PapersEmptyKind | null {
  if (page.items.length > 0) return null;
  if (page.total > 0) return "page-out-of-range";
  if (isExactLookup(query)) return "exact-miss";
  if (query.q || query.author) return "no-match";
  if (hasSearchConditions(query)) return "filter-only";
  return "empty-library";
}

/**
 * 文献检索空态（RSC）：关键词无结果 / 仅筛选无结果 / PMID·DOI 未命中 / 空库 / 页码越界。
 * 检索条与 facet 仍在页面上，这里只替换列表区。
 */
export function PapersEmptyResult({
  kind,
  query,
}: {
  kind: PapersEmptyKind;
  query: PaperSearchQuery;
}) {
  const clearHref = papersHref(clearAllConditions(query));
  switch (kind) {
    case "no-match":
      return (
        <EmptyState
          title={`未找到匹配 "${query.q || query.author}" 的文献`}
          description="换个关键词试试，或放宽 / 清除筛选条件。"
          actions={[
            { href: clearHref, label: "清除全部筛选" },
            { href: "/", label: "返回首页" },
          ]}
        />
      );
    case "filter-only":
      return (
        <EmptyState
          title="当前筛选条件下没有文献"
          description="放宽或清除部分筛选条件后重试。"
          actions={[{ href: clearHref, label: "清除全部筛选" }]}
        />
      );
    case "exact-miss":
      return (
        <EmptyState
          title={`未找到 ${query.pmid ? `PMID ${query.pmid}` : `DOI ${query.doi}`} 对应的文献`}
          description="请核对号码是否正确；库中尚未收录的文献也无法查到。"
          actions={[{ href: "/papers", label: "浏览全部文献" }]}
        />
      );
    case "empty-library":
      return (
        <EmptyState
          title="库中暂无文献"
          description="文献数据尚未入库，请稍后再试。"
          actions={[{ href: "/", label: "返回首页" }]}
        />
      );
    case "page-out-of-range":
      return (
        <EmptyState
          title="该页没有内容"
          description="页码超出了结果范围。"
          actions={[{ href: papersHref({ ...query, page: 1 }), label: "回到第 1 页" }]}
        />
      );
  }
}
