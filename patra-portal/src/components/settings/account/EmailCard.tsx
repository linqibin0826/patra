"use client";

import { Mail } from "lucide-react";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { SettingsCard } from "@/components/settings/SettingsCard";
import { StatusPill } from "@/components/ui/status-pill";
import { emailChangeSchema } from "@/lib/auth/form-schemas";
import { ACCOUNT_MESSAGES, EMAIL_MESSAGES, newEmailMessage } from "@/lib/auth/messages";
import type { CurrentUser } from "@/lib/auth/types";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { formatClock } from "@/lib/format-time";
import { cancelEmailChange, requestEmailChange } from "@/lib/identity/account-actions";
import type { PendingEmailChange } from "@/lib/identity/account-schema";
import { btnPrimary, btnSecondary, btnText } from "@/lib/portal-ui";
import { useReauth } from "@/lib/reauth/use-reauth";
import { cn } from "@/lib/utils";

const PAGE = "/settings/account";
const SMALL = "px-3.5 py-1.5 text-sm";

/// 卡片提示条的几种情况
type Alert = "pending-exists" | "throttled" | "failed";

const ALERTS: Record<Alert, string> = {
  "pending-exists": EMAIL_MESSAGES.pendingExists,
  throttled: EMAIL_MESSAGES.throttled,
  failed: ACCOUNT_MESSAGES.saveFailed,
};

