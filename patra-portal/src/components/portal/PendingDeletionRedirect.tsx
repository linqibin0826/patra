"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect } from "react";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { deletionPendingHref } from "@/lib/auth/next-path";

/// 冷静期跳转的客户端兜底（spec §9.2）：当前用户是冷静期账号时去取消注销页，取消后回到这一页。
/// 整页打开由 proxy 拦；这里接住 proxy 不查的站内跳转、从取消注销页后退回路由缓存里的页面。冷静期账号
/// 登录时缓存里已经是带 `PENDING_DELETION` 的用户，读缓存就够。不渲染任何东西。
export function PendingDeletionRedirect(): null {
  const router = useRouter();
  const pathname = usePathname();
  const query = useSearchParams().toString();
  const state = useCurrentUser();
  const pending = state.status === "authenticated" && state.user.status === "PENDING_DELETION";

  useEffect(() => {
    if (pending) {
      router.replace(deletionPendingHref(query ? `${pathname}?${query}` : pathname));
    }
  }, [pending, pathname, query, router]);

  return null;
}
