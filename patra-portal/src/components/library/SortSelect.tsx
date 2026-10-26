"use client";

import { usePathname, useRouter } from "next/navigation";
import { LIBRARY_SORTS, type LibrarySort, libraryHref } from "@/lib/library/params";

const SORT_LABELS: Record<LibrarySort, string> = {
  added: "加入时间",
  published: "出版日期",
  journal: "期刊",
};

/// 排序下拉（简报 §3.10）：加入时间（默认）/ 出版日期 / 期刊，写进地址 `?sort=`，换排序回到第 1 页
export function SortSelect({ sort }: { sort: LibrarySort }) {
  const router = useRouter();
  const pathname = usePathname();
  return (
    <label className="flex items-center gap-2 text-sm text-fg-2">
      <span aria-hidden="true">按</span>
      <select
        aria-label="排序"
        value={sort}
        onChange={(event) =>
          router.push(libraryHref(pathname, event.target.value as LibrarySort, 1))
        }
        className="rounded-md border border-border-default bg-paper-50 px-2 py-1 text-sm text-fg-1"
      >
        {LIBRARY_SORTS.map((option) => (
          <option key={option} value={option}>
            {SORT_LABELS[option]}
          </option>
        ))}
      </select>
    </label>
  );
}
