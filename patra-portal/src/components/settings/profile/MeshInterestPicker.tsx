"use client";

import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { arrayMove, rectSortingStrategy, SortableContext, useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical, Search, X } from "lucide-react";
import { type KeyboardEvent, useEffect, useState } from "react";
import { FIELD_BOX } from "@/components/auth/FormField";
import { FieldError } from "@/components/settings/profile/FieldError";
import { HighlightedText } from "@/components/settings/profile/HighlightedText";
import {
  Combobox,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxPopup,
  ComboboxStatus,
} from "@/components/ui/combobox";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { hasCjk, meshCategoryLabel } from "@/lib/identity/suggest-labels";
import type { MeshSuggestion } from "@/lib/portal-api/suggest";
import { useMeshSuggestQuery } from "@/lib/portal-api/suggest-query-hooks";
import { normalizeSuggestQuery, suggestQueryLength } from "@/lib/suggest-query";
import { useMediaQuery } from "@/lib/use-media-query";
import { cn } from "@/lib/utils";

const DEBOUNCE_MS = 250;
/// 最多 10 个（release spec 2.1.2）
const MAX_INTERESTS = 10;

/// 已选的主题词：UI + 英文名
export interface Interest {
  ui: string;
  name: string;
}

interface MeshInterestPickerProps {
  id: string;
  value: Interest[];
  onChange: (value: Interest[]) => void;
  /// 下标 → 这一项的错误文案（保存时后端报的 `researchInterests[i]`）
  errors?: Record<number, string>;
  readOnly?: boolean;
  /// 保存出错时要聚焦的搜索框（交给 RHF 的 `field.ref`）
  focusRef?: (element: HTMLElement | null) => void;
}

/// 关注领域选择器（简报 §3.9）：上方是有序的已选胶囊（可移除、可排序），下方是 MeSH 搜索组合框，只用英文。
/// 排序：桌面拖动手柄（dnd-kit），聚焦手柄按 Alt + 方向键；≤ 880px 不能拖，手柄打开「上移 / 下移 / 移除」菜单。
export function MeshInterestPicker({
  id,
  value,
  onChange,
  errors = {},
  readOnly,
  focusRef,
}: MeshInterestPickerProps) {
  const narrow = useMediaQuery("(max-width: 880px)");
  const [input, setInput] = useState("");
  const normalized = normalizeSuggestQuery(input);
  const [debounced, setDebounced] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(normalized), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [normalized]);

  const cjk = hasCjk(normalized);
  const searchable = !hasCjk(debounced) && suggestQueryLength(debounced) >= 2;
  const query = useMeshSuggestQuery(searchable ? debounced : "");
  const settled = searchable && debounced === normalized;
  const suggestions = settled && query.data ? query.data : [];
  const chosen = new Set(value.map((interest) => interest.ui));
  const full = value.length >= MAX_INTERESTS;

  let note: string | null = null;
  if (cjk) note = "关注领域只支持英文主题词，请用英文搜索";
  else if (settled && query.isError) note = "主题词搜索暂时不可用，请稍后再试";
  else if (settled && query.data?.length === 0) note = "没有找到匹配的主题词";

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }));

  /// 把第 `from` 项挪到 `to`
  function move(from: number, to: number) {
    if (to >= 0 && to < value.length && from !== to) {
      onChange(arrayMove(value, from, to));
    }
  }

  function onDragEnd({ active, over }: DragEndEvent) {
    if (over && active.id !== over.id) {
      move(
        value.findIndex((interest) => interest.ui === active.id),
        value.findIndex((interest) => interest.ui === over.id),
      );
    }
  }

  function pick(suggestion: MeshSuggestion | null) {
    if (!suggestion || chosen.has(suggestion.ui) || full) {
      return;
    }
    onChange([...value, { ui: suggestion.ui, name: suggestion.name }]);
    setInput("");
  }

  const errorLines = value.flatMap((interest, index) =>
    errors[index] ? [`${interest.name}：${errors[index]}`] : [],
  );

  return (
    <div className="flex min-w-0 flex-col gap-3">
      {value.length > 0 && (
        <DndContext
          sensors={narrow ? [] : sensors}
          collisionDetection={closestCenter}
          onDragEnd={onDragEnd}
        >
          <SortableContext
            items={value.map((interest) => interest.ui)}
            strategy={rectSortingStrategy}
          >
            <ul aria-label="已选主题词，可排序" className="flex flex-wrap gap-2">
              {value.map((interest, index) => (
                <InterestChip
                  key={interest.ui}
                  interest={interest}
                  index={index}
                  count={value.length}
                  narrow={narrow}
                  invalid={Boolean(errors[index])}
                  readOnly={readOnly}
                  onMove={move}
                  onRemove={() => onChange(value.filter((_, i) => i !== index))}
                />
              ))}
            </ul>
          </SortableContext>
        </DndContext>
      )}
      {errorLines.map((line) => (
        <FieldError key={line}>{line}</FieldError>
      ))}
      <Combobox<MeshSuggestion>
        items={suggestions}
        filter={null}
        value={null}
        inputValue={input}
        onInputValueChange={(next) => setInput(next)}
        onValueChange={(suggestion) => pick(suggestion)}
        itemToStringLabel={(suggestion) => suggestion.name}
        isItemEqualToValue={(a, b) => a.ui === b.ui}
        disabled={full}
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
            lang="en"
            aria-label="搜索 MeSH 主题词"
            placeholder={
              full ? "最多 10 个，先移除一些再添加" : "用英文搜索 MeSH 主题词，如 Heart Failure"
            }
          />
        </div>
        <ComboboxPopup
          aria-busy={query.isFetching || undefined}
          top={
            <>
              {query.isFetching && (
                <div
                  aria-hidden="true"
                  className="absolute top-0 right-3 left-3 h-0.5 rounded-full bg-clay-400 motion-safe:animate-pulse"
                />
              )}
              {note && <div className="px-2.5 py-2.5 text-sm leading-5 text-fg-3">{note}</div>}
              <ComboboxStatus className="sr-only">
                {suggestions.length > 0 ? `找到 ${suggestions.length} 个主题词` : ""}
              </ComboboxStatus>
            </>
          }
        >
          <ComboboxList>
            {(suggestion: MeshSuggestion) => (
              <ComboboxItem
                key={suggestion.ui}
                value={suggestion}
                disabled={chosen.has(suggestion.ui)}
              >
                <div className="min-w-0 flex-1" lang="en">
                  <div className="text-base leading-5 text-fg-1">
                    <HighlightedText text={suggestion.name} query={normalized} />
                  </div>
                  {suggestion.matchedTerm && (
                    <div className="mt-0.5 text-xs leading-4 text-fg-3">
                      via {suggestion.matchedTerm}
                    </div>
                  )}
                </div>
                <span className="shrink-0 pt-0.5 font-mono text-3xs text-fg-3">
                  {chosen.has(suggestion.ui) ? "已添加" : meshCategoryLabel(suggestion.treeNumbers)}
                </span>
              </ComboboxItem>
            )}
          </ComboboxList>
        </ComboboxPopup>
      </Combobox>
    </div>
  );
}

