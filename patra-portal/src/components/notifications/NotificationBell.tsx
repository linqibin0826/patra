"use client";

import { Bell } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { formatUnread, UnreadBadge } from "@/components/notifications/UnreadBadge";
import { useNotificationsEnabled, useUnreadCounts } from "@/lib/notifications/queries";
import { cn } from "@/lib/utils";

/// 在不在提醒列表页（三个标签）
export function isNotificationsPath(pathname: string): boolean {
  return pathname === "/notifications" || pathname.startsWith("/notifications/");
}

/// 提醒入口的读屏名称（简报 §3.18）：数字变化不主动播报，名称里带着。铃铛与移动菜单的「提醒」按钮共用
export function bellLabel(total: number | undefined, unavailable: boolean): string {
  if (unavailable) {
    return "提醒（暂不可用）";
  }
  return total ? `提醒，${formatUnread(total)} 条未读` : "提醒";
}

/// 导航栏的提醒入口（简报 §3.18）：36px 圆形图标按钮，有未读时右上角挂徽标；在 `/notifications/**` 时是当前项；
/// 未读数取不到时图标变浅、不挂徽标，仍可点（进列表页看错误与重试）。未登录不渲染。桌面与窄屏顶栏各放一个，
/// 都只读缓存，轮询在 TopNav 里
export function NotificationBell({ className }: { className?: string }) {
  const enabled = useNotificationsEnabled();
  const { counts, unavailable } = useUnreadCounts();
  const pathname = usePathname();
  if (!enabled) {
    return null;
  }
  const current = isNotificationsPath(pathname);
  return (
    <Link
      href="/notifications"
      aria-label={bellLabel(counts?.total, unavailable)}
      aria-current={current ? "page" : undefined}
      title={unavailable ? "提醒暂不可用" : undefined}
      className={cn(
        "relative inline-flex size-9 shrink-0 items-center justify-center rounded-full text-fg-2 transition-colors hover:bg-paper-200 hover:text-fg-1 aria-[current=page]:bg-paper-200 aria-[current=page]:text-fg-1",
        unavailable && "text-fg-3",
        className,
      )}
    >
      <Bell className="size-4.5" strokeWidth={1.75} aria-hidden="true" />
      {!unavailable && counts && (
        <UnreadBadge count={counts.total} className="absolute -top-0.5 -right-0.5" />
      )}
    </Link>
  );
}
