"use client";

import { CalendarDays } from "lucide-react";
import { type KeyboardEvent, type Ref, useEffect, useRef, useState } from "react";
import { Calendar } from "@/components/ui/calendar";
import { ResponsivePopover } from "@/components/ui/popover";
import { daysBetween, parseDateInput } from "@/lib/date-input";
import { cn } from "@/lib/utils";

// 日期选择（画布「表单控件重设计」）：输入框可直接敲，失焦或回车时规范成 `YYYY-MM-DD`，认不出的原文交给表单
// 由 schema 报错；右端按钮（或输入框里按 ↓）打开日历，桌面是浮层、≤ 880px 是底部抽屉（`ResponsivePopover`）。

interface DatePickerProps {
  id: string;
  /// `""`、`YYYY-MM-DD`，或认不出的原文
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  /// 北京时间的今天，可选上限
  today: string;
  /// 最早可选日
  min: string;
  /// 参照日（如投稿日）：着色、之前的日子不可选，页脚写「距{label} N 天」
  reference?: { date: string; label: string };
  disabled?: boolean;
  invalid?: boolean;
  describedBy?: string;
  autoFocus?: boolean;
  inputRef?: Ref<HTMLInputElement>;
  /// 窄屏抽屉的标题（字段名）
  sheetTitle: string;
}

const toDate = (iso: string): Date => {
  const [year = 1990, month = 1, day = 1] = iso.split("-").map(Number);
  return new Date(year, month - 1, day);
};

const pad = (n: number) => String(n).padStart(2, "0");
const toIso = (date: Date): string =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

/// 合法的 `YYYY-MM-DD` 才当日期用
const validDate = (value: string | undefined): string | undefined =>
  value && parseDateInput(value) === value ? value : undefined;

