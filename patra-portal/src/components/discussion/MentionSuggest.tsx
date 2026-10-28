"use client";

import { Loader2 } from "lucide-react";
import {
  type CSSProperties,
  type KeyboardEvent,
  type RefObject,
  type TextareaHTMLAttributes,
  useEffect,
  useId,
  useLayoutEffect,
  useState,
} from "react";
import { Avatar } from "@/components/user/Avatar";
import { caretCoordinates } from "@/lib/discussion/caret";
import { isImeKey } from "@/lib/discussion/ime";
import {
  findMentionQuery,
  type MentionQuery,
  replaceMention,
  shouldAutoClose,
} from "@/lib/discussion/mention-query";
import { COMPOSER_COPY, mentionNotFound } from "@/lib/discussion/messages";
import { useMentionSuggestions } from "@/lib/discussion/queries";
import type { MentionSuggestion, Subject } from "@/lib/interaction/discussion-schema";
import { cn } from "@/lib/utils";

/// 输入停下 200ms 再查
const DEBOUNCE_MS = 200;
/// 弹层宽 296px，右边越界时向左收，留 8px
const POPUP_WIDTH = 296;
const EDGE = 8;

function useDebounced<T>(value: T, delay: number): T {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return settled;
}

interface UseMentionSuggestInput {
  subject: Subject;
  value: string;
  onChange: (value: string) => void;
  textareaRef: RefObject<HTMLTextAreaElement | null>;
}

/// 接到 `MarkdownEditor` 上的几个口子
export interface MentionEditorProps {
  overlay: React.ReactNode;
  onTextareaKeyDown: (event: KeyboardEvent<HTMLTextAreaElement>) => boolean;
  onCaretChange: () => void;
  comboboxProps: TextareaHTMLAttributes<HTMLTextAreaElement>;
}

/// @ 联想（简报 §3.6）：按光标前的 `@查询词` 打开，参与者在前、其他用户在后，`↑↓` 移动、`Enter` / `Tab` 选中、
/// `Esc` 关闭；没有结果且查询词超过 6 个字自动收起。弹层锚在光标行下方（窄屏占满编辑框宽）
export function useMentionSuggest({
  subject,
  value,
  onChange,
  textareaRef,
}: UseMentionSuggestInput): { editorProps: MentionEditorProps } {
  const listId = useId();
  const [caret, setCaret] = useState<number | null>(null);
  const [dismissedAt, setDismissedAt] = useState<number | null>(null);
  const [activeIndex, setActiveIndex] = useState(0);
  const [anchor, setAnchor] = useState<{ top: number; left: number } | null>(null);

  const query: MentionQuery | null = caret === null ? null : findMentionQuery(value, caret);
  const wanted = query !== null && dismissedAt !== query.start;
  const debounced = useDebounced(wanted ? query.query : null, DEBOUNCE_MS);
  const suggestions = useMentionSuggestions(subject, debounced, wanted);
  const items = suggestions.data ?? [];
  const settled =
    wanted &&
    debounced === query.query &&
    !suggestions.isFetching &&
    !suggestions.isPlaceholderData;
  const autoClosed = settled && suggestions.isSuccess && shouldAutoClose(query.query, items.length);
  const open = wanted && !autoClosed;

  // 换了一批候选，高亮回到第一个
  // biome-ignore lint/correctness/useExhaustiveDependencies: 只在候选变化时重置
  useEffect(() => setActiveIndex(0), [suggestions.data]);

  // 打开时量 `@` 的位置：相对编辑框（文本框的定位父元素），右边越界时向左收
  const start = open ? query.start : null;
  useLayoutEffect(() => {
    const element = textareaRef.current;
    if (start === null || !element) {
      setAnchor(null);
      return;
    }
    const at = caretCoordinates(element, start);
    const width = element.offsetParent?.clientWidth ?? element.clientWidth;
    setAnchor({
      top: element.offsetTop + at.top + at.height + 4,
      left: Math.max(EDGE, Math.min(element.offsetLeft + at.left, width - POPUP_WIDTH - EDGE)),
    });
  }, [start, textareaRef]);

  const pick = (person: MentionSuggestion) => {
    if (!query) {
      return;
    }
    const next = replaceMention(value, query, person.username);
    onChange(next.value);
    setCaret(next.caret);
    requestAnimationFrame(() => {
      const element = textareaRef.current;
      element?.focus();
      element?.setSelectionRange(next.caret, next.caret);
    });
  };

  const onTextareaKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>): boolean => {
    if (!open || isImeKey(event)) {
      return false;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      setDismissedAt(query.start);
      return true;
    }
    if (items.length === 0) {
      return false;
    }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const step = event.key === "ArrowDown" ? 1 : -1;
      setActiveIndex((index) => (index + step + items.length) % items.length);
      return true;
    }
    const plain = !event.metaKey && !event.ctrlKey && !event.altKey && !event.shiftKey;
    if ((event.key === "Enter" || event.key === "Tab") && plain) {
      event.preventDefault();
      const person = items[activeIndex];
      if (person) {
        pick(person);
      }
      return true;
    }
    return false;
  };

  const onCaretChange = () => {
    const element = textareaRef.current;
    if (!element || element.selectionStart !== element.selectionEnd) {
      setCaret(null);
      return;
    }
    setCaret(element.selectionStart);
  };

  const optionId = (index: number) => `${listId}-option-${index}`;
  const comboboxProps: TextareaHTMLAttributes<HTMLTextAreaElement> = {
    role: "combobox",
    "aria-autocomplete": "list",
    "aria-expanded": open && items.length > 0,
    "aria-controls": open && items.length > 0 ? listId : undefined,
    "aria-activedescendant": open && items.length > 0 ? optionId(activeIndex) : undefined,
    onBlur: () => setCaret(null),
  };

  const loading = wanted && (debounced !== query?.query || suggestions.isFetching);
  const overlay =
    open && anchor ? (
      <div
        className="absolute top-(--ms-top) left-(--ms-left) z-[65] w-74 rounded-lg border border-border-subtle bg-bg-elevated p-1.5 shadow-lg max-[880px]:right-2 max-[880px]:left-2! max-[880px]:w-auto"
        style={{ "--ms-top": `${anchor.top}px`, "--ms-left": `${anchor.left}px` } as CSSProperties}
      >
        {items.length > 0 && (
          <SuggestionList
            id={listId}
            items={items}
            activeIndex={activeIndex}
            optionId={optionId}
            onHover={setActiveIndex}
            onPick={pick}
          />
        )}
        {suggestions.isError ? (
          <Note>{COMPOSER_COPY.mentionError}</Note>
        ) : loading ? (
          <Note>
            <Loader2 className="size-3 animate-spin" aria-hidden="true" />
            {COMPOSER_COPY.mentionLoading}
          </Note>
        ) : (
          items.length === 0 && (
            <Note>
              {query.query === "" ? COMPOSER_COPY.mentionHint : mentionNotFound(query.query)}
            </Note>
          )
        )}
      </div>
    ) : null;

  return { editorProps: { overlay, onTextareaKeyDown, onCaretChange, comboboxProps } };
}

