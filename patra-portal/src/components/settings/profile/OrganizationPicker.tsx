"use client";

import { Search, X } from "lucide-react";
import { useEffect, useState } from "react";
import { FIELD_BOX } from "@/components/auth/FormField";
import { HighlightedText } from "@/components/settings/profile/HighlightedText";
import {
  Combobox,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxPopup,
  ComboboxStatus,
} from "@/components/ui/combobox";
import { lengthIssue, type OrganizationValue, TEXT_LIMITS } from "@/lib/identity/profile-form";
import { organizationDetail, organizationTypeLabel } from "@/lib/identity/suggest-labels";
import type { OrganizationSuggestion } from "@/lib/portal-api/suggest";
import { useOrganizationSuggestQuery } from "@/lib/portal-api/suggest-query-hooks";
import { normalizeSuggestQuery, suggestQueryLength } from "@/lib/suggest-query";
import { cn } from "@/lib/utils";

const DEBOUNCE_MS = 250;

/// 下拉里的一项：ROR 候选，或最后固定的「直接使用」
type Option =
  | { type: "ror"; suggestion: OrganizationSuggestion }
  | { type: "free"; name: string; tooLong: boolean };

interface OrganizationPickerProps {
  id: string;
  value: OrganizationValue | null;
  onChange: (value: OrganizationValue | null) => void;
  /// 字段错误文案；有值时输入框描红
  error?: string;
  readOnly?: boolean;
  /// 保存出错时要聚焦的元素：输入态是输入框，卡片态是「更换」（交给 RHF 的 `field.ref`）
  focusRef?: (element: HTMLElement | null) => void;
}

