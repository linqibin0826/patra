"use client";

import { SearchIcon, XIcon } from "lucide-react";
import { type FormEvent, useState } from "react";
import { useBrowseQuery } from "@/components/portal/browse/BrowseQueryProvider";
import { FilterTrigger } from "@/components/portal/browse/FilterTrigger";
import { type SortOption, SortSegmented } from "@/components/portal/browse/SortSegmented";
import { SEARCH_MODES } from "@/data/search-modes";
import {
  filterCount,
  submitExactLookup,
  submitTextSearch,
  validateSearchInput,
  withSort,
} from "@/lib/portal-api/paper-search";
import { cn } from "@/lib/utils";
import type { ComposerMode, PaperSearchQuery, PaperSortId } from "@/types/portal";

const SORT_OPTIONS: readonly SortOption<PaperSortId>[] = [
  { id: "latest", label: "最近更新", desc: true },
  { id: "year", label: "年份", desc: true },
];

const ERROR_ID = "paper-search-error";

function initialMode(query: PaperSearchQuery): ComposerMode {
  if (query.pmid) return "pmid";
  if (query.doi) return "doi";
  if (!query.q && query.author) return "author";
  return "keyword";
}

function fieldValue(query: PaperSearchQuery, mode: ComposerMode): string {
  switch (mode) {
    case "keyword":
      return query.q;
    case "author":
      return query.author;
    case "pmid":
      return query.pmid;
    case "doi":
      return query.doi;
  }
}

/// 文献检索条：四 tab（关键词 / PMID / DOI / 作者）共用一个输入框，右侧排序，移动端附筛选按钮。
/// 关键词 / 作者提交只替换自身并清除 pmid/doi（与其余筛选叠加）；PMID / DOI 提交为精确定位（清空其余条件）。
export function PaperSearchBar() {
  const { query, navigate } = useBrowseQuery<PaperSearchQuery>();
  const [mode, setMode] = useState<ComposerMode>(() => initialMode(query));
  const [value, setValue] = useState(() => fieldValue(query, initialMode(query)));
  const [error, setError] = useState<string | null>(null);

  // 外部 query 变化（chip 移除 / 后退 / 清除全部）时同步输入框：渲染期比较上次同步值（派生状态写法，无 effect）
  const synced = fieldValue(query, mode);
  const [lastSynced, setLastSynced] = useState(synced);
  if (synced !== lastSynced) {
    setLastSynced(synced);
    setValue(synced);
  }

  const current = SEARCH_MODES.find((m) => m.id === mode);

  const switchMode = (next: ComposerMode) => {
    const nextValue = fieldValue(query, next);
    setMode(next);
    setValue(nextValue);
    setLastSynced(nextValue);
    setError(null);
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const text = value.trim();
    const isExact = mode === "pmid" || mode === "doi";
    if (isExact && !text) return;
    // 与 BE 约束一致：PMID 纯数字、检索词 ≤ 200 字；超长词若放行会被解析层丢弃，静默变成全库浏览
    const invalid = text ? validateSearchInput(mode, text) : null;
    if (invalid) {
      setError(invalid);
      return;
    }
    if (isExact) {
      navigate(submitExactLookup(mode, text));
      return;
    }
    navigate((cur) => submitTextSearch(cur, mode === "keyword" ? "q" : "author", text));
  };

  const handleClear = () => {
    setValue("");
    setError(null);
    if ((mode === "keyword" && query.q) || (mode === "author" && query.author)) {
      navigate((cur) => submitTextSearch(cur, mode === "keyword" ? "q" : "author", ""));
    }
  };

  return (
    <div className="flex flex-col gap-2.5 border-b border-(--border-default) pb-3">
      <fieldset
        aria-label="检索方式"
        className="flex min-w-0 items-center gap-1 max-md:overflow-x-auto"
      >
        {SEARCH_MODES.map((m) => (
          <button
            key={m.id}
            type="button"
            aria-pressed={mode === m.id}
            onClick={() => switchMode(m.id)}
            className={cn(
              "shrink-0 rounded-md px-3 py-1 text-sm font-medium transition-colors",
              mode === m.id
                ? "bg-ink-900 text-paper-50"
                : "text-(--fg-3) hover:bg-paper-200 hover:text-ink-900",
            )}
          >
            {m.label}
            {m.id === "keyword" && (
              <span className="ml-1.5 font-mono text-[10px] opacity-70">默认</span>
            )}
          </button>
        ))}
      </fieldset>

      <div className="flex flex-wrap items-center gap-3.5">
        <form
          onSubmit={handleSubmit}
          className="flex min-w-0 flex-1 basis-[360px] items-center gap-2"
        >
          <div className="flex min-w-0 flex-1 items-center gap-2.5 rounded-md border border-(--border-strong) bg-bg-elevated px-3 shadow-inner transition-colors focus-within:border-clay-400">
            <SearchIcon className="size-4 shrink-0 text-(--fg-3)" aria-hidden="true" />
            <input
              type="search"
              value={value}
              onChange={(e) => {
                setValue(e.target.value);
                setError(null);
              }}
              placeholder={current?.placeholder}
              aria-label={`按${current?.label ?? "关键词"}检索文献`}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? ERROR_ID : undefined}
              autoComplete="off"
              spellCheck="false"
              className={cn(
                "min-w-0 flex-1 border-0 bg-transparent py-2.5 text-base text-ink-900 outline-none placeholder:text-(--fg-4)",
                current?.mono && "font-mono text-sm",
              )}
            />
            {value && (
              <button
                type="button"
                aria-label="清除输入"
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

        <SortSegmented
          options={SORT_OPTIONS}
          value={query.sort}
          onChange={(sort) => navigate((cur) => withSort(cur, sort))}
        />

        <FilterTrigger count={filterCount(query)} />
      </div>

      {error && (
        <p id={ERROR_ID} role="alert" className="text-xs text-clay-700">
          {error}
        </p>
      )}
    </div>
  );
}
