"use client";

import { type CSSProperties, useLayoutEffect, useRef, useState } from "react";
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
/// 选中的墨底是一块滑动指示条：量出当前项的位置与宽度，切换时滑过去（减弱动效时直接跳到位）。
/// 量到之前（服务端渲染、水合前）由当前项自己铺墨底，不会闪。
/// 窄屏放不下时只有药丸内横向滑动；右侧还有选项时右缘渐隐，滑到尽头即取消。
export function SortSegmented<T extends string>({
  options,
  value,
  onChange,
}: SortSegmentedProps<T>) {
  const { ref, fadeEnd } = useScrollFade<HTMLDivElement>();
  const groupRef = useRef<HTMLFieldSetElement>(null);
  const [slot, setSlot] = useState<{ left: number; width: number } | null>(null);

  // value 变化时重新量；字体加载、窗口缩放会改变按钮宽度，用 ResizeObserver 跟上
  // biome-ignore lint/correctness/useExhaustiveDependencies: value 变了当前项才会换，需要重新量
  useLayoutEffect(() => {
    const group = groupRef.current;
    if (!group) return;
    const measure = () => {
      const active = group.querySelector<HTMLElement>('[aria-pressed="true"]');
      setSlot(active ? { left: active.offsetLeft, width: active.offsetWidth } : null);
    };
    measure();
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(measure);
    observer?.observe(group);
    return () => observer?.disconnect();
  }, [value]);

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
          ref={groupRef}
          aria-label="排序方式"
          className="relative m-0 inline-flex min-w-0 items-center gap-0.5 rounded-full border border-border-default bg-paper-50 p-1"
        >
          {slot && (
            <span
              data-sort-indicator
              aria-hidden
              // 指示条位置是量出来的，只能经 CSS 变量传入
              style={
                {
                  "--sort-left": `${slot.left}px`,
                  "--sort-width": `${slot.width}px`,
                } as CSSProperties
              }
              className="pointer-events-none absolute inset-y-1 left-(--sort-left) w-(--sort-width) rounded-full bg-ink-900 motion-safe:transition-[left,width] duration-500 ease-out-expo"
            />
          )}
          {options.map((opt) => {
            const active = value === opt.id;
            return (
              <button
                key={opt.id}
                type="button"
                aria-pressed={active}
                onClick={() => onChange(opt.id)}
                className={cn(
                  "relative rounded-full px-3.5 py-1.5 text-sm font-medium whitespace-nowrap transition-colors duration-200",
                  active
                    ? cn("text-paper-50", !slot && "bg-ink-900")
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
