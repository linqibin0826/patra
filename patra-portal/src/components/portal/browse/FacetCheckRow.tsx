"use client";

import { CHECK_INPUT, CHECK_MARK, RADIO_INPUT } from "@/lib/portal-ui";
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

/// facet 勾选行：自绘复选（勾线描出）/ 单选 + 标签 + 右侧千分位计数；计数为 0 且未选中时变淡但仍可点。
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
        "relative -mx-2 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm text-fg-1 transition-colors duration-150 hover:bg-paper-200/70",
        checked && "font-medium",
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
        className={type === "radio" ? RADIO_INPUT : CHECK_INPUT}
      />
      {type === "checkbox" && (
        // 勾线叠在方框正上方（行内边距 px-2 即方框位置），紧跟 input 才能靠 peer-checked 描出
        <svg data-check-mark aria-hidden viewBox="0 0 16 16" className={CHECK_MARK}>
          <path d="M3.6 8.4l2.9 2.9 5.9-6.6" pathLength={1} />
        </svg>
      )}
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {count !== undefined && (
        <span className="font-mono text-2xs tabular-nums text-fg-3">
          {count.toLocaleString("en-US")}
        </span>
      )}
    </label>
  );
}