/// 机构选择器（简报 §3.8）：未选时是组合框，搜 ROR 机构（中英文名、别名），列表最后可以直接使用输入的
/// 原文；选中后变成卡片，可「更换」「清除」。输入归一后不足 2 个字不请求，防抖 250ms。
export function OrganizationPicker({
  id,
  value,
  onChange,
  error,
  readOnly,
  focusRef,
}: OrganizationPickerProps) {
  const [changing, setChanging] = useState(false);
  const [input, setInput] = useState("");
  const normalized = normalizeSuggestQuery(input);
  const [debounced, setDebounced] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(normalized), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [normalized]);

  const enough = suggestQueryLength(debounced) >= 2;
  const query = useOrganizationSuggestQuery(enough ? debounced : "");
  const settled = enough && debounced === normalized;
  const suggestions = settled && query.data ? query.data : [];
  const name = input.trim();
  const options: Option[] = [
    ...suggestions.map((suggestion) => ({ type: "ror" as const, suggestion })),
    ...(name === ""
      ? []
      : [
          {
            type: "free" as const,
            name,
            tooLong: lengthIssue(name, TEXT_LIMITS.organizationName) === "TOO_LONG",
          },
        ]),
  ];

  let note: string | null = null;
  if (suggestQueryLength(normalized) === 1) note = "再输入一个字";
  else if (settled && query.isError) note = "机构搜索暂时不可用，可以直接填写名称";
  else if (settled && query.data?.length === 0) note = "没有找到匹配的机构";

  function pick(option: Option | null) {
    if (!option || (option.type === "free" && option.tooLong)) {
      return;
    }
    const { suggestion } = option.type === "ror" ? option : { suggestion: null };
    onChange(
      suggestion
        ? {
            kind: "ror",
            rorId: suggestion.rorId,
            name: suggestion.zhName ?? suggestion.displayName,
            detail: organizationDetail(suggestion),
          }
        : { kind: "free", name },
    );
    setChanging(false);
    setInput("");
  }

  if (value !== null && !changing) {
    return (
      <div className="flex items-start gap-3 rounded-xl border border-border-default bg-bg-elevated py-3 pr-2 pl-4">
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-2">
            <span className="truncate text-base leading-5 font-medium text-fg-1">{value.name}</span>
            {value.kind === "ror" && (
              <span
                title="来自 ROR 机构库"
                className="inline-flex h-4.5 shrink-0 items-center rounded-full border border-border-default px-1.5 font-mono text-3xs tracking-mono text-fg-3"
              >
                ROR
              </span>
            )}
          </div>
          <div className="mt-0.5 text-xs leading-4 text-fg-3">
            {value.kind === "ror" ? (value.detail ?? "ROR 机构库") : "手动填写"}
          </div>
        </div>
        <button
          ref={focusRef}
          type="button"
          disabled={readOnly}
          onClick={() => {
            setInput(value.name);
            setChanging(true);
          }}
          className="min-h-8 px-1.5 text-sm font-medium text-link transition-colors hover:text-link-hover disabled:opacity-55"
        >
          更换
        </button>
        <button
          type="button"
          aria-label="清除机构"
          disabled={readOnly}
          onClick={() => {
            onChange(null);
            setChanging(false);
            setInput("");
          }}
          className="inline-flex size-8 items-center justify-center rounded-full text-fg-3 transition-colors hover:bg-paper-200 hover:text-ink-900 disabled:opacity-55"
        >
          <X className="size-4" strokeWidth={1.75} aria-hidden="true" />
        </button>
      </div>
    );
  }

  return (
    <Combobox<Option>
      items={options}
      filter={null}
      value={null}
      inputValue={input}
      onInputValueChange={(next, { reason }) => {
        // 不接受 base-ui 自己改输入框的两种情况：选中候选时回填条目名（选中后这里清空或换成卡片）；
        // 候选收起后把输入同步成已选值（没有已选值就是清空）——Esc 只收起、保留输入，收起后再按
        // Esc 才清空（简报 §3.8，base-ui 以 `escape-key` 处理）
        if (reason !== "item-press" && reason !== "input-clear") setInput(next);
      }}
      onValueChange={(option) => pick(option)}
      itemToStringLabel={(option) =>
        option.type === "ror"
          ? (option.suggestion.zhName ?? option.suggestion.displayName)
          : option.name
      }
      isItemEqualToValue={(a, b) => a === b}
      readOnly={readOnly}
    >
      <div className={FIELD_BOX}>
        <Search
          className="mr-1.5 size-4 shrink-0 text-fg-3"
          strokeWidth={1.75}
          aria-hidden="true"
        />
        <ComboboxInput
          ref={focusRef}
          id={id}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined}
          placeholder="搜索医院、高校或研究机构，中英文都可以"
          autoFocus={changing}
        />
      </div>
      <ComboboxPopup
        aria-busy={query.isFetching || undefined}
        top={
          <>
            {query.isFetching && (
              <div
                aria-hidden="true"
                className="absolute top-0 right-3 left-3 h-0.5 rounded-full motion-safe:animate-pulse bg-clay-400"
              />
            )}
            {note && <div className="px-2.5 py-2.5 text-sm leading-5 text-fg-3">{note}</div>}
            <ComboboxStatus className="sr-only">
              {settled && suggestions.length > 0 ? `找到 ${suggestions.length} 个机构` : ""}
            </ComboboxStatus>
          </>
        }
      >
        <ComboboxList>
          {(option: Option) =>
            option.type === "ror" ? (
              <ComboboxItem key={option.suggestion.rorId} value={option}>
                <OrganizationOption suggestion={option.suggestion} query={normalized} />
              </ComboboxItem>
            ) : (
              <ComboboxItem
                key="free"
                value={option}
                disabled={option.tooLong}
                className={cn(
                  suggestions.length > 0 && "mt-1.5 border-t border-border-subtle pt-3.5",
                )}
              >
                <div className="min-w-0 flex-1">
                  <div className="text-base leading-5 text-fg-1">直接使用「{option.name}」</div>
                  <div className="mt-0.5 text-xs leading-4 text-fg-3">
                    {option.tooLong ? "名称最长 200 字" : "找不到你的机构时，可以手动填写名称"}
                  </div>
                </div>
              </ComboboxItem>
            )
          }
        </ComboboxList>
      </ComboboxPopup>
    </Combobox>
  );
}

/// 一条机构候选：主行中文名（没有就英文名，命中部分加粗），副行英文名 · 城市 · 国家，命中的名称与主副行
/// 都不同时追加「经「…」命中」；右侧类型小标。
function OrganizationOption({
  suggestion,
  query,
}: {
  suggestion: OrganizationSuggestion;
  query: string;
}) {
  const main = suggestion.zhName ?? suggestion.displayName;
  const detail = organizationDetail(suggestion);
  const matched =
    suggestion.matchedName &&
    suggestion.matchedName !== main &&
    suggestion.matchedName !== suggestion.displayName
      ? `经「${suggestion.matchedName}」命中`
      : null;
  const sub = [detail, matched].filter(Boolean).join(" · ");
  const kind = organizationTypeLabel(suggestion.types);
  return (
    <>
      <div className="min-w-0 flex-1">
        <div className="text-base leading-5 text-fg-1">
          <HighlightedText text={main} query={query} />
        </div>
        {sub && <div className="mt-0.5 text-xs leading-4 text-fg-3">{sub}</div>}
      </div>
      {kind && <span className="shrink-0 pt-0.5 font-mono text-3xs text-fg-3">{kind}</span>}
    </>
  );
}
