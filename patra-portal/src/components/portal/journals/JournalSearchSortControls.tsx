"use client";

import { SearchIcon, XIcon } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useBrowseQuery } from "@/components/portal/browse/BrowseQueryProvider";
import { FilterTrigger } from "@/components/portal/browse/FilterTrigger";
import { SortSegmented } from "@/components/portal/browse/SortSegmented";
import { SORT_OPTIONS } from "@/lib/portal-api/venue-browse";
import type { VenueBrowseQuery } from "@/types/portal";

/// 期刊浏览页检索 / 排序条。
/// 检索框：白底内嵌井（放大镜 + 清除）+ 独立「检索」提交按钮；防抖 300ms replace。
/// 排序：公用 SortSegmented；push。移动端附「筛选」按钮（开抽屉）。
export function JournalSearchSortControls() {
  const { query, navigate } = useBrowseQuery<VenueBrowseQuery>();

  const [localQ, setLocalQ] = useState(query.q);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 外部 query.q 改变时（如清除 chip 后）同步本地状态
  useEffect(() => {
    setLocalQ(query.q);
  }, [query.q]);

  // 卸载时清除未触发的防抖 timer，避免陈旧 navigate 在组件消失后仍执行
  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, []);

  const navigateQ = useCallback(
    (value: string) => navigate((cur) => ({ ...cur, q: value, page: 1 }), { replace: true }),
    [navigate],
  );

  const handleInputChange = useCallback(
    (value: string) => {
      setLocalQ(value);
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(() => navigateQ(value), 300);
    },
    [navigateQ],
  );

  const handleClear = useCallback(() => {
    setLocalQ("");
    if (debounceRef.current) clearTimeout(debounceRef.current);
    navigateQ("");
  }, [navigateQ]);

  // 「检索」按钮 / 回车提交：立即冲刷防抖，按当前输入检索
  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      if (debounceRef.current) clearTimeout(debounceRef.current);
      navigateQ(localQ);
    },
    [navigateQ, localQ],
  );

  const handleSort = useCallback(
    (sortId: VenueBrowseQuery["sort"]) => {
      // 取消 pending 的检索防抖，否则 300ms 后的 replace 会覆盖本次排序
      if (debounceRef.current) clearTimeout(debounceRef.current);
      navigate((cur) => ({ ...cur, sort: sortId, page: 1 }));
    },
    [navigate],
  );

  const activeFilterCount =
    query.subject.length +
    query.jcr.length +
    query.cas.length +
    query.country.length +
    (query.casTop ? 1 : 0) +
    (query.oa ? 1 : 0) +
    (query.doaj ? 1 : 0);

  return (
    <div className="flex flex-wrap items-center gap-3.5 border-b border-border-default py-3">
      <form
        onSubmit={handleSubmit}
        className="flex min-w-0 flex-1 basis-[360px] items-center gap-2"
      >
        <div className="flex min-w-0 flex-1 items-center gap-2.5 rounded-md border border-border-strong bg-bg-elevated px-3 shadow-inset transition-colors focus-within:border-border-focus">
          <SearchIcon className="size-4 shrink-0 text-fg-3" aria-hidden="true" />
          <input
            type="search"
            placeholder="按刊名 / 缩写检索期刊"
            value={localQ}
            onChange={(e) => handleInputChange(e.target.value)}
            autoComplete="off"
            spellCheck="false"
            aria-label="按刊名检索期刊"
            className="min-w-0 flex-1 border-0 bg-transparent py-2.5 text-base text-ink-900 outline-none placeholder:text-fg-4"
          />
          {localQ && (
            <button
              type="button"
              aria-label="清除搜索"
              onClick={handleClear}
              className="flex size-6 shrink-0 items-center justify-center rounded text-fg-3 hover:bg-paper-200 hover:text-ink-900"
            >
              <XIcon className="size-3.5" />
            </button>
          )}
        </div>
        <button
          type="submit"
          className="flex shrink-0 items-center gap-1.5 self-stretch rounded-md border border-border-strong bg-paper-50 px-3.5 text-sm font-semibold text-fg-1 transition-colors hover:bg-paper-200"
        >
          <SearchIcon className="size-3.5 text-clay-600" aria-hidden="true" />
          检索
        </button>
      </form>

      <SortSegmented options={SORT_OPTIONS} value={query.sort} onChange={handleSort} />

      <FilterTrigger count={activeFilterCount} />
    </div>
  );
}
