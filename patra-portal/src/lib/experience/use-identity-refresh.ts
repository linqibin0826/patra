"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";
import { useCurrentUser } from "@/lib/auth/current-user-query";

/// 当前用户在页面上变了（另一个标签页退出、换号、会话失效）时刷新页面。经验页的「我写的」、本人的匿名提示，
/// 主页「投稿经验」的本人视角都由服务端按会话算好、不在查询缓存里，清缓存清不到，只能让服务端重新渲染。
/// 第一次取回当前用户、同一个人资料刷新都不算
export function useRefreshOnIdentityChange(): void {
  const current = useCurrentUser();
  const router = useRouter();
  const identity =
    current.status === "pending"
      ? undefined
      : current.status === "anonymous"
        ? null
        : current.user.userId;
  const known = useRef<string | null | undefined>(undefined);
  useEffect(() => {
    if (identity === undefined) {
      return;
    }
    if (known.current !== undefined && known.current !== identity) {
      router.refresh();
    }
    known.current = identity;
  }, [identity, router]);
}
