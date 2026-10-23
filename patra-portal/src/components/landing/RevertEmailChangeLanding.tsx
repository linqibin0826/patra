"use client";

import { useQueryClient } from "@tanstack/react-query";
import { TokenLandingPage } from "@/components/landing/TokenLandingPage";
import { EMAIL_CHANGE_LANDING_MESSAGES as M } from "@/lib/auth/messages";
import { revertEmailChange } from "@/lib/identity/token-actions";
import { queryKeys } from "@/lib/query-keys";

/// 撤销改邮箱落地页（简报 §3.18 d）：旧邮箱通知里的「不是我」。成功后邮箱改回、全部会话下线、原密码作废，
/// 用户要查收重设密码邮件，所以成功屏没有按钮；冷静期账号也能用（不进冷静期跳转，spec §9.1）。
export function RevertEmailChangeLanding() {
  const queryClient = useQueryClient();

  /// 提交令牌
  async function submit(token: string) {
    const result = await revertEmailChange(token);
    if (result.ok) {
      // 令牌所属用户的会话已全部下线；登录着的若是另一个账号，它的会话还在，所以只失效不写 null
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
      title={M.revertTitle}
      description={M.revertDescription}
      actionLabel={M.revertAction}
      pendingLabel={M.revertPending}
      danger
      submit={submit}
      renderSucceeded={(email) => ({
        tone: "success",
        title: M.revertedTitle,
        description: `登录邮箱已改回 ${email}，所有设备已退出登录，原密码已作废。请查收重设密码的邮件（1 小时内有效），设置新密码后再登录。`,
      })}
      renderConflict={() => ({
        tone: "danger",
        title: M.revertTakenTitle,
        description: M.revertTakenDescription,
      })}
      renderInvalid={() => ({
        tone: "danger",
        title: M.invalidTitle,
        description: M.revertInvalidDescription,
      })}
    >
      <ul className="-mt-1 flex list-disc flex-col gap-1.5 pl-4.5 text-base leading-5.5 text-fg-2">
        {M.revertConsequences.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </TokenLandingPage>
  );
}
