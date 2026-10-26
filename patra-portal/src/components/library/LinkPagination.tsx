"use client";

import { usePathname } from "next/navigation";
import { PaginationView } from "@/components/portal/browse/BrowsePagination";
import { type LibrarySort, libraryHref } from "@/lib/library/params";

/// 文献库、公开收藏夹页的分页：页码链接带上当前排序（`?sort=&page=`）
export function LinkPagination({
  sort,
  page,
  total,
  pageSize,
}: {
  sort: LibrarySort;
  page: number;
  total: number;
  pageSize: number;
}) {
  const pathname = usePathname();
  return (
    <PaginationView
      page={page}
      total={total}
      pageSize={pageSize}
      unit="篇"
      hrefOf={(next) => libraryHref(pathname, sort, next)}
    />
  );
}
