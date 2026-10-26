import { useRouter } from "next/navigation";
import { useCallback } from "react";
import { authPageHref } from "@/lib/auth/next-path";

/// 去登录页，登录后回到当前页（路径加查询串）。点收藏、会话失效时用；回来后不自动替他收藏（简报 §2.2）。
/// 点的那一刻读 `location`，不用 `useSearchParams`：收藏按钮在 RSC 页面里，不必为它包 Suspense
export function useLoginRedirect(): () => void {
  const router = useRouter();
  return useCallback(() => {
    router.push(authPageHref("/login", `${window.location.pathname}${window.location.search}`));
  }, [router]);
}