function Note({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-2 p-2 text-xs leading-4.5 text-fg-3">{children}</div>
  );
}

interface SuggestionListProps {
  id: string;
  items: readonly MentionSuggestion[];
  activeIndex: number;
  optionId: (index: number) => string;
  onHover: (index: number) => void;
  onPick: (person: MentionSuggestion) => void;
}

/// 候选列表：参与者一组、其他用户一组，两组之间 1px 分隔
function SuggestionList({
  id,
  items,
  activeIndex,
  optionId,
  onHover,
  onPick,
}: SuggestionListProps) {
  const groups = (["PARTICIPANT", "OTHER"] as const)
    .map((group) => ({
      group,
      rows: items.flatMap((person, index) => (person.group === group ? [{ person, index }] : [])),
    }))
    .filter((group) => group.rows.length > 0);
  return (
    <div id={id} role="listbox" aria-label="可以提及的人">
      {groups.map(({ group, rows }, groupIndex) => (
        // biome-ignore lint/a11y/useSemanticElements: listbox 里的分组按 ARIA 用 role="group"，原生的表单分组元素不能放进 listbox
        <div key={group} role="group" aria-labelledby={`${id}-${group}`}>
          {groupIndex > 0 && <div className="-mx-1.5 my-1 h-px bg-border-subtle" />}
          <div
            id={`${id}-${group}`}
            className="px-2 pt-1.5 pb-1 font-mono text-3xs tracking-caps text-fg-3 uppercase"
          >
            {group === "PARTICIPANT"
              ? COMPOSER_COPY.mentionParticipants
              : COMPOSER_COPY.mentionOthers}
          </div>
          {rows.map(({ person, index }) => (
            <div
              key={person.userId}
              id={optionId(index)}
              role="option"
              aria-selected={index === activeIndex}
              tabIndex={-1}
              onMouseDown={(event) => event.preventDefault()}
              onMouseEnter={() => onHover(index)}
              onClick={() => onPick(person)}
              onKeyDown={() => {}}
              className={cn(
                "grid min-h-10 cursor-pointer grid-cols-[24px_minmax(0,1fr)] items-center gap-2.5 rounded-md px-2 max-[880px]:min-h-11",
                index === activeIndex && "bg-bg-sunken",
              )}
            >
              <Avatar identity={person} size={24} />
              <span className="flex min-w-0 items-baseline gap-2">
                <span className="truncate text-sm text-fg-1">{person.displayName}</span>
                <span className="font-mono text-2xs whitespace-nowrap text-fg-3">
                  @{person.username}
                </span>
              </span>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
