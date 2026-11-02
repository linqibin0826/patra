"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import type { ChangeEvent, ComponentProps } from "react";
import { DayPicker, type DropdownProps } from "react-day-picker";
import { zhCN } from "react-day-picker/locale";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

// shadcn base 版 calendar 的改写（画布「表单控件重设计」）：react-day-picker，中文、周一起始；
// 标题是年、月两个下拉，下拉换成门户 Select（react-day-picker 默认是原生 select），右侧上月、下月两个箭头。

/// 年、月下拉：把 react-day-picker 给的选项交给门户 Select，选中后按它要的 `change` 事件形状回调
function CalendarDropdown({ options = [], value, onChange, disabled, ...rest }: DropdownProps) {
  const items = options.map((option) => ({ value: String(option.value), label: option.label }));
  return (
    <Select
      items={items}
      value={value === undefined ? null : String(value)}
      disabled={disabled}
      onValueChange={(next) => {
        // react-day-picker 只读 `event.target.value`
        onChange?.({ target: { value: next } } as unknown as ChangeEvent<HTMLSelectElement>);
      }}
    >
      <SelectTrigger
        variant="compact"
        aria-label={rest["aria-label"]}
        className="h-8 border-transparent bg-transparent px-2 font-semibold text-md tabular-nums hover:border-transparent hover:bg-bg-sunken data-popup-open:border-transparent data-popup-open:bg-bg-sunken data-popup-open:ring-0"
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent className="min-w-32">
        {options.map((option) => (
          <SelectItem key={option.value} value={String(option.value)} disabled={option.disabled}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

const NAV_BUTTON =
  "flex size-8 cursor-pointer items-center justify-center rounded-md text-fg-2 transition-colors duration-150 hover:bg-bg-sunken hover:text-fg-1 aria-disabled:cursor-not-allowed aria-disabled:bg-transparent aria-disabled:opacity-40 in-data-[variant=sheet]:size-10";

/// 日历：选中项陶土实底，今天数字下方一个陶土小圆点，其他月份的日子用 `fg-3`，不可选的置灰。
/// 修饰符 `reference`（参照日，如投稿日）用浅陶土底
function Calendar({
  className,
  classNames,
  formatters,
  labels,
  components,
  ...props
}: ComponentProps<typeof DayPicker>) {
  return (
    <DayPicker
      locale={zhCN}
      weekStartsOn={1}
      captionLayout="dropdown"
      showOutsideDays
      fixedWeeks
      className={cn("text-fg-1", className)}
      formatters={{
        formatYearDropdown: (date) => `${date.getFullYear()} 年`,
        formatMonthDropdown: (date) => `${date.getMonth() + 1} 月`,
        ...formatters,
      }}
      labels={{
        labelPrevious: () => "上个月",
        labelNext: () => "下个月",
        labelYearDropdown: () => "选择年份",
        labelMonthDropdown: () => "选择月份",
        ...labels,
      }}
      classNames={{
        months: "relative",
        month: "flex flex-col gap-2",
        nav: "absolute top-0 right-0 flex gap-0.5",
        button_previous: NAV_BUTTON,
        button_next: NAV_BUTTON,
        month_caption: "flex h-8 items-center",
        dropdowns: "flex items-center gap-0.5",
        caption_label: "hidden",
        month_grid: "w-full border-collapse",
        weekdays: "grid grid-cols-7",
        weekday: "flex h-7 items-center justify-center font-normal text-fg-3 text-xs",
        weeks: "flex flex-col gap-0.5",
        week: "grid grid-cols-7",
        day: "p-0 text-center",
        day_button:
          "relative flex h-9.5 w-full cursor-pointer items-center justify-center rounded-md text-base text-inherit tabular-nums transition-colors duration-150 hover:bg-bg-sunken disabled:cursor-not-allowed disabled:text-fg-4 disabled:opacity-55 disabled:hover:bg-transparent in-data-[variant=sheet]:h-11 in-data-[variant=sheet]:text-md",
        selected:
          "[&>button]:bg-action-primary [&>button]:font-medium [&>button]:text-fg-on-clay [&>button]:hover:bg-action-primary-hover",
        today:
          "[&:not([data-selected=true])>button]:after:absolute [&:not([data-selected=true])>button]:after:bottom-1.25 [&:not([data-selected=true])>button]:after:left-[calc(50%-2px)] [&:not([data-selected=true])>button]:after:size-1 [&:not([data-selected=true])>button]:after:rounded-full [&:not([data-selected=true])>button]:after:bg-clay-500",
        outside: "text-fg-3",
        hidden: "invisible",
        ...classNames,
      }}
      modifiersClassNames={{
        reference:
          "[&:not([data-selected=true])>button]:bg-clay-50 [&:not([data-selected=true])>button]:text-clay-700",
      }}
      components={{
        Dropdown: CalendarDropdown,
        Chevron: ({ orientation }) =>
          orientation === "left" ? (
            <ChevronLeft className="size-4.5" aria-hidden="true" />
          ) : (
            <ChevronRight className="size-4.5" aria-hidden="true" />
          ),
        ...components,
      }}
      {...props}
    />
  );
}

export { Calendar };
