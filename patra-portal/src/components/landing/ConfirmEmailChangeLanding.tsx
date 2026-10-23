"use client";

import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { TokenLandingPage } from "@/components/landing/TokenLandingPage";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { EMAIL_CHANGE_LANDING_MESSAGES as M } from "@/lib/auth/messages";
import { confirmEmailChange } from "@/lib/identity/token-actions";
import { btnBlock, btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";

/// 「去账号设置」
const toAccount = (
  <Link href="/settings/account" className={cn(btnSecondary, btnBlock)}>
    {M.toAccount}
  </Link>
);

/// 确认新邮箱落地页（简报 §3.18 c）：成功立即生效（本机会话不受影响，刷新当前用户换成新邮箱）；地址在确认前
/// 被别人注册了是冲突屏；链接无效时按登录状态给出口。
export function ConfirmEmailChangeLanding() {
  const queryClient = useQueryClient();
  const state = useCurrentUser();

  /// 提交令牌
  async function submit(token: string) {
    const result = await confirmEmailChange(token);
    if (result.ok) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.currentUser });
      return { kind: "succeeded", data: result.email } as const;
    }
    if (result.reason === "taken") return { kind: "conflict", data: null } as const;
    return result.reason === "invalid"
      ? ({ kind: "invalid" } as const)
      : ({ kind: "unavailable" } as const);
  }

  return (
    <TokenLandingPage<string, null>
      title={M.confirmTitle}
      description={M.confirmDescription}
      actionLabel={M.confirmAction}
      pendingLabel={M.confirmPending}
      submit={submit}
      renderSucceeded={(email) => ({
        tone: "success",
        title: M.confirmedTitle,
        description: `以后请用 ${email} 登录。原来的邮箱会收到一封通知。`,
        actions: (
          <Link href="/" className={cn(btnPrimary, btnBlock)}>
            {M.continue}
          </Link>
        ),
      })}
      renderConflict={() => ({
        tone: "danger",
        title: M.confirmTakenTitle,
        description: M.confirmTakenDescription,
        actions: toAccount,
      })}
      renderInvalid={() => ({
        tone: "danger",
        title: M.invalidTitle,
        description: M.confirmInvalidDescription,
        actions:
          state.status === "authenticated" ? (
            toAccount
          ) : (
            <Link href="/login" className={cn(btnSecondary, btnBlock)}>
              {M.login}
            </Link>
          ),
      })}
    />
  );
}
