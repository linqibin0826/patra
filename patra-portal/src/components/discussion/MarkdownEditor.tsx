"use client";

import {
  AtSign,
  Bold,
  Code,
  Italic,
  Link2,
  List,
  ListOrdered,
  type LucideIcon,
  Quote,
} from "lucide-react";
import {
  type KeyboardEvent,
  type ReactNode,
  type RefObject,
  type TextareaHTMLAttributes,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { MarkdownBody } from "@/components/discussion/MarkdownBody";
import { isImeKey } from "@/lib/discussion/ime";
import {
  COMMENT_LIMITS,
  type LimitState,
  limitState,
  measure,
  mentionCandidates,
} from "@/lib/discussion/limits";
import { COMPOSER_COPY, overLimitMessage } from "@/lib/discussion/messages";
import { applyToolbar, type Selection, type ToolbarAction } from "@/lib/discussion/toolbar";
import type { Subject } from "@/lib/interaction/discussion-schema";
import { cn } from "@/lib/utils";

/// 打开 @ 联想要的上下文：联想接口按对象列参与者
export interface MentionConfig {
  subject: Subject;
}

const TOOLS: { action: ToolbarAction; label: string; icon: LucideIcon }[] = [
  { action: "bold", label: "粗体", icon: Bold },
  { action: "italic", label: "斜体", icon: Italic },
  { action: "quote", label: "引用", icon: Quote },
  { action: "bullet", label: "无序列表", icon: List },
  { action: "ordered", label: "有序列表", icon: ListOrdered },
  { action: "code", label: "行内代码", icon: Code },
  { action: "link", label: "链接", icon: Link2 },
  { action: "mention", label: "提及", icon: AtSign },
];

/// 文本框自动增高的上限：16 行（行高 24px）+ 上下内边距
const MAX_TEXTAREA_HEIGHT = 16 * 24 + 24;

const formatCount = (value: number) => value.toLocaleString("en-US");

/// 编辑器当前内容的计数与能不能提交
export function useEditorMetrics(
  value: string,
  limits: { graphemes: number; bytes: number },
  countMentions: boolean,
): { graphemes: number; state: LimitState; mentionOver: boolean; blocked: boolean } {
  const m = measure(value);
  const state = limitState(m, limits);
  const mentionOver = countMentions && mentionCandidates(value).length > COMMENT_LIMITS.mentions;
  return {
    graphemes: m.graphemes,
    state,
    mentionOver,
    blocked: state === "over-graphemes" || state === "over-bytes" || mentionOver,
  };
}

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
  /// 文本框的读屏名称
  label: string;
  placeholder: string;
  limits: { graphemes: number; bytes: number };
  /// 超字素上限时的说明：`评论最多 5,000 字` / `正文最多 2,000 字`
  limitLabel: "评论" | "正文";
  /// 评论里开 @ 联想；投稿经验正文传 false（不解析 @，没有 @ 按钮）
  mentions: MentionConfig | false;
  textareaRef?: RefObject<HTMLTextAreaElement | null>;
  disabled?: boolean;
  readOnly?: boolean;
  autoFocus?: boolean;
  /// `⌘ Enter` / `Ctrl Enter`
  onSubmitShortcut?: () => void;
  /// 文本框下方、底栏之上的内容（@ 联想弹层挂在这里）
  overlay?: ReactNode;
  /// 文本框的按键与光标变化，@ 联想用
  onTextareaKeyDown?: (event: KeyboardEvent<HTMLTextAreaElement>) => boolean;
  onCaretChange?: () => void;
  /// 文本框上的组合框属性（@ 联想打开时）
  comboboxProps?: TextareaHTMLAttributes<HTMLTextAreaElement>;
}

