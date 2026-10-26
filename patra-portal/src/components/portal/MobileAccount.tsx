"use client";

import { Library, LogOut, Settings, UserRound } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { AuthLink } from "@/components/auth/AuthLink";
import { Avatar } from "@/components/user/Avatar";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { useLogout } from "@/lib/auth/logout-mutation";
import { useAuthPageHref } from "@/lib/auth/use-auth-page-href";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 「登录」「退出登录」：48px 高的次按钮，占满一行
const ACCOUNT_BUTTON = cn(btnSecondary, "flex h-12 w-full gap-2 py-0");

/// 「主页」「文献库」「设置」：三个并排的等宽次按钮（v0.13 简报 §2.3）
const LINK_BUTTON = cn(btnSecondary, "flex h-12 min-w-0 flex-1 gap-2 px-2 py-0");

/// 账号块的外框：上方一条分隔线。未登录与占位的内容是 80px 高；已登录多一行链接，更高（简报 §3.3）
function Frame({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-3 border-t border-border-default pt-5">
      {children}
    </div>
  );
}

/// 登录状态还没确认时的中性占位，也是 TopNav 里 Suspense 的 fallback
export function MobileAccountPlaceholder() {
  return (
    <Frame>
      <span
        role="status"
        aria-label="正在确认登录状态"
        className="block h-20 rounded-sm bg-bg-sunken"
      />
    </Frame>
  );
}

interface MobileAccountProps {
  /// 关闭全屏菜单：点「登录」「注册」时，以及退出成功后
  onClose: () => void;
}

/// 移动端全屏菜单底部的账号块（v0.8 高保真稿 Nav-M-*；已登录形态按 v0.12 简报 §3.3：头像 + 昵称 /
/// @用户名一行，下面并排「主页」「文献库」「设置」，再下面整行「退出登录」；v0.13 加文献库）。
/// 「退出登录」进行中用 `aria-disabled` 而不是 `disabled`：按钮不丢焦点，`title` 照常显示。
export function MobileAccount({ onClose }: MobileAccountProps) {
  const state = useCurrentUser();
  const logout = useLogout();
  const loginHref = useAuthPageHref("/login");
  const registerHref = useAuthPageHref("/register");

  if (state.status === "pending") {
    return <MobileAccountPlaceholder />;
  }
  if (state.status === "anonymous") {
    return (
      <Frame>
        <Link href={loginHref} onClick={onClose} className={ACCOUNT_BUTTON}>
          登录
        </Link>
        <p className="text-center text-base leading-5 text-fg-2">
          还没有账号？
          <AuthLink href={registerHref} onClick={onClose}>
            注册
          </AuthLink>
        </p>
      </Frame>
    );
  }
  return (
    <Frame>
      <div className="flex min-w-0 items-center gap-3">
        <Avatar identity={state.user} size={40} />
        <div className="flex min-w-0 flex-col">
          <span
            title={state.user.displayName}
            className="truncate text-base leading-5 font-medium text-fg-1"
          >
            {state.user.displayName}
          </span>
          <span className="truncate font-mono text-xs leading-4 text-fg-3">
            @{state.user.username}
          </span>
        </div>
      </div>
      <div className="flex gap-3">
        <Link href={`/u/${state.user.username}`} onClick={onClose} className={LINK_BUTTON}>
          <UserRound className="size-3.75" aria-hidden="true" />
          主页
        </Link>
        <Link href="/library" onClick={onClose} className={LINK_BUTTON}>
          <Library className="size-3.75" aria-hidden="true" />
          文献库
        </Link>
        <Link href="/settings/profile" onClick={onClose} className={LINK_BUTTON}>
          <Settings className="size-3.75" aria-hidden="true" />
          设置
        </Link>
      </div>
      <button
        type="button"
        aria-disabled={logout.isPending || undefined}
        title={logout.isPending ? "正在退出登录" : undefined}
        onClick={() => {
          if (!logout.isPending) {
            logout.mutate(undefined, { onSuccess: () => onClose() });
          }
        }}
        className={ACCOUNT_BUTTON}
      >
        <LogOut className="size-3.75" aria-hidden="true" />
        退出登录
      </button>
    </Frame>
  );
}
