import { useCallback } from "react";
import type { ReauthRequired } from "@/lib/identity/mutation-failure";
import { useReauthStore } from "@/store/reauth";

/// 用户在再认证对话框里取消：调用方什么都不显示，按钮回到可点
export type Cancelled = { ok: false; reason: "cancelled" };

/// 需要再认证的操作的统一包装（spec §4.1、§6.2）：调用 `call`，结果是 `reauth-required` 就弹再认证对话框，
/// 验证成功后用同样的参数（闭包里带着）再调一次；第二次仍要求就再弹一次，由用户决定，不自动循环。
/// 用户取消返回 `cancelled`。
export function useReauth(): {
  withReauth<R>(
    call: () => Promise<R | ReauthRequired>,
  ): Promise<Exclude<R, ReauthRequired> | Cancelled>;
} {
  const request = useReauthStore((state) => state.request);
  const withReauth = useCallback(
    async <R>(call: () => Promise<R | ReauthRequired>) => {
      for (;;) {
        const result = await call();
        if (!isReauthRequired(result)) {
          return result as Exclude<R, ReauthRequired>;
        }
        if (!(await request())) {
          return { ok: false, reason: "cancelled" } as const;
        }
      }
    },
    [request],
  );
  return { withReauth };
}

/// 结果是不是 `reauth-required`
function isReauthRequired(result: unknown): result is ReauthRequired {
  return (
    typeof result === "object" &&
    result !== null &&
    "reason" in result &&
    result.reason === "reauth-required"
  );
}
