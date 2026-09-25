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

/// 可折叠的一组 facet：标题按钮控制展开，内容区是以组名为可访问名的 fieldset。
export function FacetGroup({ title, selCount, defaultOpen = true, children }: FacetGroupProps) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-b border-border last:border-0">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-1 px-1 py-2 text-sm font-medium"
      >
        <ChevronDownIcon className={cn("size-3.5 transition-transform", !open && "-rotate-90")} />
        <span className="flex-1 text-left">{title}</span>
        {selCount > 0 && (
          <span className="flex size-4 items-center justify-center rounded-full bg-primary text-[10px] text-primary-foreground">
            {selCount}
          </span>
        )}
      </button>
      {open && (
        <fieldset aria-label={title} className="border-0 p-0 pb-2 pl-1">
          {children}
        </fieldset>
      )}
    </div>
  );
}
