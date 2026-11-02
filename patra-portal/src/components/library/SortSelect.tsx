"use client";

import { usePathname, useRouter } from "next/navigation";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { LIBRARY_SORTS, type LibrarySort, libraryHref } from "@/lib/library/params";

const SORT_LABELS: Record<LibrarySort, string> = {
  added: "加入时间",
  published: "出版日期",
  journal: "期刊",
};

const SORT_ITEMS = LIBRARY_SORTS.map((value) => ({ value, label: SORT_LABELS[value] }));

/// 排序下拉（简报 §3.10）：加入时间（默认）/ 出版日期 / 期刊，写进地址 `?sort=`，换排序回到第 1 页
export function SortSelect({ sort }: { sort: LibrarySort }) {
  const router = useRouter();
  const pathname = usePathname();
  return (
    <div className="flex items-center gap-2 text-fg-2 text-sm">
      <span aria-hidden="true">按</span>
      <Select
        items={SORT_ITEMS}
        value={sort}
        onValueChange={(next) => {
          if (next) {
            router.push(libraryHref(pathname, next, 1));
          }
        }}
      >
        <SelectTrigger variant="compact" aria-label="排序">
          <SelectValue />
        </SelectTrigger>
        <SelectContent sheetTitle="排序">
          {SORT_ITEMS.map((item) => (
            <SelectItem key={item.value} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
