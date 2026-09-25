"use client";

import { cn } from "@/lib/utils";

export interface SortOption<T extends string> {
  id: T;
  label: string;
  /** 降序项在文案后带 ↓ */
  desc?: boolean;
}

interface SortSegmentedProps<T extends string> {
  options: readonly SortOption<T>[];
  value: T;
  onChange: (id: T) => void;
}

/// 排序段控件：「排序」标签 + 连体按钮（选中深填充，降序项带 ↓）。
export function SortSegmented<T extends string>({
  options,
  value,
  onChange,
}: SortSegmentedProps<T>) {
  return (
    <div className="flex shrink-0 items-center gap-2">
      <span className="font-mono text-3xs uppercase tracking-caps text-fg-3">排序</span>
      <fieldset
        aria-label="排序方式"
        className="m-0 inline-flex min-w-0 items-center overflow-hidden rounded-md border border-border-default p-0"
      >
        {options.map((opt) => {
          const active = value === opt.id;
          return (
            <button
              key={opt.id}
              type="button"
              aria-pressed={active}
              onClick={() => onChange(opt.id)}
              className={cn(
                "border-0 border-r border-border-subtle px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors last:border-r-0",
                active
                  ? "bg-ink-900 text-paper-50 hover:bg-ink-800"
                  : "text-fg-3 hover:bg-paper-200 hover:text-ink-900",
              )}
            >
              {opt.label}
              {opt.desc && (
                <span aria-hidden="true" className="ml-1 font-mono text-3xs opacity-70">
                  ↓
                </span>
              )}
            </button>
          );
        })}
      </fieldset>
    </div>
  );
}