/// Markdown 编辑器（简报 §3.5）：编写 / 预览标签、工具条、自动增高的文本框、字素与字节计数。讨论区的发表、回复、
/// 编辑与 PAP-125 的经验正文共用；@ 联想由 `CommentComposer` 经 `overlay` 等几个口子接进来
export function MarkdownEditor({
  value,
  onChange,
  label,
  placeholder,
  limits,
  limitLabel,
  mentions,
  textareaRef,
  disabled = false,
  readOnly = false,
  autoFocus = false,
  onSubmitShortcut,
  overlay,
  onTextareaKeyDown,
  onCaretChange,
  comboboxProps,
}: MarkdownEditorProps) {
  const ids = useId();
  const [tab, setTab] = useState<"write" | "preview">("write");
  const ownRef = useRef<HTMLTextAreaElement | null>(null);
  const box = textareaRef ?? ownRef;
  const pendingSelection = useRef<Selection | null>(null);
  const metrics = useEditorMetrics(value, limits, mentions !== false);

  // 自动增高到 16 行，之后内部滚动；工具条改值之后恢复选区
  useLayoutEffect(() => {
    const element = box.current;
    if (!element) {
      return;
    }
    element.style.height = "auto";
    element.style.height = `${Math.min(element.scrollHeight, MAX_TEXTAREA_HEIGHT)}px`;
    const selection = pendingSelection.current;
    if (selection) {
      pendingSelection.current = null;
      element.focus();
      element.setSelectionRange(selection.start, selection.end);
      onCaretChange?.();
    }
  });

  const apply = (action: ToolbarAction) => {
    const element = box.current;
    if (!element || disabled || readOnly) {
      return;
    }
    const next = applyToolbar(
      value,
      { start: element.selectionStart, end: element.selectionEnd },
      action,
    );
    pendingSelection.current = next.selection;
    onChange(next.value);
  };

  const counterTone =
    metrics.state === "near"
      ? "text-amber-700"
      : metrics.state === "ok"
        ? "text-fg-3"
        : "text-status-danger";
  const warning =
    metrics.state === "over-graphemes"
      ? overLimitMessage(limitLabel, limits.graphemes)
      : metrics.state === "over-bytes"
        ? COMPOSER_COPY.tooLong
        : metrics.mentionOver
          ? COMPOSER_COPY.tooManyMentions
          : null;
  const tools = TOOLS.filter((tool) => tool.action !== "mention" || mentions !== false);
  const hint = mentions === false && limitLabel === "正文" ? COMPOSER_COPY.experienceHint : null;

  return (
    <div
      className={cn(
        "relative flex flex-col rounded-xl border bg-bg-elevated transition-[border-color,box-shadow] duration-200",
        disabled
          ? "border-border-default bg-bg-sunken"
          : "border-border-strong shadow-inset ring-clay-500/15 focus-within:border-border-focus focus-within:ring-4",
        warning && !disabled && "border-status-danger",
      )}
    >
      <div className="flex min-h-10 items-center justify-between gap-3 border-b border-border-subtle pr-2 pl-1">
        <div role="tablist" aria-label="编辑方式" className="flex shrink-0 gap-0.5">
          {(["write", "preview"] as const).map((name) => (
            <button
              key={name}
              type="button"
              role="tab"
              id={`${ids}-${name}-tab`}
              aria-selected={tab === name}
              aria-controls={`${ids}-${name}`}
              onClick={() => setTab(name)}
              className="-mb-px h-10 border-b-2 border-transparent px-2.5 text-sm font-medium whitespace-nowrap text-fg-3 hover:text-fg-1 aria-selected:border-clay-500 aria-selected:text-fg-1"
            >
              {name === "write" ? "编写" : "预览"}
            </button>
          ))}
        </div>
        <div className="ml-auto flex min-w-0 items-center gap-0.5 overflow-x-auto [scrollbar-width:none]">
          {tools.map(({ action, label: toolLabel, icon: Icon }) => (
            <button
              key={action}
              type="button"
              aria-label={toolLabel}
              title={toolLabel}
              aria-disabled={tab !== "write" || disabled || readOnly}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => tab === "write" && apply(action)}
              className="inline-flex size-7 shrink-0 items-center justify-center rounded-md text-fg-3 hover:bg-bg-sunken hover:text-fg-1 aria-disabled:cursor-default aria-disabled:bg-transparent aria-disabled:text-fg-4 max-[880px]:size-9"
            >
              <Icon className="size-4" strokeWidth={1.75} aria-hidden="true" />
            </button>
          ))}
        </div>
      </div>

      {tab === "write" ? (
        <div id={`${ids}-write`} role="tabpanel" aria-labelledby={`${ids}-write-tab`}>
          <textarea
            ref={box}
            value={value}
            aria-label={label}
            placeholder={placeholder}
            disabled={disabled}
            readOnly={readOnly}
            // biome-ignore lint/a11y/noAutofocus: 编辑器展开时焦点进文本框（简报 §6）
            autoFocus={autoFocus}
            rows={4}
            onChange={(event) => {
              onChange(event.target.value);
              onCaretChange?.();
            }}
            onSelect={() => onCaretChange?.()}
            onKeyDown={(event) => {
              if (onTextareaKeyDown?.(event)) {
                return;
              }
              if (event.key === "Enter" && (event.metaKey || event.ctrlKey) && !isImeKey(event)) {
                event.preventDefault();
                onSubmitShortcut?.();
              }
            }}
            {...comboboxProps}
            className="block min-h-28 w-full resize-none bg-transparent px-3.5 py-3 text-md leading-6 whitespace-pre-wrap text-ink-900 outline-none placeholder:text-fg-4 disabled:cursor-not-allowed disabled:text-fg-3 max-[880px]:text-lg"
          />
          {overlay}
        </div>
      ) : (
        <div
          id={`${ids}-preview`}
          role="tabpanel"
          aria-labelledby={`${ids}-preview-tab`}
          className="min-h-28 px-3.5 py-3"
        >
          {value.trim() === "" ? (
            <p className="text-sm text-fg-3">{COMPOSER_COPY.previewEmpty}</p>
          ) : (
            <MarkdownBody source={value} mentions={[]} />
          )}
        </div>
      )}

      <div className="flex items-center justify-between gap-3 px-3.5 pt-1.5 pb-2 text-2xs leading-4 text-fg-3">
        {hint ? (
          <span>{hint}</span>
        ) : (
          <>
            <span className="max-[880px]:hidden">{COMPOSER_COPY.markdownHint}</span>
            <span className="min-[881px]:hidden">{COMPOSER_COPY.markdownHintShort}</span>
          </>
        )}
        <span className="flex shrink-0 items-center gap-2">
          {warning && <span className="text-status-danger">{warning}</span>}
          <span className={cn("font-mono", counterTone)}>
            {formatCount(metrics.graphemes)} / {formatCount(limits.graphemes)}
          </span>
        </span>
      </div>
    </div>
  );
}
