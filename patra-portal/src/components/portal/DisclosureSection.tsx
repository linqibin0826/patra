"use client";

import { ChevronRight } from "lucide-react";
import { type ReactNode, useId, useState } from "react";

interface DisclosureSectionProps {
  title: string;
  count?: string;
  defaultOpen?: boolean;
  children: ReactNode;
}

/// 渐进式披露折叠区（受控 button[aria-expanded]，非原生 details）。
/// body 始终在 DOM、用 hidden 切换，保证 aria-controls 引用始终有效。相邻区 -1px 合并边框。
export function DisclosureSection({
  title,
  count,
  defaultOpen = false,
  children,
}: DisclosureSectionProps) {
  const [open, setOpen] = useState(defaultOpen);
  const bodyId = useId();
  return (
    <section className="overflow-hidden rounded-lg border border-border-default bg-paper-50 [&+&]:-mt-px">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={bodyId}
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-3 px-5 py-4 text-left font-sans transition-colors hover:bg-paper-100 focus-visible:shadow-(--ring-focus) focus-visible:outline-none"
      >
        <ChevronRight
          size={16}
          data-open={open ? "true" : "false"}
          className="shrink-0 text-fg-3 transition-transform duration-200 data-[open=true]:rotate-90"
        />
        <span className="text-lg font-semibold leading-snug tracking-tight text-ink-900">
          {title}
        </span>
        {count != null && (
          <span className="ml-auto shrink-0 font-mono text-xs tabular-nums text-fg-3">{count}</span>
        )}
      </button>
      <div id={bodyId} hidden={!open} className="border-t border-border-subtle px-5 pb-5 pt-4">
        {children}
      </div>
    </section>
  );
}
