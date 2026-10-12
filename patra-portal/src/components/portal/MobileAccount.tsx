"use client";

import { LogOut } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { AuthLink } from "@/components/auth/AuthLink";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { useLogout } from "@/lib/auth/logout-mutation";
import { useAuthPageHref } from "@/lib/auth/use-auth-page-href";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 「登录」「退出登录」：48px 高的次按钮，占满一行
const ACCOUNT_BUTTON = cn(btnSecondary, "flex h-12 w-full gap-2 py-0");

/// 账号块的外框：上方一条分隔线。三种形态的内容都是 80px 高，切换时菜单不跳
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

/// 移动端全屏菜单底部的账号块（高保真稿 Nav-M-MenuLoggedOut、Nav-M-MenuLoggedIn、
/// Nav-M-MenuLongEmail、Nav-M-MenuLoggingOut、Nav-M-MenuPending）。
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
      <p className="flex min-w-0 items-baseline gap-2.5 text-base leading-5">
        <span className="shrink-0 font-mono text-3xs leading-4 tracking-caps text-fg-3">
          当前账号
        </span>
        <span title={state.user.email} className="min-w-0 truncate font-medium text-fg-1">
          {state.user.email}
        </span>
      </p>
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
