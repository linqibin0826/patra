import { ArrowUpRight } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { SettingsNav } from "@/components/settings/SettingsNav";

interface SettingsShellProps {
  /// 当前用户名；未登录时为 null（页面会跳去登录页）
  username: string | null;
  children: ReactNode;
}

/// 设置页外壳（简报 §3.5）：h1「设置」+ @用户名，右端「查看我的主页 ↗」；桌面两栏（左 200px 分区导航、
/// 右内容列 ≤ 640px），≤ 880px 改为分段标签在上、内容整宽。
export function SettingsShell({ username, children }: SettingsShellProps) {
  return (
    <div className="mx-auto max-w-page px-gutter pt-10 pb-24 max-[880px]:pt-6">
      <div className="flex items-end justify-between gap-6">
        <div className="min-w-0">
          <h1 className="font-serif text-display-3 leading-heading font-medium tracking-tight text-fg-1">
            设置
          </h1>
          {username && <p className="mt-1.5 truncate font-mono text-sm text-fg-3">@{username}</p>}
        </div>
        {username && (
          <Link
            href={`/u/${username}`}
            className="inline-flex shrink-0 items-center gap-1 text-sm leading-5 font-medium text-link transition-colors hover:text-link-hover"
          >
            查看我的主页
            <ArrowUpRight className="size-3.5" strokeWidth={1.75} aria-hidden="true" />
          </Link>
        )}
      </div>
      <div className="mt-10 grid grid-cols-[200px_minmax(0,640px)] items-start gap-16 max-[880px]:mt-5 max-[880px]:grid-cols-1 max-[880px]:gap-6">
        <SettingsNav />
        <div className="min-w-0">{children}</div>
      </div>
    </div>
  );
}