interface InterestChipProps {
  interest: Interest;
  index: number;
  count: number;
  narrow: boolean;
  invalid: boolean;
  readOnly?: boolean;
  onMove: (from: number, to: number) => void;
  onRemove: () => void;
}

/// Alt + 方向键 → 位移：右 / 下后移一位，左 / 上前移一位
const KEY_OFFSET: Record<string, number> = {
  ArrowRight: 1,
  ArrowDown: 1,
  ArrowLeft: -1,
  ArrowUp: -1,
};

/// 一个已选胶囊：手柄（桌面拖动 + 键盘排序；窄屏打开菜单）、名称、移除
function InterestChip({
  interest,
  index,
  count,
  narrow,
  invalid,
  readOnly,
  onMove,
  onRemove,
}: InterestChipProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: interest.ui,
    disabled: narrow || readOnly,
  });
  const handleLabel = `调整 ${interest.name} 的位置`;

  function onHandleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    const offset = KEY_OFFSET[event.key];
    if (event.altKey && offset !== undefined) {
      event.preventDefault();
      onMove(index, index + offset);
    }
  }

  const handleClass =
    "inline-flex size-6 items-center justify-center rounded-full text-fg-3 transition-colors hover:bg-paper-200 hover:text-ink-900";

  return (
    <li
      ref={setNodeRef}
      // dnd-kit 拖动时的位移只能写成行内样式（每一帧的数值不同）
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={cn("relative", isDragging && "z-10 opacity-80")}
    >
      <span
        lang="en"
        className={cn(
          "inline-flex h-8 items-center gap-1.5 rounded-full border bg-paper-50 pr-1.5 pl-1.5 text-sm text-fg-1",
          invalid ? "border-status-danger" : "border-border-default",
        )}
      >
        {narrow ? (
          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label={handleLabel}
              disabled={readOnly}
              className={handleClass}
            >
              <GripVertical className="size-3.5" aria-hidden="true" />
            </DropdownMenuTrigger>
            <DropdownMenuContent className="min-w-35">
              <DropdownMenuItem disabled={index === 0} onClick={() => onMove(index, index - 1)}>
                上移
              </DropdownMenuItem>
              <DropdownMenuItem
                disabled={index === count - 1}
                onClick={() => onMove(index, index + 1)}
              >
                下移
              </DropdownMenuItem>
              <DropdownMenuItem onClick={onRemove}>移除</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        ) : (
          <button
            type="button"
            aria-label={handleLabel}
            disabled={readOnly}
            {...attributes}
            {...listeners}
            onKeyDown={onHandleKeyDown}
            className={cn(handleClass, "cursor-grab touch-none active:cursor-grabbing")}
          >
            <GripVertical className="size-3.5" aria-hidden="true" />
          </button>
        )}
        <span>{interest.name}</span>
        <button
          type="button"
          aria-label={`移除 ${interest.name}`}
          disabled={readOnly}
          onClick={onRemove}
          className="inline-flex size-5.5 items-center justify-center rounded-full text-fg-3 transition-colors hover:bg-paper-200 hover:text-ink-900"
        >
          <X className="size-3.25" strokeWidth={2} aria-hidden="true" />
        </button>
      </span>
    </li>
  );
}
