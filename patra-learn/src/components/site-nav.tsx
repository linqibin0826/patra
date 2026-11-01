// patra-learn/src/components/site-nav.tsx
// 顶栏（画板 Nav）：站名、今天 / 路线 / 主题 / 标签 / 批注、搜索入口；任意页按 / 进搜索
"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useAnnotations } from "@/lib/annotations/use-annotations";
import { cx, wrap } from "./ui";

const LINKS = [
  { href: "/", label: "今天", match: (p: string) => p === "/" },
  { href: "/routes", label: "路线", match: (p: string) => p.startsWith("/routes") },
  { href: "/topics", label: "主题", match: (p: string) => p.startsWith("/topics") },
  { href: "/tags", label: "标签", match: (p: string) => p.startsWith("/tags") },
];

/// 两片叶的标志
function Leaf() {
  return (
    <svg
      aria-hidden="true"
      width="26"
      height="26"
      viewBox="0 0 26 26"
      fill="none"
      stroke="#0E8FA3"
      strokeWidth="1.8"
      strokeLinecap="round"
    >
      <path d="M13 23V11" />
      <path d="M13 14c-5 0-8-3-8-8 5 0 8 3 8 8z" />
      <path d="M13 11c0-4 3-7 8-7 0 4-3 7-8 7z" />
    </svg>
  );
}

/// 不在输入框里时按 / 进搜索
function useSlashToSearch() {
  const router = useRouter();
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "/" || event.metaKey || event.ctrlKey || event.altKey) return;
      const target = event.target as HTMLElement | null;
      if (target?.closest("input, textarea, select, [contenteditable='true']")) return;
      event.preventDefault();
      router.push("/search");
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [router]);
}

/// 「我的批注」入口，有批注时带条数（批注只在浏览器里，服务端与水合时是 0）
function NotesLink({ current }: { current: boolean }) {
  const { annotations } = useAnnotations();
  const count = annotations.length;
  return (
    <Link
      href="/notes"
      aria-current={current ? "page" : undefined}
      aria-label={count > 0 ? `批注，${count} 条` : undefined}
      className={cx(
        "inline-flex items-center gap-1.5 rounded-full px-3 py-1.5",
        current
          ? "bg-surface font-medium text-ink shadow-[inset_0_0_0_1px_var(--color-line)]"
          : "text-ink-3 hover:bg-surface hover:text-ink",
      )}
    >
      批注
      {count > 0 ? (
        <span className="box-border inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-teal-tint px-1.5 font-semibold text-[11.5px] text-teal-deep">
          {count}
        </span>
      ) : null}
    </Link>
  );
}

export function SiteNav() {
  const pathname = usePathname();
  useSlashToSearch();
  return (
    <header className={cx(wrap, "flex flex-wrap items-center gap-x-7 gap-y-3 py-5")}>
      <Link href="/" className="flex items-center gap-2.5 text-ink hover:text-ink">
        <Leaf />
        <b className="font-bold font-display text-[19px] tracking-[0.01em]">Patra 学习站</b>
      </Link>
      <nav aria-label="主导航" className="flex gap-1 font-ui text-ui">
        {LINKS.map((link) => {
          const current = link.match(pathname);
          return (
            <Link
              key={link.href}
              href={link.href}
              aria-current={current ? "page" : undefined}
              className={cx(
                "rounded-full px-3 py-1.5",
                current
                  ? "bg-surface font-medium text-ink shadow-[inset_0_0_0_1px_var(--color-line)]"
                  : "text-ink-3 hover:bg-surface hover:text-ink",
              )}
            >
              {link.label}
            </Link>
          );
        })}
        <NotesLink current={pathname.startsWith("/notes")} />
      </nav>
      <span className="flex-1 max-sm:hidden" />
      <Link
        href="/search"
        aria-label="搜索条目"
        className="box-border flex h-10 flex-[0_1_300px] items-center gap-2 rounded-full border border-line bg-surface pr-2 pl-3.5 font-ui text-ink-4 text-sm max-sm:flex-1"
      >
        <svg
          aria-hidden="true"
          width="15"
          height="15"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.5-3.5" />
        </svg>
        <span className="flex-1 truncate">搜索条目、概念、报错</span>
        <span className="box-border inline-flex h-[22px] min-w-[22px] items-center justify-center rounded-md border border-line bg-surface-soft px-1.5 font-mono text-[11.5px] text-ink-4">
          /
        </span>
      </Link>
    </header>
  );
}
