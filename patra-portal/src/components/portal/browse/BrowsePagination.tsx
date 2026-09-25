"use client";

import Link from "next/link";
import { useBrowseQuery } from "@/components/portal/browse/BrowseQueryProvider";
import { cn } from "@/lib/utils";

/**
 * 计算分页窗口，返回页码数组（省略号用字符串 "…" 表示）。
 * - total ≤ 7：全列
 * - 否则：始终展示首页、末页、当前页及其前后各一页，间隙用 "…" 填充
 */
export function pageWindow(cur: number, total: number): (number | "…")[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }

  const pages = new Set<number>(
    [1, total, cur - 1, cur, cur + 1].filter((p) => p >= 1 && p <= total),
  );
  const sorted = Array.from(pages).sort((a, b) => a - b);

  const result: (number | "…")[] = [];
  for (let i = 0; i < sorted.length; i++) {
    const page = sorted[i];
    const prev = sorted[i - 1];
    if (page === undefined) continue;
    if (i > 0 && prev !== undefined && page - prev > 1) {
      result.push("…");
    }
    result.push(page);
  }
  return result;
}

const fmt = (n: number) => n.toLocaleString("en-US");

const CELL = "flex h-8 w-8 items-center justify-center rounded border text-sm transition";

interface BrowsePaginationProps {
  page: number;
  total: number;
  pageSize: number;
  /** 计量单位：本 / 篇 */
  unit: string;
}

/**
 * 浏览页分页（client）：页码链接由 Provider 的 hrefFor 生成。
 * 导航进行中整体禁用——旧页码属于旧结果集，此时点击会撤销刚做的筛选。pageCount ≤ 1 时不渲染。
 */
export function BrowsePagination({ page: rawPage, total, pageSize, unit }: BrowsePaginationProps) {
  const { query, isPending, hrefFor } = useBrowseQuery();
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  if (pageCount <= 1) return null;

  const page = Math.max(1, Math.min(rawPage, pageCount));
  const start = (page - 1) * pageSize;
  const end = Math.min(page * pageSize, total);
  const hrefOf = (p: number) => hrefFor({ ...query, page: p });
  const edgeClass = (disabled: boolean) =>
    cn(
      CELL,
      "border-(--border-default)",
      disabled ? "pointer-events-none opacity-40" : "hover:border-ink-300 hover:bg-paper-100",
    );

  return (
    <div className="flex flex-col items-center gap-3">
      <p className="text-sm text-(--fg-3)">
        第 {fmt(start + 1)}–{fmt(end)} {unit} · 共 {fmt(total)} {unit}
      </p>
      <nav
        aria-label="分页"
        aria-disabled={isPending || undefined}
        className={cn("flex items-center gap-1", isPending && "pointer-events-none opacity-50")}
      >
        <Link
          href={hrefOf(page - 1)}
          aria-label="上一页"
          aria-disabled={page <= 1 ? "true" : undefined}
          tabIndex={page <= 1 || isPending ? -1 : undefined}
          className={edgeClass(page <= 1)}
        >
          ‹
        </Link>

        {pageWindow(page, pageCount).map((item, position) =>
          item === "…" ? (
            <span
              // biome-ignore lint/suspicious/noArrayIndexKey: 省略号无业务 id，位置索引是唯一标识；加 gap- 前缀避免与数字页码 key 相撞
              key={`gap-${position}`}
              className="flex h-8 w-8 items-center justify-center text-sm text-(--fg-3)"
            >
              …
            </span>
          ) : (
            <Link
              key={item}
              href={hrefOf(item)}
              aria-label={String(item)}
              aria-current={item === page ? "page" : undefined}
              tabIndex={isPending ? -1 : undefined}
              className={cn(
                CELL,
                item === page
                  ? "border-ink-900 bg-ink-900 font-semibold text-paper-50"
                  : "border-(--border-default) hover:border-ink-300 hover:bg-paper-100",
              )}
            >
              {item}
            </Link>
          ),
        )}

        <Link
          href={hrefOf(page + 1)}
          aria-label="下一页"
          aria-disabled={page >= pageCount ? "true" : undefined}
          tabIndex={page >= pageCount || isPending ? -1 : undefined}
          className={edgeClass(page >= pageCount)}
        >
          ›
        </Link>
      </nav>
    </div>
  );
}
