import type { InputHTMLAttributes } from "react";
import { CHECK_INPUT, CHECK_MARK } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 自绘复选框：纸面方框 + 勾选时描出的勾线（`CHECK_INPUT` / `CHECK_MARK`）。只是外观，属性原样交给 input
export function CheckBox({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <span className="relative inline-flex shrink-0">
      <input type="checkbox" className={cn(CHECK_INPUT, className)} {...props} />
      <svg aria-hidden="true" viewBox="0 0 16 16" className={cn(CHECK_MARK, "left-0")}>
        <path d="M3.6 8.4l2.9 2.9 5.9-6.6" pathLength={1} />
      </svg>
    </span>
  );
}
