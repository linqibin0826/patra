"use client";

import { Plus } from "lucide-react";
import { type ReactNode, useId, useState } from "react";
import { cn } from "@/lib/utils";

interface DisclosureSectionProps {
  title: string;
  count?: string;
  defaultOpen?: boolean;
  /** 挂标签栏：计数进 128px 标签格、内容与标题对齐（文献档案用；祖先要带 `@container/main`） */
  rail?: boolean;
  children: ReactNode;
}

/// 渐进式披露折叠区（受控 button[aria-expanded]，非原生 details）：发丝线分隔的编辑式手风琴，
/// 右侧圆钮里的 ＋ 展开时旋转成 ×。`rail` 时计数或类别（`2 位`、`ID`）放进 128px 标签格、内容与标题左对齐
/// （标签栏几何见 `RailRow`）。body 始终在 DOM、用 hidden 切换，保证 aria-controls 引用始终有效；
/// 展开 / 收起的淡入下落由全局 .disclosure-body 过渡提供（仅 motion-safe）。
export function DisclosureSection({
  title,
  count,
  defaultOpen = false,
  rail = false,
  children,
}: DisclosureSectionProps) {
  const [open, setOpen] = useState(defaultOpen);
  const bodyId = useId();
  return (
    <section className="border-t border-border-default last:border-b">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={bodyId}
        onClick={() => setOpen((o) => !o)}
        className={cn(
          "group/d w-full py-5 text-left",
          rail
            ? "grid grid-cols-[128px_minmax(0,1fr)_auto] items-center gap-x-8 @max-[580px]/main:grid-cols-[minmax(0,1fr)_auto] @max-[580px]/main:gap-x-4"
            : "flex items-center gap-4",
        )}
      >
        {rail && (
          <span className="font-mono text-2xs tracking-mono text-fg-3 tabular-nums @max-[580px]/main:hidden">
            {count}
          </span>
        )}
        <span className="font-serif text-xl leading-snug font-medium tracking-tight text-ink-900 transition-colors group-hover/d:text-clay-700">
          {title}
        </span>
        {!rail && count != null && (
          <span className="shrink-0 font-mono text-2xs tracking-mono text-fg-3 tabular-nums">
            {count}
          </span>
        )}
        <span
          aria-hidden
          className={cn(
            "flex size-8 shrink-0 items-center justify-center rounded-full border border-border-default text-fg-2 transition-colors duration-200 group-hover/d:border-ink-500 group-hover/d:text-ink-900 group-aria-expanded/d:border-ink-900 group-aria-expanded/d:bg-ink-900 group-aria-expanded/d:text-paper-50",
            !rail && "ml-auto",
          )}
        >
          <Plus
            size={15}
            strokeWidth={1.75}
            className="motion-safe:transition-transform duration-300 ease-out-expo group-aria-expanded/d:rotate-45"
          />
        </span>
      </button>
      <div
        id={bodyId}
        hidden={!open}
        className={cn("disclosure-body pb-7", rail && "pl-[160px] @max-[580px]/main:pl-0")}
      >
        {children}
      </div>
    </section>
  );
}
