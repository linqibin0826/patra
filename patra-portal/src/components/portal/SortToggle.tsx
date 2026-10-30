"use client";

import { cn } from "@/lib/utils";

/// 排序的分段控件（讨论区三项、期刊页经验列表两项）：`fieldset` + 读屏可见的 `legend`，每项是 `aria-pressed` 按钮
export function SortToggle<T extends string>({
  value,
  options,
  onChange,
  legend = "排序",
}: {
  value: T;
  options: readonly { value: T; label: string }[];
  onChange: (value: T) => void;
  legend?: string;
}) {
  return (
    <fieldset className="flex min-w-0 rounded-full border border-border-default bg-paper-100 p-0.5">
      <legend className="sr-only">{legend}</legend>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={value === option.value}
          onClick={() => onChange(option.value)}
          className={cn(
            "h-6.5 rounded-full px-2.5 text-xs whitespace-nowrap transition-colors max-[880px]:h-8",
            value === option.value
              ? "bg-paper-50 font-medium text-fg-1 shadow-sm"
              : "text-fg-2 hover:text-fg-1",
          )}
        >
          {option.label}
        </button>
      ))}
    </fieldset>
  );
}