export function DatePicker({
  id,
  value,
  onChange,
  onBlur,
  today,
  min,
  reference,
  disabled,
  invalid,
  describedBy,
  autoFocus,
  inputRef,
  sheetTitle,
}: DatePickerProps) {
  const [text, setText] = useState(value);
  const [open, setOpen] = useState(false);
  const selected = validDate(value);
  const referenceDate = validDate(reference?.date);
  const earliest = referenceDate && referenceDate > min ? referenceDate : min;
  // 日历打开时停在哪个月：已选日，或参照日，或今天；夹在可选范围内（参照日改晚后，旧的选中日可能已在范围外）
  const startOf = () => {
    const base = selected ?? referenceDate ?? today;
    return toDate(base < earliest ? earliest : base > today ? today : base);
  };
  const [month, setMonth] = useState(startOf);
  const boxRef = useRef<HTMLDivElement>(null);
  const ownInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => setText(value), [value]);

  const commit = () => {
    const next = parseDateInput(text) ?? text.trim();
    setText(next);
    if (next !== value) {
      onChange(next);
    }
  };

  const openCalendar = () => {
    setMonth(startOf());
    setOpen(true);
  };

  /// 从日历选定：等同于敲完离开，交给表单后补一次失焦，好让点日历按钮时冒出的校验错误跟着更新
  const choose = (next: string) => {
    setText(next);
    onChange(next);
    onBlur?.();
    setOpen(false);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      commit();
    } else if (event.key === "ArrowDown" && !open) {
      event.preventDefault();
      openCalendar();
    }
  };

  let footNote = "";
  if (reference && referenceDate) {
    footNote =
      selected && selected >= referenceDate
        ? `距${reference.label} ${daysBetween(referenceDate, selected)} 天`
        : `${reference.label}日 ${referenceDate.slice(5)} 起可选`;
  }
  const todayAllowed = today >= earliest;

  return (
    <>
      <div
        ref={boxRef}
        className={cn(
          "flex h-11 items-center gap-1 rounded-lg border border-border-strong bg-bg-elevated pr-1.5 pl-4 shadow-inset ring-clay-500/15 transition-[border-color,box-shadow,background-color] duration-200 hover:border-ink-500 has-[input:focus]:border-border-focus has-[input:focus]:ring-4 has-[input:disabled]:border-border-default has-[input:disabled]:bg-bg-sunken has-[input:disabled]:shadow-none",
          open && "border-border-focus ring-4",
          invalid && "border-status-danger ring-rust-500/15 has-[input:focus]:border-status-danger",
        )}
      >
        <input
          id={id}
          ref={(node) => {
            ownInputRef.current = node;
            if (typeof inputRef === "function") {
              inputRef(node);
            } else if (inputRef) {
              inputRef.current = node;
            }
          }}
          type="text"
          inputMode="numeric"
          autoComplete="off"
          placeholder="年-月-日"
          // biome-ignore lint/a11y/noAutofocus: 调用方决定（投稿经验表单展开后焦点进第一个字段）
          autoFocus={autoFocus}
          value={text}
          disabled={disabled}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          onChange={(event) => setText(event.target.value)}
          onBlur={() => {
            commit();
            onBlur?.();
          }}
          onKeyDown={onKeyDown}
          className="h-full min-w-0 flex-1 bg-transparent p-0 font-mono text-ink-900 text-sm tracking-wide outline-none placeholder:text-fg-4 disabled:cursor-not-allowed disabled:text-fg-2 max-[880px]:text-md"
        />
        <button
          type="button"
          aria-label={`打开日历：${sheetTitle}`}
          aria-haspopup="dialog"
          aria-expanded={open}
          disabled={disabled}
          onClick={() => (open ? setOpen(false) : openCalendar())}
          className={cn(
            "flex size-8 shrink-0 cursor-pointer items-center justify-center rounded-md text-fg-3 transition-colors duration-150 hover:bg-bg-sunken hover:text-fg-1 disabled:cursor-not-allowed disabled:bg-transparent disabled:text-fg-4 max-[880px]:size-10",
            open && "bg-bg-sunken text-fg-1",
          )}
        >
          <CalendarDays className="size-4.5" aria-hidden="true" />
        </button>
      </div>
      <ResponsivePopover
        open={open}
        onOpenChange={setOpen}
        anchor={boxRef}
        finalFocus={ownInputRef}
        title={`${sheetTitle}：选择日期`}
        className="w-78 p-3"
      >
        <p
          aria-hidden="true"
          className="mb-3 hidden font-semibold text-fg-1 text-md leading-6 max-[880px]:block"
        >
          {sheetTitle}
        </p>
        <Calendar
          mode="single"
          required={false}
          selected={selected ? toDate(selected) : undefined}
          onSelect={(date) => date && choose(toIso(date))}
          month={month}
          onMonthChange={setMonth}
          startMonth={toDate(earliest)}
          endMonth={toDate(today)}
          today={toDate(today)}
          disabled={[{ before: toDate(earliest) }, { after: toDate(today) }]}
          modifiers={referenceDate ? { reference: toDate(referenceDate) } : undefined}
        />
        <div className="mt-2.5 flex items-center justify-between gap-2 border-border-subtle border-t pt-2.5 text-fg-3 text-xs">
          <span>{footNote}</span>
          <span className="flex gap-0.5">
            <button
              type="button"
              disabled={!value}
              onClick={() => choose("")}
              className="h-7 cursor-pointer rounded-md px-2 font-medium text-fg-2 text-sm hover:bg-bg-sunken hover:text-fg-1 disabled:cursor-not-allowed disabled:bg-transparent disabled:text-fg-4"
            >
              清除
            </button>
            <button
              type="button"
              disabled={!todayAllowed || selected === today}
              onClick={() => choose(today)}
              className="h-7 cursor-pointer rounded-md px-2 font-medium text-link text-sm hover:bg-bg-sunken disabled:cursor-not-allowed disabled:bg-transparent disabled:text-fg-4"
            >
              今天
            </button>
          </span>
        </div>
      </ResponsivePopover>
    </>
  );
}
