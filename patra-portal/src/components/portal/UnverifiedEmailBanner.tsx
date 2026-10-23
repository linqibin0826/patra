"use client";

import { MailWarning } from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { useCurrentUser } from "@/lib/auth/current-user-query";

/// 未验证邮箱横幅（简报 §3.4）：已登录、邮箱未验证、账号正常时出现在导航栏正下方，随页面滚走，不能关。
/// 桌面一行（文案 + 重新发送 · 更改邮箱），≤ 880px 两行（「更改邮箱」收进设置页）。
export function UnverifiedEmailBanner() {
  const state = useCurrentUser();
  if (
    state.status !== "authenticated" ||
    state.user.emailVerified ||
    state.user.status !== "ACTIVE"
  ) {
    return null;
  }
  const { email } = state.user;
  return (
    <div className="border-y border-amber-500/30 bg-status-warning-bg">
      <div className="mx-auto flex max-w-page items-start gap-3 px-gutter py-2.5 text-sm leading-5 text-fg-1">
        <MailWarning className="mt-0.5 size-4 shrink-0 text-amber-700" aria-hidden="true" />
        {/* 桌面：一行文案 + 右侧操作；窄屏：第一行短文案，第二行左邮箱、右「重新发送」 */}
        <div className="grid min-w-0 flex-1 grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1">
          <p className="max-[880px]:hidden">
            验证邮箱后，别人才能看到你的主页。验证邮件已发到 {email}。
          </p>
          <p className="col-span-2 hidden max-[880px]:block">请验证邮箱：别人现在看不到你的主页</p>
          <span className="hidden truncate text-fg-2 max-[880px]:block">{email}</span>
          <div className="flex items-center gap-2">
            <Suspense>
              <ResendVerificationButton />
            </Suspense>
            <span aria-hidden="true" className="text-fg-3 max-[880px]:hidden">
              ·
            </span>
            <Link
              href="/settings/account#email"
              className="text-sm leading-5 font-medium text-clay-700 transition-colors hover:text-clay-800 max-[880px]:hidden"
            >
              更改邮箱
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
