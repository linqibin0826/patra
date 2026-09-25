"use client";

import { SearchIcon, SlidersHorizontalIcon, XIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { SORT_OPTIONS, serializeVenueBrowseQuery } from "@/lib/portal-api/venue-browse";
import { cn } from "@/lib/utils";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";
import type { VenueBrowseQuery } from "@/types/portal";

interface Props {
  query: VenueBrowseQuery;
}

/// 期刊浏览页检索 / 排序条。
/// 检索框：白底内嵌井（放大镜 + 清除）+ 独立「检索」提交按钮；防抖 300ms replace。
/// 排序：「排序」标签 + 连体 segmented 控件（选中深填充，降序项带 ↓）；push。
/// 移动端附「筛选」按钮（开抽屉）。
export function JournalSearchSortControls({ query }: Props) {
  const router = useRouter();
  const open = useBrowseFilterUiStore((s) => s.open);

  const [localQ, setLocalQ] = useState(query.q);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 当外部 query.q 改变时（如清除 chip 后），同步本地状态
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
    (value: string) => {
      const qs = serializeVenueBrowseQuery({ ...query, q: value, page: 1 });
      router.replace(`/journals${qs ? `?${qs}` : ""}`);
    },
    [query, router],
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
      // 取消 pending 的检索防抖，否则 300ms 后旧 query 的 replace 会回滚本次排序
      if (debounceRef.current) clearTimeout(debounceRef.current);
      const qs = serializeVenueBrowseQuery({ ...query, sort: sortId, page: 1 });
      router.push(`/journals${qs ? `?${qs}` : ""}`);
    },
    [query, router],
  );

  // 已选筛选维度总数（移动端角标）
  const activeFilterCount =
    query.subject.length +
    query.jcr.length +
    query.cas.length +
    query.country.length +
    (query.casTop ? 1 : 0) +
    (query.oa ? 1 : 0) +
    (query.doaj ? 1 : 0);

  return (
    <div className="flex flex-wrap items-center gap-3.5 border-b border-(--border-default) py-3">
      {/* 检索框 + 检索按钮 */}
      <form
        onSubmit={handleSubmit}
        className="flex min-w-0 flex-1 basis-[360px] items-center gap-2"
      >
        <div className="flex min-w-0 flex-1 items-center gap-2.5 rounded-md border border-(--border-strong) bg-white px-3 shadow-inner transition-colors focus-within:border-clay-400">
          <SearchIcon className="size-4 shrink-0 text-(--fg-3)" aria-hidden="true" />
          <input
            type="search"
            placeholder="按刊名 / 缩写检索期刊"
            value={localQ}
            onChange={(e) => handleInputChange(e.target.value)}
            autoComplete="off"
            spellCheck="false"
            aria-label="按刊名检索期刊"
            className="min-w-0 flex-1 border-0 bg-transparent py-2.5 text-base text-ink-900 outline-none placeholder:text-(--fg-4)"
          />
          {localQ && (
            <button
              type="button"
              aria-label="清除搜索"
              onClick={handleClear}
              className="flex size-6 shrink-0 items-center justify-center rounded text-(--fg-3) hover:bg-paper-200 hover:text-ink-900"
            >
              <XIcon className="size-3.5" />
            </button>
          )}
        </div>
        <button
          type="submit"
          className="flex shrink-0 items-center gap-1.5 self-stretch rounded-md border border-(--border-strong) bg-paper-50 px-3.5 text-sm font-semibold text-(--fg-1) transition-colors hover:bg-paper-200"
        >
          <SearchIcon className="size-3.5 text-clay-600" aria-hidden="true" />
          检索
        </button>
      </form>

      {/* 排序段控件 */}
      <div className="flex shrink-0 items-center gap-2">
        <span className="font-mono text-[9.5px] uppercase tracking-[0.08em] text-(--fg-4)">
          排序
        </span>
        <fieldset
          aria-label="排序方式"
          className="m-0 inline-flex min-w-0 items-center overflow-hidden rounded-md border border-(--border-default) p-0"
        >
          {SORT_OPTIONS.map((opt) => {
            const active = query.sort === opt.id;
            return (
              <button
                key={opt.id}
                type="button"
                aria-pressed={active}
                onClick={() => handleSort(opt.id)}
                className={cn(
                  "border-0 border-r border-(--border-subtle) px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors last:border-r-0",
                  active
                    ? "bg-ink-900 text-paper-50 hover:bg-ink-800"
                    : "text-(--fg-3) hover:bg-paper-200 hover:text-ink-900",
                )}
              >
                {opt.label}
                {opt.desc && (
                  <span aria-hidden="true" className="ml-1 font-mono text-[10px] opacity-70">
                    ↓
                  </span>
                )}
              </button>
            );
          })}
        </fieldset>
      </div>

      {/* 移动端筛选按钮（md 以上隐藏） */}
      <button
        type="button"
        onClick={open}
        aria-label="筛选"
        className="relative ml-auto flex shrink-0 items-center gap-1.5 rounded-md border border-(--border-default) bg-paper-50 px-3 py-1.5 text-sm font-semibold text-(--fg-1) transition-colors hover:bg-paper-200 md:hidden"
      >
        <SlidersHorizontalIcon className="size-3.5" />
        筛选
        {activeFilterCount > 0 && (
          <span
            data-testid="filter-badge"
            className="absolute -top-1.5 -right-1.5 flex size-4 items-center justify-center rounded-full bg-clay-500 text-[10px] font-semibold text-white"
          >
            {activeFilterCount}
          </span>
        )}
      </button>
    </div>
  );
}
