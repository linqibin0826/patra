"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { AuthLink } from "@/components/auth/AuthLink";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { ProviderMark } from "@/components/auth/ProviderMark";
import { UsernameField } from "@/components/auth/UsernameField";
import { useServerFieldErrors } from "@/components/auth/use-auth-submit";
import { useBlurValidation } from "@/components/auth/use-blur-validation";
import { providerRegistrationSchema } from "@/lib/auth/form-schemas";
import {
  REGISTRATION_MESSAGES,
  resendWaitMessage,
  usernameHint,
  usernameMessage,
} from "@/lib/auth/messages";
import { authPageHref } from "@/lib/auth/next-path";
import { providerName } from "@/lib/auth/oauth-providers";
import { postRegistrationCode } from "@/lib/auth/post-registration";
import type { PendingRegistration } from "@/lib/auth/registration-types";
import { btnBlock, btnPrimary, btnSecondary, btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";
import { useAuthHandoffStore } from "@/store/auth-handoff";

/// 撞车「去登录」：登录原账号后到这里绑定（简报 §2.2 规则 6）
const CLASH_LOGIN_HREF = authPageHref("/login", "/settings/security");

/// 从验证码步退回来的原因：用户名或邮箱在这期间被别人抢先了
export type RegistrationFillIssue = "username-race" | "email-clash";

interface RegistrationFillFormProps {
  pending: PendingRegistration;
  loginHref: string;
  /// 打开时的用户名、邮箱：第一次是预填值，退回来时是上次提交的
  username: string;
  email: string;
  issue: RegistrationFillIssue | null;
  onSent: (values: { username: string; email: string }, nextResendAt: string) => void;
  onExpired: () => void;
}

/// 补填步（画板 Complete）：已确认的第三方账号、用户名、邮箱。邮箱撞车不建账号也不合并（决策 D），在字段下方引导先用
/// 原方式登录再绑定。用户名和邮箱提交时绑定验证码，下一步不能换。
export function RegistrationFillForm({
  pending,
  loginHref,
  username: initialUsername,
  email: initialEmail,
  issue,
  onSent,
  onExpired,
}: RegistrationFillFormProps) {
  const name = providerName(pending.provider);
  const [alert, setAlert] = useState<string | null>(null);
  const [clash, setClash] = useState(issue === "email-clash");
  const [raced, setRaced] = useState(issue === "username-race" ? initialUsername : null);
  const [busy, setBusy] = useState(false);
  const serverErrors = useServerFieldErrors();
  const {
    register,
    handleSubmit,
    formState: { errors },
    getFieldState,
    trigger,
    setFocus,
    setValue,
    watch,
  } = useForm({
    resolver: zodResolver(providerRegistrationSchema),
    mode: "onSubmit",
    reValidateMode: "onSubmit",
    defaultValues: { username: initialUsername, email: initialEmail },
  });
  const validateOnBlur = useBlurValidation(trigger);
  const putHandoff = useAuthHandoffStore((state) => state.put);
  const toLoginRef = useRef<HTMLAnchorElement | null>(null);
  // 撞车块出现时焦点到「去登录」：这是读者接下来最可能做的事
  useEffect(() => {
    if (clash) {
      toLoginRef.current?.focus();
    }
  }, [clash]);
  const [username, email] = watch(["username", "email"]);

  /// 字段显示错误之后，每次输入都重新校验它，改对了错误立即消失
  function revalidate(field: "username" | "email") {
    if (getFieldState(field).error) {
      void trigger(field);
    }
  }

  /// 校验通过后发验证码，按结果进下一步或把错误放到对应位置
  async function send(values: { username: string; email: string }) {
    if (busy) return;
    setBusy(true);
    const result = await postRegistrationCode(values);
    setBusy(false);
    if (result.ok) {
      onSent(values, result.nextResendAt);
      return;
    }
    switch (result.reason) {
      case "expired":
        onExpired();
        return;
      case "taken":
        setClash(result.email);
        if (result.username) {
          serverErrors.show({
            username: { value: values.username, message: usernameMessage("ALREADY_TAKEN") },
          });
        }
        return;
      case "invalid-fields": {
        const first = serverErrors.showFieldErrors(result.fieldErrors, values);
        if (first) {
          setFocus(first);
          return;
        }
        setAlert(REGISTRATION_MESSAGES.sendFailed);
        return;
      }
      case "throttled":
        setAlert(resendWaitMessage(result.retryAfterSeconds ?? 60, true));
        return;
      default:
        setAlert(REGISTRATION_MESSAGES.sendFailed);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAlert(null);
    setClash(false);
    setRaced(null);
    serverErrors.clear();
    void handleSubmit((values) => send(values))(event);
  }

  const normalized = username.trim().toLowerCase();
  const usernameError =
    errors.username?.message ??
    serverErrors.messageFor("username", normalized) ??
    (raced !== null && raced === normalized ? REGISTRATION_MESSAGES.usernameRaced : undefined);
  const emailError = errors.email?.message ?? serverErrors.messageFor("email", email.trim());
  const prefilled = pending.suggestedUsername !== null && username === pending.suggestedUsername;

  return (
    <form method="post" noValidate onSubmit={onSubmit} className="mt-6 flex flex-col gap-5">
      <div className="flex items-center gap-3 rounded-lg border border-border-default bg-bg-elevated px-3.5 py-3">
        <ProviderMark provider={pending.provider} className="shrink-0" />
        <div className="min-w-0">
          <div className="text-sm leading-5 text-fg-2">{REGISTRATION_MESSAGES.confirmed(name)}</div>
          <div className="font-mono text-sm leading-5 text-fg-1 [overflow-wrap:anywhere]">
            {pending.accountLabel}
          </div>
        </div>
      </div>
      {alert && <FormAlert>{alert}</FormAlert>}
      <FormField
        id="registration-username"
        label="用户名"
        error={usernameError}
        hint={
          <>
            {usernameHint(username)}
            {prefilled && (
              <span className="mt-2 block">{REGISTRATION_MESSAGES.usernamePrefilled(name)}</span>
            )}
          </>
        }
      >
        {(control) => (
          <UsernameField
            {...control}
            {...register("username", {
              onBlur: validateOnBlur("username"),
              onChange: () => revalidate("username"),
            })}
            className={cn(control.className, "font-mono")}
            readOnly={busy}
            autoFocus
          />
        )}
      </FormField>
      <FormField
        id="registration-email"
        label="邮箱"
        error={emailError}
        invalid={clash}
        hint={clash ? undefined : REGISTRATION_MESSAGES.emailHint(pending)}
      >
        {(control) => (
          <input
            {...control}
            {...register("email", {
              onBlur: validateOnBlur("email"),
              onChange: () => {
                setClash(false);
                revalidate("email");
              },
            })}
            type="email"
            autoComplete="email"
            spellCheck={false}
            placeholder="name@example.com"
            readOnly={busy}
          />
        )}
      </FormField>
      {clash && (
        <div
          role="alert"
          className="disclosure-body flex flex-col gap-3 rounded-lg bg-status-warning-bg p-3.5 text-base leading-5.5 text-fg-1"
        >
          <p>
            <b className="font-medium">{REGISTRATION_MESSAGES.clashTitle}</b>
            {REGISTRATION_MESSAGES.clashNoChange}
          </p>
          <p>{REGISTRATION_MESSAGES.clashHow(name)}</p>
          <div className="flex flex-wrap items-center gap-4">
            <Link
              replace
              ref={toLoginRef}
              href={CLASH_LOGIN_HREF}
              // 邮箱经内存带过去，不进地址栏
              onClick={() => putHandoff({ email: email.trim(), notice: "email-clash" })}
              className={cn(btnSecondary, "px-3.5 py-1.5 text-sm max-[880px]:w-full")}
            >
              {REGISTRATION_MESSAGES.toLogin}
            </Link>
            <button
              type="button"
              className={btnText}
              onClick={() => {
                setClash(false);
                setValue("email", "");
                setFocus("email");
              }}
            >
              {REGISTRATION_MESSAGES.changeEmail}
            </button>
          </div>
        </div>
      )}
      <button
        type="submit"
        aria-disabled={busy || undefined}
        className={cn(btnPrimary, btnBlock, "mt-1")}
      >
        {busy ? REGISTRATION_MESSAGES.sending : REGISTRATION_MESSAGES.send}
      </button>
      {!clash && (
        <p className="text-center text-sm leading-5 text-fg-3">
          已经有 Patra 账号？先
          <AuthLink replace href={loginHref}>
            登录
          </AuthLink>
          ，再到「设置 › 安全」绑定 {name}。
        </p>
      )}
    </form>
  );
}
