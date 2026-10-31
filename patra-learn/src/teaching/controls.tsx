// patra-learn/src/teaching/controls.tsx
// 开关与分段切换（参数组件、对比切换共用）
"use client";

import type { ReactNode } from "react";
import { cx, Inline } from "./ui";

/// 开关：水青轨道，圆钮用 spring 缓动滑过去
export function Switch({
  label,
  checked,
  disabled,
  onChange,
}: {
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (next: boolean) => void;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label.replaceAll("`", "")}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className="inline-flex min-h-11 cursor-pointer items-center gap-2.5 border-0 bg-transparent p-0 text-left font-ui text-ink text-ui disabled:cursor-not-allowed disabled:opacity-45"
    >
      <span
        className={cx(
          "relative h-[26px] w-11 flex-none rounded-[13px] transition-colors duration-280 ease-out",
          checked ? "bg-teal" : "bg-line",
        )}
      >
        <span
          className={cx(
            "absolute top-[3px] left-[3px] size-5 rounded-full bg-white shadow-[0_1px_3px_rgba(31,42,36,0.25)] transition-transform duration-280 ease-spring",
            checked && "translate-x-[18px]",
          )}
        />
      </span>
      <span aria-hidden="true">
        <Inline text={label} />
      </span>
    </button>
  );
}

/// 分段切换：一组互斥的标签
export function Segmented({
  label,
  items,
  value,
  onChange,
}: {
  label: string;
  items: readonly string[];
  value: number;
  onChange: (index: number) => void;
}) {
  return (
    <div
      role="tablist"
      aria-label={label}
      className="inline-flex gap-1 rounded-full bg-line-soft p-1"
    >
      {items.map((item, i) => (
        <button
          key={item}
          type="button"
          role="tab"
          aria-selected={value === i}
          onClick={() => onChange(i)}
          className={cx(
            "h-9 cursor-pointer rounded-full border-0 px-4 font-medium font-ui text-sm transition-[background-color,color,box-shadow] duration-280 ease-out",
            value === i
              ? "bg-surface text-ink shadow-[0_1px_3px_rgba(31,42,36,0.14)]"
              : "bg-transparent text-ink-3 hover:text-ink",
          )}
        >
          {item}
        </button>
      ))}
    </div>
  );
}

/// 小标题行
export function FieldLabel({ children }: { children: ReactNode }) {
  return <span className="font-ui text-ink-4 text-meta">{children}</span>;
}
