"use client";

import { ChevronDownIcon } from "lucide-react";
import { type ReactNode, useState } from "react";
import { cn } from "@/lib/utils";

interface FacetGroupProps {
  title: string;
  /** 本组已选项数（>0 时标题右侧显示角标） */
  selCount: number;
  defaultOpen?: boolean;
  children: ReactNode;
}

/// 可折叠的一组 facet：标题按钮控制展开，内容区是以组名为可访问名的 fieldset（展开时淡入下落）。
export function FacetGroup({ title, selCount, defaultOpen = true, children }: FacetGroupProps) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-b border-border-subtle last:border-0">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className="group/fg flex w-full items-center gap-2 py-3.5 text-left"
      >
        <span className="flex-1 text-sm font-semibold text-ink-900">{title}</span>
        {selCount > 0 && (
          <span className="flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-action-primary px-1 font-mono text-3xs font-semibold text-fg-on-clay tabular-nums">
            {selCount}
          </span>
        )}
        <ChevronDownIcon
          aria-hidden
          className={cn(
            "size-3.5 text-fg-3 transition-transform duration-300 ease-out-expo group-hover/fg:text-ink-900",
            !open && "-rotate-90",
          )}
        />
      </button>
      {open && (
        <fieldset aria-label={title} className="disclosure-body m-0 min-w-0 border-0 p-0 pb-4">
          {children}
        </fieldset>
      )}
    </div>
  );
}
