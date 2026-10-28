"use client";

import { Bell, Library, LogOut, Settings, UserRound } from "lucide-react";
import Link from "next/link";
import { formatUnread } from "@/components/notifications/UnreadBadge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuLinkItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar } from "@/components/user/Avatar";
import type { CurrentUser } from "@/lib/auth/types";
import { useUnreadCounts } from "@/lib/notifications/queries";

interface UserMenuProps {
  user: CurrentUser;
  /// 退出登录进行中：「退出登录」禁用
  loggingOut: boolean;
  onLogout: () => void;
}

/// 已登录时导航栏右上角的头像和用户菜单（简报 §3.3、§4.3 nav.*）。顶栏只放 36px 头像；菜单头部是 40px
/// 头像 + 昵称 / @用户名（都截断，悬停看全），邮箱不再出现。菜单项：个人主页、我的文献库、提醒（右端未读数，0 不写，
/// 取不到写「暂不可用」，v0.14 简报 §3.18）、设置、退出登录。
/// 开合、方向键、Esc 和点外面关闭、关闭后焦点回到头像，都用 base-ui 自带的。「退出登录」点了不关菜单：
/// 退出完成前菜单一直开着，完成后整个菜单随登录状态换成「登录」。
export function UserMenu({ user, loggingOut, onLogout }: UserMenuProps) {
  const handle = `@${user.username}`;
  const { counts, unavailable } = useUnreadCounts();
  const unread = unavailable ? "暂不可用" : counts?.total ? formatUnread(counts.total) : null;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="账号菜单"
        className="inline-flex shrink-0 rounded-full ring-paper-300 transition-shadow hover:ring-3 data-popup-open:ring-3"
      >
        <Avatar identity={user} size={36} />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuGroup>
          <DropdownMenuLabel className="flex items-center gap-3">
            <Avatar identity={user} size={40} />
            <div className="flex min-w-0 flex-col">
              <div
                title={user.displayName}
                className="truncate text-sm leading-5 font-medium text-fg-1"
              >
                {user.displayName}
              </div>
              <div title={handle} className="truncate font-mono text-2xs leading-4 text-fg-3">
                {handle}
              </div>
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuLinkItem render={<Link href={`/u/${user.username}`} />}>
            <UserRound className="size-3.75 text-fg-3" aria-hidden="true" />
            个人主页
          </DropdownMenuLinkItem>
          <DropdownMenuLinkItem render={<Link href="/library" />}>
            <Library className="size-3.75 text-fg-3" aria-hidden="true" />
            我的文献库
          </DropdownMenuLinkItem>
          <DropdownMenuLinkItem render={<Link href="/notifications" />}>
            <Bell className="size-3.75 text-fg-3" aria-hidden="true" />
            提醒
            {unread && <span className="ml-auto font-mono text-2xs text-fg-3">{unread}</span>}
          </DropdownMenuLinkItem>
          <DropdownMenuLinkItem render={<Link href="/settings/profile" />}>
            <Settings className="size-3.75 text-fg-3" aria-hidden="true" />
            设置
          </DropdownMenuLinkItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            closeOnClick={false}
            disabled={loggingOut}
            title={loggingOut ? "正在退出登录" : undefined}
            onClick={onLogout}
          >
            <LogOut className="size-3.75 text-fg-3" aria-hidden="true" />
            退出登录
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
