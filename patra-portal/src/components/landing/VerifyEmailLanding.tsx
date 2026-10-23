"use client";

import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { type LandingScreen, TokenLandingPage } from "@/components/landing/TokenLandingPage";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { fetchCurrentUser, useCurrentUser } from "@/lib/auth/current-user-query";
import { VERIFY_LANDING_MESSAGES as M } from "@/lib/auth/messages";
import { authPageHref } from "@/lib/auth/next-path";
import { verifyEmail } from "@/lib/identity/token-actions";
import { btnBlock, btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";

/// 成功后要显示的：验证的邮箱，以及本机会话还在不在
interface Verified {
  email: string;
  sessionKept: boolean;
}

/// 「继续浏览」（回首页）
const continueLink = (
  <Link href="/" className={cn(btnPrimary, btnBlock)}>
    {M.continue}
  </Link>
);

/// 验证邮箱落地页（简报 §3.18 a、spec §8.3）。成功后重新取当前用户，以「本机会话还在、而且就是这个邮箱」
/// 决定文案：带着别的账号的会话点链接时，identity 撤销的是令牌所属用户的全部会话，不保留本机的。
/// 不做「已登录且已验证就直接显示已验证」的捷径（前端判断不了令牌属于谁），改在无效屏里显示已验证。
export function VerifyEmailLanding() {
  const queryClient = useQueryClient();
  const state = useCurrentUser();

  /// 提交令牌；成功后刷新当前用户再判断本机会话
  async function submit(token: string) {
    const result = await verifyEmail(token);
    if (!result.ok) {
      return result.reason === "invalid"
        ? ({ kind: "invalid" } as const)
        : ({ kind: "unavailable" } as const);
    }
    // 重新取一次；取不到就按无会话说，不拿验证之前的旧用户判断
    const user = await queryClient
      .fetchQuery({ queryKey: queryKeys.currentUser, queryFn: fetchCurrentUser, staleTime: 0 })
      .catch(() => null);
    const sessionKept = user?.email.toLowerCase() === result.email.toLowerCase();
    return { kind: "succeeded", data: { email: result.email, sessionKept } } as const;
  }

  /// 成功屏：有 / 无本机会话两种说法
  function succeeded({ email, sessionKept }: Verified): LandingScreen {
    return sessionKept
      ? {
          tone: "success",
          title: M.verifiedTitle,
          description: `${email} 已验证。其他设备上的登录已退出。`,
          actions: continueLink,
        }
      : {
          tone: "success",
          title: M.verifiedTitle,
          description: `${email} 已验证。为了安全，所有设备都已退出登录。`,
          actions: (
            <Link href="/login" className={cn(btnPrimary, btnBlock)}>
              {M.login}
            </Link>
          ),
        };
  }

  /// 无效屏：按当前登录状态给出口
  function invalid(): LandingScreen {
    if (state.status === "authenticated" && state.user.emailVerified) {
      return {
        tone: "success",
        title: M.verifiedTitle,
        description: `你的邮箱 ${state.user.email} 已经验证过了。`,
        actions: continueLink,
      };
    }
    return {
      tone: "danger",
      title: M.invalidTitle,
      description: M.invalidDescription,
      actions:
        state.status === "authenticated" ? (
          <ResendVerificationButton variant="block" label={M.resend} />
        ) : (
          <Link
            href={authPageHref("/login", "/settings/account")}
            className={cn(btnSecondary, btnBlock)}
          >
            {M.loginToResend}
          </Link>
        ),
    };
  }

  return (
    <TokenLandingPage<Verified>
      title={M.title}
      description={M.description}
      actionLabel={M.action}
      pendingLabel={M.pending}
      submit={submit}
      renderSucceeded={succeeded}
      renderInvalid={invalid}
    />
  );
}
