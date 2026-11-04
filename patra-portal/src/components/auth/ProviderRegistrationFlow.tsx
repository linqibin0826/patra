"use client";

import { CircleAlert, Info } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { AuthShell } from "@/components/auth/AuthShell";
import { RegistrationCodeForm } from "@/components/auth/RegistrationCodeForm";
import {
  RegistrationFillForm,
  type RegistrationFillIssue,
} from "@/components/auth/RegistrationFillForm";
import { StateIcon } from "@/components/auth/StateIcon";
import { REGISTRATION_MESSAGES } from "@/lib/auth/messages";
import { authPageHref } from "@/lib/auth/next-path";
import { providerName } from "@/lib/auth/oauth-providers";
import type { PendingRegistration } from "@/lib/auth/registration-types";
import { btnBlock, btnPrimary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 没给下次可重发时间时按 60 秒算
const DEFAULT_WAIT_MS = 60_000;

/// 下次可以重发的时刻
function resendAt(nextResendAt: string): number {
  return Date.parse(nextResendAt) || Date.now() + DEFAULT_WAIT_MS;
}

/// 第三方首次注册（画板 Complete）：补填 → 验证码 → 建好账号；任何一步发现记录过期都到过期态，两个标签页并发时到
/// 「已经建好了」。两步来回切换时用户名和邮箱留在这里。刷新页面时 `pendingCode` 有值就直接回到验证码步。
/// `next` 已经过 `safeNextPath`。
export function ProviderRegistrationFlow({
  pending,
  next,
}: {
  pending: PendingRegistration | "expired";
  next: string;
}) {
  const loginHref = authPageHref("/login", next);
  const initial = pending === "expired" ? null : pending;
  const [step, setStep] = useState<"fill" | "code" | "expired" | "taken">(
    initial === null ? "expired" : initial.pendingCode ? "code" : "fill",
  );
  const [email, setEmail] = useState(initial?.pendingCode?.email ?? initial?.suggestedEmail ?? "");
  const [username, setUsername] = useState(
    initial?.pendingCode?.username ?? initial?.suggestedUsername ?? "",
  );
  const [nextResend, setNextResend] = useState(
    initial?.pendingCode ? resendAt(initial.pendingCode.nextResendAt) : null,
  );
  const [issue, setIssue] = useState<RegistrationFillIssue | null>(null);

  if (initial === null || step === "expired") {
    return (
      <AuthShell
        icon={<StateIcon icon={CircleAlert} tone="danger" />}
        title={REGISTRATION_MESSAGES.expiredTitle}
        description={REGISTRATION_MESSAGES.expiredDescription(
          initial ? providerName(initial.provider) : null,
        )}
        back={loginHref}
      >
        <Link replace href={loginHref} className={cn(btnPrimary, btnBlock, "mt-8")}>
          {REGISTRATION_MESSAGES.backToLogin}
        </Link>
      </AuthShell>
    );
  }

  const name = providerName(initial.provider);

  if (step === "taken") {
    return (
      <AuthShell
        icon={<StateIcon icon={Info} tone="info" />}
        title={REGISTRATION_MESSAGES.takenTitle}
        description={REGISTRATION_MESSAGES.takenDescription(name)}
        back={loginHref}
      >
        <Link replace href={loginHref} className={cn(btnPrimary, btnBlock, "mt-8")}>
          {REGISTRATION_MESSAGES.backToLogin}
        </Link>
      </AuthShell>
    );
  }

  /// 回到补填步，可以带上为什么回来
  function backToFill(reason: RegistrationFillIssue | null) {
    setIssue(reason);
    setStep("fill");
  }

  if (step === "code") {
    return (
      <AuthShell
        title={REGISTRATION_MESSAGES.codeTitle}
        description={
          <>
            验证码已发到 <b className="font-medium text-fg-1 [overflow-wrap:anywhere]">{email}</b>
            ，10 分钟内有效。验证通过后账号才会建好。
          </>
        }
        back={loginHref}
      >
        <RegistrationCodeForm
          next={next}
          email={email}
          username={username}
          nextResend={nextResend}
          onResent={(at) => setNextResend(resendAt(at))}
          onExpired={() => setStep("expired")}
          onProviderTaken={() => setStep("taken")}
          onBackToFill={backToFill}
        />
      </AuthShell>
    );
  }

  return (
    <AuthShell
      title={REGISTRATION_MESSAGES.fillTitle}
      description={REGISTRATION_MESSAGES.fillDescription(name)}
      back={loginHref}
    >
      <RegistrationFillForm
        pending={initial}
        loginHref={loginHref}
        username={username}
        email={email}
        issue={issue}
        onSent={(values, at) => {
          setUsername(values.username);
          setEmail(values.email);
          setNextResend(resendAt(at));
          setIssue(null);
          setStep("code");
        }}
        onExpired={() => setStep("expired")}
      />
    </AuthShell>
  );
}
