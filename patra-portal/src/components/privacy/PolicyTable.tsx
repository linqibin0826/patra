import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/// 隐私政策里的表（简报 v0.18 §3.9）：表头等宽小字、上边一道墨色实线。三列或两列；窄屏三列表每行变成
/// 「第一列一行、其余一行」。语义上是表格，排版用每行一个 grid
export function PolicyTable({
  head,
  rows,
}: {
  head: readonly string[];
  rows: readonly (readonly ReactNode[])[];
}) {
  const three = head.length === 3;
  const row = cn(
    "grid gap-4 border-b border-border-subtle py-3 text-left leading-[1.6] font-normal",
    three
      ? "grid-cols-[1.1fr_1.6fr_1fr] max-md:grid-cols-2 max-md:gap-y-1 max-md:*:first:col-span-2"
      : "grid-cols-[1fr_2fr]",
  );
  return (
    <table className="mt-4.5 block border-t border-ink-900 text-sm text-fg-1">
      <thead className="block">
        <tr className={cn(row, "font-mono text-2xs tracking-caps text-fg-3 uppercase")}>
          {head.map((cell) => (
            <th key={cell} scope="col" className="font-normal">
              {cell}
            </th>
          ))}
        </tr>
      </thead>
      <tbody className="block">
        {rows.map((cells, i) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: 固定内容的表，行没有身份
          <tr key={i} className={row}>
            {cells.map((cell, j) => (
              // biome-ignore lint/suspicious/noArrayIndexKey: 同上
              <td key={j}>{cell}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
