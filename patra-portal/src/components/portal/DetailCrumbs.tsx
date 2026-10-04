import Link from "next/link";
import { Fragment } from "react";
import { cn } from "@/lib/utils";

interface DetailCrumbsProps {
  /** 当前页之前的层级（首项在窄屏也保留，其余窄屏隐藏） */
  trail: { label: string; href: string }[];
  /** 当前页 */
  current: string;
}

/// 详情页吸顶面包屑：高 44px + 1px 底边（侧栏吸顶偏移 134px 依赖这一高度），底边叠一条 2px 陶土阅读进度线
/// （scroll-driven，仅支持的浏览器且 motion-safe 时出现）。
export function DetailCrumbs({ trail, current }: DetailCrumbsProps) {
  return (
    <nav
      aria-label="面包屑"
      className="sticky top-14 z-30 border-b border-border-default bg-bg-sticky backdrop-blur-md"
    >
      <ol className="mx-auto flex h-11 max-w-page items-center gap-2 px-gutter font-mono text-2xs tracking-mono text-fg-3">
        {trail.map((item, i) => (
          <Fragment key={item.href}>
            <li className={cn(i > 0 && "max-[720px]:hidden")}>
              <Link href={item.href} className="link-draw pb-px hover:text-ink-900">
                {item.label}
              </Link>
            </li>
            <li aria-hidden className={cn("text-ink-300", i > 0 && "max-[720px]:hidden")}>
              /
            </li>
          </Fragment>
        ))}
        <li aria-current="page" className="truncate font-medium text-fg-1">
          {current}
        </li>
      </ol>
      <div
        aria-hidden
        className="read-progress pointer-events-none absolute inset-x-0 -bottom-px h-0.5 bg-clay-500"
      />
    </nav>
  );
}
