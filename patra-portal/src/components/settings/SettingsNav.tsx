"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

/// 五个分区（release spec 2.1.11）：已连接账号还没有内容，不渲染入口。账号、安全两页由 PAP-102 建，通知由
/// v0.16 的 PAP-148 建
const SECTIONS = [
  { href: "/settings/profile", label: "个人资料", short: "资料" },
  { href: "/settings/account", label: "账号", short: "账号" },
  { href: "/settings/security", label: "安全", short: "安全" },
  { href: "/settings/privacy", label: "隐私", short: "隐私" },
  { href: "/settings/notifications", label: "通知", short: "通知" },
] as const;

/// 设置页的分区导航（简报 §3.5）：桌面是左侧吸顶的竖排列表，当前项左侧 2px 陶土竖线；≤ 880px 是吸顶在
/// 导航栏下的五个等宽分段标签，当前项下划线。两套都渲染，按宽度显示其一。
export function SettingsNav() {
  const pathname = usePathname();
  const current = (href: string) => (pathname === href ? "page" : undefined);
  return (
    <div className="sticky top-20 z-30 max-[880px]:top-[57px] max-[880px]:-mx-gutter">
      <nav aria-label="设置分区" className="flex flex-col gap-0.5 max-[880px]:hidden">
        {SECTIONS.map((section) => (
          <Link
            key={section.href}
            href={section.href}
            aria-current={current(section.href)}
            className="block border-l-2 border-transparent px-3.5 py-2 text-base leading-5 text-fg-2 transition-colors hover:bg-paper-200 hover:text-ink-900 aria-[current=page]:border-clay-500 aria-[current=page]:font-medium aria-[current=page]:text-fg-1"
          >
            {section.label}
          </Link>
        ))}
      </nav>
      <nav
        aria-label="设置分区（窄屏）"
        className="hidden grid-cols-5 border-b border-border-default bg-bg-sticky px-gutter backdrop-blur-md max-[880px]:grid"
      >
        {SECTIONS.map((section) => (
          <Link
            key={section.href}
            href={section.href}
            aria-current={current(section.href)}
            className="-mb-px flex h-11 items-center justify-center border-b-2 border-transparent text-base text-fg-2 aria-[current=page]:border-clay-500 aria-[current=page]:font-medium aria-[current=page]:text-fg-1"
          >
            {section.short}
          </Link>
        ))}
      </nav>
    </div>
  );
}
