"use client";

interface FacetToggleRowProps {
  label: string;
  checked: boolean;
  onToggle: () => void;
  count?: number;
}

/// facet 布尔开关行（如"仅开放获取"），可带千分位计数。
export function FacetToggleRow({ label, checked, onToggle, count }: FacetToggleRowProps) {
  return (
    <label className="flex cursor-pointer items-center gap-2 rounded px-1 py-1 text-sm hover:bg-muted/60">
      <input
        type="checkbox"
        aria-label={label}
        checked={checked}
        onChange={onToggle}
        className="size-3.5 accent-primary"
      />
      <span className="flex-1">{label}</span>
      {count !== undefined && (
        <span className="text-xs tabular-nums text-muted-foreground">
          {count.toLocaleString("en-US")}
        </span>
      )}
    </label>
  );
}
