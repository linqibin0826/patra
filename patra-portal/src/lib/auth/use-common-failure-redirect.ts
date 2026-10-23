import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useCallback } from "react";
import { authPageHref, deletionPendingHref } from "@/lib/auth/next-path";
import { queryKeys } from "@/lib/query-keys";

/// 写操作都一样处理的两种失败怎么跳（spec §10）：`page` 是当前页的路径（可带查询串）。返回的函数在 `page`
/// 不变时保持同一个引用，可以放进 effect 的依赖。
/// - `pending-deletion`：去取消注销页，取消后回到这一页
/// - `signed-out`：当前用户写成 null（导航栏立即变成未登录），去登录页，登录后回到这一页
export function useCommonFailureRedirect(
  page: string,
): (reason: "signed-out" | "pending-deletion") => void {
  const router = useRouter();
  const queryClient = useQueryClient();
  return useCallback(
    (reason) => {
      if (reason === "pending-deletion") {
        router.replace(deletionPendingHref(page));
        return;
      }
      queryClient.setQueryData(queryKeys.currentUser, null);
      router.replace(authPageHref("/login", page));
    },
    [router, queryClient, page],
  );
}
