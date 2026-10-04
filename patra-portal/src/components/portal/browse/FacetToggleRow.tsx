"use client";

import { SWITCH_INPUT } from "@/lib/portal-ui";

interface FacetToggleRowProps {
  label: string;
  checked: boolean;
  onToggle: () => void;
  count?: number;
}

/// facet 布尔开关行（如"仅开放获取"）：外观为拨动开关，语义仍是 checkbox；可带千分位计数。
export function FacetToggleRow({ label, checked, onToggle, count }: FacetToggleRowProps) {
  return (
    <label className="-mx-2 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm text-fg-1 transition-colors duration-150 hover:bg-paper-200/70">
      <span className="flex-1">{label}</span>
      {count !== undefined && (
        <span className="font-mono text-2xs tabular-nums text-fg-3">
          {count.toLocaleString("en-US")}
        </span>
      )}
      <input
        type="checkbox"
        aria-label={label}
        checked={checked}
        onChange={onToggle}
        className={SWITCH_INPUT}
      />
    </label>
  );
}
