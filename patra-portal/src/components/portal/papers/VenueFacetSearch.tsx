"use client";

import { SearchIcon } from "lucide-react";
import { type KeyboardEvent, useEffect, useId, useState } from "react";
import { FacetCheckRow } from "@/components/portal/browse/FacetCheckRow";
import { MAX_VENUES } from "@/lib/portal-api/paper-search";
import { useVenueSuggestQuery } from "@/lib/portal-api/venue-suggest-query";
import { cn } from "@/lib/utils";
import type { VenueSuggestion } from "@/types/portal";

const DEBOUNCE_MS = 250;

interface VenueFacetSearchProps {
  selected: readonly string[];
  /** 已选期刊 id → 刊名（服务端刊名 + 刚添加的缓存） */
  names: Readonly<Record<string, string>>;
  onAdd: (venue: VenueSuggestion) => void;
  onRemove: (id: string) => void;
}

/// 期刊 facet：输入刊名 → 防抖取候选（combobox + listbox，↑↓ / Enter / Esc）→ 选中加入筛选；
/// 已选期刊以勾选行列在下方（无计数，BE 不返回期刊计数），取消勾选即移除。
export function VenueFacetSearch({ selected, names, onAdd, onRemove }: VenueFacetSearchProps) {
  const listId = useId();
  const [input, setInput] = useState("");
  const [debounced, setDebounced] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(input.trim()), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [input]);

  const { data, isFetching, isError } = useVenueSuggestQuery(debounced);

  // 候选只属于"已防抖的关键词"：输入与之不一致时视为加载中，禁止选择，避免回车选中上一个词的候选
  // 已选满上限：URL 解析只保留前 MAX_VENUES 个，再添加会被丢弃，故直接禁用输入
  const full = selected.length >= MAX_VENUES;
  const settled = input.trim() === debounced;
  const candidates = settled ? (data ?? []).filter((venue) => !selected.includes(venue.id)) : [];
  const showList = open && input.trim().length > 0;
  // 候选变少时把高亮夹回有效范围
  const activeIndex = Math.min(active, Math.max(candidates.length - 1, 0));
  const activeOption = showList ? candidates[activeIndex] : undefined;

  let status: string | null = null;
  if (!settled || (isFetching && !data)) status = "加载中…";
  else if (isError && !data) status = "候选加载失败";
  else if (data && candidates.length === 0) status = "未找到匹配期刊";

  const pick = (venue: VenueSuggestion) => {
    onAdd(venue);
    setInput("");
    setDebounced("");
    setOpen(false);
    setActive(0);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setOpen(true);
      setActive(Math.min(activeIndex + 1, Math.max(candidates.length - 1, 0)));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive(Math.max(activeIndex - 1, 0));
    } else if (e.key === "Enter") {
      if (activeOption) {
        e.preventDefault();
        pick(activeOption);
      }
    } else if (e.key === "Escape") {
      setOpen(false);
    }
  };

  return (
    <div className="flex flex-col gap-1.5 pr-1">
      <div className="relative">
        <div className="flex items-center gap-1.5 rounded-md border border-(--border-default) bg-white px-2">
          <SearchIcon className="size-3.5 shrink-0 text-(--fg-3)" aria-hidden="true" />
          <input
            role="combobox"
            aria-label="搜索期刊"
            aria-expanded={showList}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={activeOption ? `${listId}-${activeOption.id}` : undefined}
            placeholder="搜刊名添加…"
            disabled={full}
            value={input}
            onChange={(e) => {
              setInput(e.target.value);
              setOpen(true);
              setActive(0);
            }}
            onKeyDown={handleKeyDown}
            onBlur={() => setOpen(false)}
            className="h-7 min-w-0 flex-1 border-0 bg-transparent text-xs outline-none placeholder:text-(--fg-4)"
          />
        </div>
        {showList && (
          <div className="absolute inset-x-0 top-full z-20 mt-1 rounded-md border border-(--border-default) bg-paper-50 py-1 shadow-md">
            {status ? (
              <p className="px-2 py-1.5 text-xs text-(--fg-3)">{status}</p>
            ) : (
              <div
                id={listId}
                role="listbox"
                aria-label="期刊候选"
                className="max-h-56 overflow-y-auto"
              >
                {candidates.map((venue, index) => (
                  <div
                    key={venue.id}
                    id={`${listId}-${venue.id}`}
                    role="option"
                    aria-selected={index === activeIndex}
                    // 焦点始终留在输入框（aria-activedescendant 模式），选项只需可被程序聚焦
                    tabIndex={-1}
                    // mouseDown 早于 input blur，保证点选生效
                    onMouseDown={(e) => {
                      e.preventDefault();
                      pick(venue);
                    }}
                    className={cn(
                      "cursor-pointer px-2 py-1.5 text-xs",
                      index === activeIndex && "bg-paper-200",
                    )}
                  >
                    <span className="block truncate text-(--fg-1)">{venue.name}</span>
                    {venue.abbr && (
                      <span className="block truncate font-mono text-[10px] text-(--fg-3)">
                        {venue.abbr}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
      <p className="text-[11px] text-(--fg-4)">
        {full ? `最多选择 ${MAX_VENUES} 本期刊` : "输入刊名，从候选中添加"}
      </p>
      {selected.map((id) => (
        <FacetCheckRow
          key={id}
          label={names[id] ?? `期刊 #${id}`}
          checked
          onToggle={() => onRemove(id)}
        />
      ))}
    </div>
  );
}