/// 账号页 · 邮箱卡片（简报 §3.10 卡片 2、§4.7）：邮箱与验证状态、未验证时重发；「更改邮箱」在卡片内展开，
/// 提交经再认证包装，成功后显示待确认块（到期自动消失）并禁用「更改邮箱」。锚点 `#email`（横幅的「更改邮箱」）。
export function EmailCard({
  user,
  pending: initialPending,
}: {
  user: CurrentUser;
  pending: PendingEmailChange | null;
}) {
  const router = useRouter();
  const leave = useCommonFailureRedirect(PAGE);
  const { withReauth } = useReauth();
  const inputRef = useRef<HTMLInputElement | null>(null);
  // 首帧只用服务端给的数据，不看当前时间：服务端渲染与水合的结果一致；过期的由下面的 effect 挂载后立即收起
  const [pending, setPending] = useState(initialPending);
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [alert, setAlert] = useState<Alert | null>(null);
  const [busy, setBusy] = useState<"submit" | "cancel" | null>(null);

  // 页面刷新带回新的待确认（如「已有待确认」之后）时同步过来
  useEffect(() => {
    setPending(initialPending);
  }, [initialPending]);

  // 到期那一刻收起待确认块，「更改邮箱」恢复可点；已经过期的立即收起
  useEffect(() => {
    if (!pending) return;
    const remaining = Date.parse(pending.expiresAt) - Date.now();
    if (remaining <= 0) {
      setPending(null);
      return;
    }
    const timer = setTimeout(() => setPending(null), remaining);
    return () => clearTimeout(timer);
  }, [pending]);

  useEffect(() => {
    if (editing) inputRef.current?.focus();
  }, [editing]);

  const schema = emailChangeSchema(user.email);

  /// 前端校验，返回问题文案或 null
  function frontEndIssue(raw: string): string | null {
    const result = schema.safeParse({ newEmail: raw });
    return result.success ? null : (result.error.issues[0]?.message ?? null);
  }

  /// 提交改邮箱：先校验，再经再认证包装请求
  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const issue = frontEndIssue(value);
    if (issue) {
      setError(issue);
      inputRef.current?.focus();
      return;
    }
    setBusy("submit");
    setAlert(null);
    const result = await withReauth(() => requestEmailChange(value.trim())).catch(
      () => ({ ok: false, reason: "unavailable" }) as const,
    );
    setBusy(null);
    if (result.ok) {
      setPending(result.pending);
      setEditing(false);
      setValue("");
      return;
    }
    switch (result.reason) {
      case "cancelled":
        return;
      case "invalid":
        setError(newEmailMessage(result.code));
        return;
      case "taken":
        setError(EMAIL_MESSAGES.taken);
        return;
      case "pending-exists":
        setAlert("pending-exists");
        router.refresh();
        return;
      case "throttled":
        setAlert("throttled");
        return;
      case "pending-deletion":
      case "signed-out":
        leave(result.reason);
        return;
      default:
        setAlert("failed");
    }
  }

  /// 取消待确认的更改
  async function cancelPending() {
    if (busy) return;
    setBusy("cancel");
    setAlert(null);
    const result = await cancelEmailChange().catch(
      () => ({ ok: false, reason: "unavailable" }) as const,
    );
    setBusy(null);
    if (result.ok) {
      setPending(null);
      toast.success(EMAIL_MESSAGES.cancelled);
      return;
    }
    if (result.reason === "unavailable") {
      setAlert("failed");
      return;
    }
    leave(result.reason);
  }

  return (
    <SettingsCard id="email" title="邮箱">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex min-w-0 flex-1 items-center gap-2.5">
          <span className="truncate text-md text-fg-1">{user.email}</span>
          {user.emailVerified ? (
            <StatusPill tone="success">{EMAIL_MESSAGES.verified}</StatusPill>
          ) : (
            <StatusPill tone="warning">{EMAIL_MESSAGES.unverified}</StatusPill>
          )}
        </div>
        {!editing && (
          <button
            type="button"
            onClick={() => {
              if (pending) return;
              setError(null);
              setAlert(null);
              setEditing(true);
            }}
            aria-disabled={pending !== null || undefined}
            className={cn(btnSecondary, SMALL)}
          >
            更改邮箱
          </button>
        )}
      </div>
      {!user.emailVerified && (
        <p className="flex flex-wrap items-baseline gap-2.5 text-sm leading-5 text-fg-3">
          {EMAIL_MESSAGES.unverifiedNote}
          <ResendVerificationButton />
        </p>
      )}
      {pending && (
        <div className="flex items-center gap-2.5 rounded-lg bg-status-info-bg px-3.5 py-3 text-base leading-5 text-fg-1">
          <Mail className="size-4 shrink-0 text-status-info" aria-hidden="true" />
          <span className="min-w-0 flex-1 [overflow-wrap:anywhere]">
            正在等待 <b className="font-medium">{pending.newEmail}</b> 确认 ·{" "}
            {formatClock(pending.expiresAt, Date.now())} 前有效
          </span>
          <button
            type="button"
            onClick={cancelPending}
            aria-disabled={busy === "cancel" || undefined}
            className={btnText}
          >
            {busy === "cancel" ? "取消中…" : "取消更改"}
          </button>
        </div>
      )}
      {alert && <FormAlert>{ALERTS[alert]}</FormAlert>}
      {editing && (
        <form
          noValidate
          onSubmit={onSubmit}
          className="flex flex-col gap-4 border-t border-border-subtle pt-5"
        >
          <FormField
            id="account-new-email"
            label="新邮箱"
            hint={EMAIL_MESSAGES.changeNote}
            error={error ?? undefined}
          >
            {(control) => (
              <input
                {...control}
                ref={inputRef}
                type="email"
                autoComplete="email"
                spellCheck={false}
                placeholder="name@example.com"
                value={value}
                readOnly={busy === "submit"}
                onChange={(event) => {
                  setValue(event.target.value);
                  if (error) setError(frontEndIssue(event.target.value));
                }}
              />
            )}
          </FormField>
          <div className="flex gap-3">
            <button
              type="submit"
              aria-disabled={busy === "submit" || undefined}
              className={cn(btnPrimary, SMALL)}
            >
              {busy === "submit" ? "发送中…" : "发送确认邮件"}
            </button>
            <button
              type="button"
              onClick={() => setEditing(false)}
              className={cn(btnSecondary, SMALL)}
            >
              取消
            </button>
          </div>
        </form>
      )}
    </SettingsCard>
  );
}
