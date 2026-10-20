"use client";

import { Plus } from "lucide-react";
import { type ReactNode, useId, useState } from "react";

interface DisclosureSectionProps {
  title: string;
  count?: string;
  defaultOpen?: boolean;
  children: ReactNode;
}

/// 渐进式披露折叠区（受控 button[aria-expanded]，非原生 details）：发丝线分隔的编辑式手风琴，
/// 计数或类别（`2 位`、`ID`）放在 128px 标签格，标题在内容格，右侧圆钮里的 ＋ 展开时旋转成 ×；
/// 内容与标题左对齐（标签栏几何见 `RailRow`，祖先带 `@container/main`）。body 始终在 DOM、用 hidden 切换，保证 aria-controls 引用始终有效；
/// 展开 / 收起的淡入下落由全局 .disclosure-body 过渡提供（仅 motion-safe）。
export function DisclosureSection({
  title,
  count,
  defaultOpen = false,
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
        className="group/d grid w-full grid-cols-[128px_minmax(0,1fr)_auto] items-center gap-x-8 py-5 text-left @max-[580px]/main:grid-cols-[minmax(0,1fr)_auto] @max-[580px]/main:gap-x-4"
      >
        <span className="font-mono text-2xs tracking-mono text-fg-3 tabular-nums @max-[580px]/main:hidden">
          {count}
        </span>
        <span className="font-serif text-xl leading-snug font-medium tracking-tight text-ink-900 transition-colors group-hover/d:text-clay-700">
          {title}
        </span>
        <span
          aria-hidden
          className="flex size-8 shrink-0 items-center justify-center rounded-full border border-border-default text-fg-2 transition-colors duration-200 group-hover/d:border-ink-500 group-hover/d:text-ink-900 group-aria-expanded/d:border-ink-900 group-aria-expanded/d:bg-ink-900 group-aria-expanded/d:text-paper-50"
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
        className="disclosure-body pb-7 pl-[160px] @max-[580px]/main:pl-0"
      >
        {children}
      </div>
    </section>
  );
}
