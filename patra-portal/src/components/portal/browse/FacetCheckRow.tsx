"use client";

import { cn } from "@/lib/utils";

interface FacetCheckRowProps {
  label: string;
  checked: boolean;
  onToggle: () => void;
  /** 命中数；不传则不显示（如已选期刊行） */
  count?: number;
  /** checkbox 多选（默认）/ radio 单选（再点已选项可取消） */
  type?: "checkbox" | "radio";
  /** radio 分组名 */
  name?: string;
  /** 弱化文字（占比大但信息量低的项，如"未分级"） */
  muted?: boolean;
}

const noop = () => undefined;

/// facet 勾选行：右侧千分位计数；计数为 0 且未选中时变淡但仍可点。
export function FacetCheckRow({
  label,
  checked,
  onToggle,
  count,
  type = "checkbox",
  name,
  muted = false,
}: FacetCheckRowProps) {
  const isZero = count === 0 && !checked;
  return (
    <label
      className={cn(
        "flex cursor-pointer items-center gap-2 rounded px-1 py-1 text-sm hover:bg-muted/60",
        isZero && "is-zero opacity-50",
        muted && "text-fg-3",
      )}
    >
      <input
        type={type}
        name={name}
        // 可访问名只取标签本身：label 内标签与计数是相邻 span，浏览器会拼成"T793"这类无分隔名称
        aria-label={label}
        checked={checked}
        // radio 在点击已选中项时不触发 onChange，改用 onClick 才能"再点取消"
        onChange={type === "checkbox" ? onToggle : noop}
        onClick={type === "radio" ? onToggle : undefined}
        className="size-3.5 accent-primary"
      />
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {count !== undefined && (
        <span className="text-xs tabular-nums text-muted-foreground">
          {count.toLocaleString("en-US")}
        </span>
      )}
    </label>
  );
}
