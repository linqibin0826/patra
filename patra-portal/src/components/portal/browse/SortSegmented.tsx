"use client";

import { useScrollFade } from "@/lib/use-scroll-fade";
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

/// 排序段控件：「排序」标签 + 药丸分段按钮（选中墨底纸字，降序项带 ↓）。
/// 窄屏放不下时只有药丸内横向滑动；右侧还有选项时右缘渐隐，滑到尽头即取消。
export function SortSegmented<T extends string>({
  options,
  value,
  onChange,
}: SortSegmentedProps<T>) {
  const { ref, fadeEnd } = useScrollFade<HTMLDivElement>();
  return (
    <div className="flex min-w-0 shrink-0 items-center gap-2.5 max-md:flex-1 max-md:shrink">
      <span className="shrink-0 font-mono text-3xs whitespace-nowrap uppercase tracking-caps text-fg-3">
        排序
      </span>
      <div
        ref={ref}
        data-fade-end={fadeEnd || undefined}
        className="min-w-0 max-md:overflow-x-auto max-md:[scrollbar-width:none] max-md:data-fade-end:[mask-image:linear-gradient(to_right,black_80%,transparent)]"
      >
        <fieldset
          aria-label="排序方式"
          className="m-0 inline-flex min-w-0 items-center gap-0.5 rounded-full border border-border-default bg-paper-50 p-1"
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
                  "rounded-full px-3.5 py-1.5 text-sm font-medium whitespace-nowrap transition-colors duration-200",
                  active
                    ? "bg-ink-900 text-paper-50"
                    : "text-fg-2 hover:bg-paper-200 hover:text-ink-900",
                )}
              >
                {opt.label}
                {opt.desc && (
                  <>
                    <span aria-hidden="true" className="ml-1 font-mono text-3xs opacity-70">
                      ↓
                    </span>
                    <span className="sr-only">，降序</span>
                  </>
                )}
              </button>
            );
          })}
        </fieldset>
      </div>
    </div>
  );
}
