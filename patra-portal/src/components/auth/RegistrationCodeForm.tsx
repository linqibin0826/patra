"use client";

import { type FormEvent, useRef, useState } from "react";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import type { RegistrationFillIssue } from "@/components/auth/RegistrationFillForm";
import { useAuthSubmit } from "@/components/auth/use-auth-submit";
import {
  LINK_MESSAGES,
  REGISTRATION_MESSAGES,
  resendWaitMessage,
  TOAST_MESSAGES,
} from "@/lib/auth/messages";
import {
  postLink,
  postLinkCode,
  postRegistrationCode,
  postRegistrationComplete,
} from "@/lib/auth/post-registration";
import type { CompleteResult, LinkResult } from "@/lib/auth/registration-types";
import { btnBlock, btnPrimary, btnText } from "@/lib/portal-ui";
import { useCountdown } from "@/lib/use-countdown";
import { useReadyAnnouncement } from "@/lib/use-ready-announcement";
import { cn } from "@/lib/utils";

/// 这一步要做什么：建账号要连用户名一起重发；绑定到已有账号只看邮箱
export type RegistrationCodeFlow =
  | { kind: "register"; username: string }
  | { kind: "link"; providerName: string };

interface RegistrationCodeFormProps {
  next: string;
  email: string;
  flow: RegistrationCodeFlow;
  /// 下次可以重发的时刻（毫秒时间戳）
  nextResend: number | null;
  onResent: (nextResendAt: string) => void;
  onExpired: () => void;
  onProviderTaken: () => void;
  /// 绑定时目标账号已经绑了这一家的另一个账号
  onAlreadyLinked: () => void;
  onBackToFill: (issue: RegistrationFillIssue | null) => void;
}

/// 验证码步（画板 Complete）：6 位数字，填回发起的页面才建账号或绑定（决策 D）。验证码失效后锁住输入，只能重发。
/// 绑定时发码接口不说明信发没发，这一步不提「已发送」和垃圾邮件。
export function RegistrationCodeForm({
  next,
  email,
  flow,
  nextResend,
  onResent,
  onExpired,
  onProviderTaken,
  onAlreadyLinked,
  onBackToFill,
}: RegistrationCodeFormProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [code, setCode] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [alert, setAlert] = useState<string | null>(null);
  const [locked, setLocked] = useState(false);
  const [resending, setResending] = useState(false);
  const [limitedUntil, setLimitedUntil] = useState<number | null>(null);
  const until = limitedUntil ?? nextResend;
  const left = useCountdown(until);
  const announcement = useReadyAnnouncement(left > 0);
  const linking = flow.kind === "link";

  /// 这个码不能再用了：提示后只能重发
  function lock(message: string) {
    setAlert(message);
    setCode("");
    setLocked(true);
  }

  /// 验证码用掉了但账号没建成：用户名或邮箱被抢，回补填步
  function takenIssue(result: { email: boolean }): RegistrationFillIssue {
    return result.email ? "email-clash" : "username-race";
  }

  const { submitting, submit } = useAuthSubmit<
    { code: string },
    Exclude<CompleteResult | LinkResult, { ok: true }>
  >({
    next,
    action: linking ? postLink : postRegistrationComplete,
    successToast: linking ? LINK_MESSAGES.signedIn(flow.providerName) : TOAST_MESSAGES.registered,
    onFailure: (failure) => {
      switch (failure.reason) {
        case "code-invalid":
          setFieldError(REGISTRATION_MESSAGES.codeInvalid);
          inputRef.current?.focus();
          inputRef.current?.select();
          return;
        case "code-expired":
          lock(REGISTRATION_MESSAGES.codeExpired);
          return;
        case "provider-taken":
          onProviderTaken();
          return;
        case "already-linked":
          onAlreadyLinked();
          return;
        case "expired":
          onExpired();
          return;
        case "taken":
          onBackToFill(takenIssue(failure));
          return;
        default:
          setAlert(REGISTRATION_MESSAGES.verifyFailed);
      }
    },
  });

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || locked) return;
    setAlert(null);
    if (!code) {
      setFieldError(REGISTRATION_MESSAGES.codeRequired);
      inputRef.current?.focus();
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      setFieldError(REGISTRATION_MESSAGES.codeFormat);
      inputRef.current?.focus();
      return;
    }
    setFieldError(null);
    void submit({ code });
  }

  /// 重发：同样的内容再交一次，旧码作废
  async function resend() {
    if (resending) return;
    setResending(true);
    setAlert(null);
    const result = linking
      ? await postLinkCode({ email })
      : await postRegistrationCode({ username: flow.username, email });
    setResending(false);
    if (result.ok) {
      setLimitedUntil(null);
      setLocked(false);
      setCode("");
      setFieldError(null);
      onResent(result.nextResendAt);
      return;
    }
    switch (result.reason) {
      case "expired":
        onExpired();
        return;
      case "taken":
        onBackToFill(takenIssue(result));
        return;
      case "invalid-fields":
        onBackToFill(null);
        return;
      case "throttled":
        setLimitedUntil(Date.now() + (result.retryAfterSeconds ?? 60) * 1_000);
        return;
      default:
        setAlert(REGISTRATION_MESSAGES.resendFailed);
    }
  }

  return (
    <form noValidate onSubmit={onSubmit} className="mt-6 flex flex-col gap-5">
      {alert && <FormAlert>{alert}</FormAlert>}
      <FormField id="registration-code" label="验证码" error={fieldError ?? undefined}>
        {(control) => (
          <input
            {...control}
            ref={inputRef}
            className={cn(
              control.className,
              "font-mono text-xl tracking-spaced max-[880px]:text-xl",
            )}
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            // 不写 maxLength：浏览器会先截断粘贴的内容，带空格的验证码就丢了后面几位，长度在 onChange 里限制
            spellCheck={false}
            placeholder="000000"
            value={code}
            onChange={(event) => {
              setCode(event.target.value.replace(/\D/g, "").slice(0, 6));
              setFieldError(null);
            }}
            readOnly={submitting || locked}
            // biome-ignore lint/a11y/noAutofocus: 进入这一步就是来填验证码的
            autoFocus
          />
        )}
      </FormField>
      <button
        type="submit"
        aria-disabled={submitting || locked || undefined}
        className={cn(btnPrimary, btnBlock, "mt-1")}
      >
        {submitting
          ? REGISTRATION_MESSAGES.verifying
          : linking
            ? LINK_MESSAGES.submit
            : REGISTRATION_MESSAGES.complete}
      </button>
      <div className="flex flex-col items-start gap-2 text-sm leading-5 text-fg-3">
        {!linking && <span>{REGISTRATION_MESSAGES.spam}</span>}
        {resending ? (
          <span>{REGISTRATION_MESSAGES.sending}</span>
        ) : left > 0 ? (
          <span>
            {linking && limitedUntil === null
              ? LINK_MESSAGES.wait(left)
              : resendWaitMessage(left, limitedUntil !== null)}
          </span>
        ) : (
          <button type="button" className={btnText} onClick={() => void resend()}>
            {REGISTRATION_MESSAGES.resend}
          </button>
        )}
        <span role="status" className="sr-only">
          {announcement}
        </span>
        <button type="button" className={btnText} onClick={() => onBackToFill(null)}>
          {linking ? LINK_MESSAGES.back : REGISTRATION_MESSAGES.backToFill}
        </button>
      </div>
    </form>
  );
}
