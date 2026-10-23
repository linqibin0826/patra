"use client";

import { useQueryClient } from "@tanstack/react-query";
import { usePathname, useSearchParams } from "next/navigation";
import { type FormEvent, type RefObject, useRef, useState } from "react";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { Dialog } from "@/components/ui/dialog";
import { REAUTH_MESSAGES, waitMinutes } from "@/lib/auth/messages";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { reauthenticate } from "@/lib/identity/reauth-actions";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { useReauthStore } from "@/store/reauth";

/// 再认证对话框（简报 §3.14、§4.9）：store 打开它，验证成功 `settle(true)`、取消或会话没了 `settle(false)`。
/// 每次打开都是新的表单（关闭时卸载），不带上一次的输入和错误。
export function ReauthDialog() {
  const open = useReauthStore((state) => state.open);
  const settle = useReauthStore((state) => state.settle);
  const [busy, setBusy] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) settle(false);
      }}
      title={REAUTH_MESSAGES.title}
      description={REAUTH_MESSAGES.description}
      size="sm"
      dismissible={!busy}
      initialFocus={inputRef}
    >
      {open && <ReauthForm inputRef={inputRef} onBusyChange={setBusy} />}
    </Dialog>
  );
}

/// 对话框里的表单：一个密码框和两个按钮
function ReauthForm({
  inputRef,
  onBusyChange,
}: {
  inputRef: RefObject<HTMLInputElement | null>;
  onBusyChange: (busy: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const settle = useReauthStore((state) => state.settle);
  const pathname = usePathname();
  const query = useSearchParams().toString();
  const leave = useCommonFailureRedirect(query ? `${pathname}?${query}` : pathname);
  const [password, setPassword] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [alert, setAlert] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /// 进入 / 退出验证中，同步给外层（验证中 `Esc` 与点遮罩不关）
  function markBusy(next: boolean) {
    setBusy(next);
    onBusyChange(next);
  }

  /// 提交：空值就地提示；否则验证密码，按结果关闭或显示错误
  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setAlert(null);
    if (!password) {
      setFieldError(REAUTH_MESSAGES.required);
      inputRef.current?.focus();
      return;
    }
    setFieldError(null);
    markBusy(true);
    const result = await reauthenticate(password).catch(
      () => ({ ok: false, reason: "unavailable" }) as const,
    );
    markBusy(false);
    if (result.ok) {
      queryClient.setQueryData(queryKeys.currentUser, result.user);
      settle(true);
      return;
    }
    switch (result.reason) {
      case "wrong-password":
        setFieldError(REAUTH_MESSAGES.wrong);
        setPassword("");
        inputRef.current?.focus();
        return;
      case "throttled":
        setAlert(`尝试次数过多，请 ${waitMinutes(result.retryAfterSeconds)} 分钟后再试`);
        return;
      case "signed-out":
        settle(false);
        leave("signed-out");
        return;
      default:
        setAlert(REAUTH_MESSAGES.failed);
    }
  }

  return (
    <form noValidate onSubmit={onSubmit} className="flex flex-col gap-5">
      {alert && <FormAlert>{alert}</FormAlert>}
      <FormField
        id="reauth-password"
        label="密码"
        error={fieldError ?? undefined}
        aside={
          <a
            href="/forgot-password"
            target="_blank"
            rel="noopener noreferrer"
            className="link-draw text-sm text-link hover:text-link-hover"
          >
            {REAUTH_MESSAGES.forgot}
          </a>
        }
      >
        {(control) => (
          <PasswordInput
            {...control}
            ref={inputRef}
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
              if (fieldError) setFieldError(null);
            }}
            autoComplete="current-password"
            spellCheck={false}
            readOnly={busy}
          />
        )}
      </FormField>
      <div className="flex justify-end gap-3 max-[880px]:flex-col-reverse">
        <button type="button" className={btnSecondary} onClick={() => settle(false)}>
          {REAUTH_MESSAGES.cancel}
        </button>
        <button type="submit" aria-disabled={busy || undefined} className={btnPrimary}>
          {busy ? REAUTH_MESSAGES.verifying : REAUTH_MESSAGES.confirm}
        </button>
      </div>
    </form>
  );
}
