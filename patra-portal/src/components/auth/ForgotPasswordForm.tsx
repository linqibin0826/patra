"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { MailCheck } from "lucide-react";
import Link from "next/link";
import { type FormEvent, useState } from "react";
import { useForm } from "react-hook-form";
import { AuthLink } from "@/components/auth/AuthLink";
import { AuthShell } from "@/components/auth/AuthShell";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { StateIcon } from "@/components/auth/StateIcon";
import { type ForgotPasswordFormValues, forgotPasswordSchema } from "@/lib/auth/form-schemas";
import { FIELD_MESSAGES, FORGOT_MESSAGES, resendWaitMessage } from "@/lib/auth/messages";
import { authPageHref } from "@/lib/auth/next-path";
import { requestPasswordReset } from "@/lib/identity/token-actions";
import { btnBlock, btnPrimary, btnSecondary, btnText } from "@/lib/portal-ui";
import { useCountdown } from "@/lib/use-countdown";
import { cn } from "@/lib/utils";
import { useAuthHandoffStore } from "@/store/auth-handoff";

/// 没给等待秒数时按 60 秒算
const DEFAULT_WAIT_MS = 60_000;

/// 下次可以再申请的时刻；`limited` 为 true 是被限流（文案不同）
interface Wait {
  until: number;
  limited: boolean;
}

/// 忘记密码页（简报 §3.17、§4.10）：输入态 → 已发送态（注册与否都一样）。自己渲染专注布局外壳，因为标题与
/// 副文案随态切换。冷却只在本页内存里，换页丢失可以接受（再点得到 429）。`next` 已经过 `safeNextPath`。
export function ForgotPasswordForm({ next }: { next: string }) {
  const loginHref = authPageHref("/login", next);
  const [initialEmail] = useState(() => useAuthHandoffStore.getState().take().email ?? "");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [wait, setWait] = useState<Wait | null>(null);
  const [alert, setAlert] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const left = useCountdown(wait?.until ?? null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema),
    mode: "onSubmit",
    reValidateMode: "onChange",
    defaultValues: { email: initialEmail },
  });

  /// 发出申请：首次与「重新发送」共用
  async function send(email: string) {
    if (busy) return;
    setBusy(true);
    setAlert(null);
    const result = await requestPasswordReset(email).catch(
      () => ({ ok: false, reason: "unavailable" }) as const,
    );
    setBusy(false);
    if (result.ok) {
      setSentTo(result.email);
      setWait({
        until: Date.parse(result.nextRequestAt) || Date.now() + DEFAULT_WAIT_MS,
        limited: false,
      });
      return;
    }
    switch (result.reason) {
      case "invalid":
        setSentTo(null);
        setError("email", { message: FIELD_MESSAGES.emailInvalid });
        return;
      case "throttled": {
        const until =
          Date.now() +
          (result.retryAfterSeconds === null ? DEFAULT_WAIT_MS : result.retryAfterSeconds * 1_000);
        setWait({ until, limited: true });
        if (sentTo === null) {
          setAlert(resendWaitMessage(Math.ceil((until - Date.now()) / 1_000), true));
        }
        return;
      }
      default:
        setAlert(FORGOT_MESSAGES.failed);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void handleSubmit((values) => send(values.email))(event);
  }

  if (sentTo !== null) {
    return (
      <AuthShell
        title={FORGOT_MESSAGES.sentTitle}
        description={
          <>
            如果 <b className="font-medium text-fg-1 [overflow-wrap:anywhere]">{sentTo}</b> 注册过
            Patra，几分钟内会收到一封邮件。链接 1 小时内有效。
          </>
        }
        back={loginHref}
        icon={<StateIcon icon={MailCheck} tone="info" />}
      >
        <div className="mt-7 flex flex-col gap-2.5 text-sm leading-5 text-fg-3">
          {alert && <FormAlert>{alert}</FormAlert>}
          <span>{FORGOT_MESSAGES.spam}</span>
          <span aria-live="polite">
            {busy ? (
              FORGOT_MESSAGES.submitting
            ) : left > 0 && wait ? (
              wait.limited ? (
                resendWaitMessage(left, true)
              ) : (
                `${left} 秒后可重新发送`
              )
            ) : (
              <button type="button" onClick={() => void send(sentTo)} className={btnText}>
                {FORGOT_MESSAGES.resend}
              </button>
            )}
          </span>
          <button
            type="button"
            onClick={() => {
              setSentTo(null);
              setAlert(null);
            }}
            className={cn(btnText, "self-start")}
          >
            {FORGOT_MESSAGES.changeEmail}
          </button>
        </div>
        <Link replace href={loginHref} className={cn(btnSecondary, btnBlock, "mt-8")}>
          {FORGOT_MESSAGES.backToLogin}
        </Link>
      </AuthShell>
    );
  }

  return (
    <AuthShell
      title={FORGOT_MESSAGES.title}
      description={FORGOT_MESSAGES.description}
      back={loginHref}
    >
      <form
        method="post"
        noValidate
        onSubmit={onSubmit}
        className={cn("flex flex-col gap-5", alert ? "mt-6" : "mt-8")}
      >
        {alert && <FormAlert>{alert}</FormAlert>}
        <FormField id="forgot-email" label="邮箱" error={errors.email?.message}>
          {(control) => (
            <input
              {...control}
              {...register("email")}
              type="email"
              autoComplete="email"
              spellCheck={false}
              placeholder="name@example.com"
              readOnly={busy}
              // biome-ignore lint/a11y/noAutofocus: 简报 §6 要求进入找回页时焦点在邮箱框
              autoFocus
            />
          )}
        </FormField>
        <div className="mt-1">
          <button
            type="submit"
            aria-disabled={busy || undefined}
            className={cn(btnPrimary, btnBlock)}
          >
            {busy ? FORGOT_MESSAGES.submitting : FORGOT_MESSAGES.submit}
          </button>
        </div>
      </form>
      <p className="mt-5 text-center text-base leading-5 text-fg-2">
        {FORGOT_MESSAGES.remembered}
        <AuthLink replace href={loginHref}>
          登录
        </AuthLink>
      </p>
    </AuthShell>
  );
}
