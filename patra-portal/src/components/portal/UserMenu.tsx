"use client";

import { LogOut } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

/// 圆形标识里的字：邮箱的第一个字符（按码点取），字母转大写，数字和符号原样
function emailInitial(email: string): string {
  return Array.from(email)[0]?.toUpperCase() ?? "";
}

interface UserMenuProps {
  email: string;
  /// 退出登录进行中：「退出登录」禁用
  loggingOut: boolean;
  onLogout: () => void;
}

/// 已登录时导航栏右上角的圆形标识和用户菜单（高保真稿 Nav-LoggedIn、Nav-MenuOpen、
/// Nav-MenuLongEmail、Nav-LoggingOut）。开合、方向键、Esc 和点外面关闭、关闭后焦点回到圆形，
/// 都用 base-ui 自带的。「退出登录」点了不关菜单：退出完成前菜单一直开着，完成后整个菜单
/// 随登录状态换成「登录」。
export function UserMenu({ email, loggingOut, onLogout }: UserMenuProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="账号菜单"
        className="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-ink-900 font-serif text-md leading-none font-medium text-paper-50 tabular-nums transition-colors hover:bg-ink-800 data-popup-open:bg-ink-800"
      >
        {emailInitial(email)}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuGroup>
          <DropdownMenuLabel>
            <div className="font-mono text-3xs leading-4 tracking-caps text-fg-3">当前账号</div>
            <div title={email} className="mt-0.5 truncate text-sm leading-5 font-medium text-fg-1">
              {email}
            </div>
          </DropdownMenuLabel>
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
