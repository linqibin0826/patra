"use client";

import Link from "next/link";
import { useEffect, useRef } from "react";
import { UserMenu } from "@/components/portal/UserMenu";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { useLogout } from "@/lib/auth/logout-mutation";
import { useAuthPageHref } from "@/lib/auth/use-auth-page-href";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 登录状态还没确认时的圆形占位，也是 TopNav 里 Suspense 的 fallback。服务端渲染出的就是它，
/// 所以不会先闪一下「登录」再变成圆形标识。
export function NavAccountPlaceholder() {
  return (
    <span
      role="status"
      aria-label="正在确认登录状态"
      className="ml-1.5 size-9 shrink-0 rounded-full bg-bg-sunken max-[880px]:hidden"
    />
  );
}

/// 桌面导航栏的账号区，在「关于」右边：登录状态未定时是占位，未登录是「登录」，已登录是
/// 圆形标识和用户菜单。移动端顶栏不显示登录状态（高保真稿 Nav-M-TopBar），账号块在全屏菜单里。
export function NavAccount() {
  const state = useCurrentUser();
  const logout = useLogout();
  const loginHref = useAuthPageHref("/login");
  const loginRef = useRef<HTMLAnchorElement>(null);
  const loggedOut = logout.isSuccess && state.status === "anonymous";

  // 退出成功后圆形变回「登录」，原来聚焦的菜单项已经不在了：焦点移到「登录」上
  useEffect(() => {
    if (loggedOut) {
      loginRef.current?.focus();
    }
  }, [loggedOut]);

  if (state.status === "pending") {
    return <NavAccountPlaceholder />;
  }
  if (state.status === "anonymous") {
    return (
      <Link
        ref={loginRef}
        href={loginHref}
        className={cn(
          btnSecondary,
          "h-9 px-4 py-0 text-sm leading-5 font-medium max-[880px]:hidden",
        )}
      >
        登录
      </Link>
    );
  }
  return (
    <div className="ml-1.5 flex max-[880px]:hidden">
      <UserMenu user={state.user} loggingOut={logout.isPending} onLogout={() => logout.mutate()} />
    </div>
  );
}
