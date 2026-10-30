import type { ReactNode } from "react";

/** 事实条一格的外框（占位格也用它，避免布局跳动）。 */
export const FACT_CELL =
  "border-r border-border-subtle py-3.5 pr-4 pl-4 first:pl-0 last:border-r-0 max-[540px]:border-r-0 max-[540px]:pl-0";

/** 事实条的一格：等宽小标 + 数值。 */
export function FactCell({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className={FACT_CELL}>
      <dt className="font-mono text-3xs tracking-mono text-fg-3">{term}</dt>
      <dd className="m-0 mt-1.5 flex items-center gap-1.5 font-sans text-md font-medium tabular-nums text-fg-1">
        {children}
      </dd>
    </div>
  );
}
