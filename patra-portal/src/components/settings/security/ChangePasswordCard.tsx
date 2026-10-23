"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { type FormEvent, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { SettingsCard } from "@/components/settings/SettingsCard";
import { type ChangePasswordFormValues, changePasswordSchema } from "@/lib/auth/form-schemas";
import {
  CHANGE_PASSWORD_MESSAGES,
  FIELD_MESSAGES,
  fieldErrorMessage,
  newPasswordMessage,
  waitMinutes,
} from "@/lib/auth/messages";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { changePassword } from "@/lib/identity/security-actions";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnPrimary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";

const PAGE = "/settings/security";
const EMPTY: ChangePasswordFormValues = {
  currentPassword: "",
  newPassword: "",
  confirmPassword: "",
};

/// 安全页 · 修改密码（简报 §3.11 卡片 1、§4.8）：当前密码、新密码、确认新密码。每次都要当前密码，不走再认证。
/// 成功后清空、提示、刷新设备列表（只剩本机）；当前密码错只提示，不登出。
export function ChangePasswordCard() {
  const queryClient = useQueryClient();
  const leave = useCommonFailureRedirect(PAGE);
  const [alert, setAlert] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    setFocus,
    formState: { errors },
  } = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    mode: "onSubmit",
    reValidateMode: "onChange",
    defaultValues: EMPTY,
  });

  /// 校验通过后提交，按结果清空或把错误放到对应字段
  async function save(values: ChangePasswordFormValues) {
    if (busy) return;
    setBusy(true);
    const result = await orUnavailable(
      changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      }),
    );
    setBusy(false);
    if (result.ok) {
      reset(EMPTY);
      toast.success(CHANGE_PASSWORD_MESSAGES.changed);
      queryClient.setQueryData(queryKeys.currentUser, result.user);
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions });
      return;
    }
    switch (result.reason) {
      case "wrong-password":
        setError("currentPassword", { message: FIELD_MESSAGES.currentPasswordWrong });
        setFocus("currentPassword");
        return;
      case "invalid-fields": {
        const current = result.fieldErrors.find((error) => error.field === "currentPassword");
        const next = result.fieldErrors.find((error) => error.field === "newPassword");
        if (current) {
          setError("currentPassword", { message: fieldErrorMessage("password", current.code) });
        }
        if (next) {
          setError("newPassword", { message: newPasswordMessage(next.code) });
        }
        if (current || next) {
          setFocus(current ? "currentPassword" : "newPassword");
          return;
        }
        setAlert(CHANGE_PASSWORD_MESSAGES.failed);
        return;
      }
      case "throttled":
        setAlert(
          `尝试次数过多，请 ${waitMinutes(result.retryAfterSeconds)} 分钟后再试。在此期间登录也会被限制。`,
        );
        return;
      case "pending-deletion":
      case "signed-out":
        leave(result.reason);
        return;
      default:
        setAlert(CHANGE_PASSWORD_MESSAGES.failed);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAlert(null);
    void handleSubmit(save)(event);
  }

  return (
    <SettingsCard title="修改密码">
      {alert && <FormAlert>{alert}</FormAlert>}
      <form
        // 页面还没接管表单就提交时（脚本没加载完），浏览器按这里的 method 提交：用 POST，密码不进地址栏
        method="post"
        noValidate
        onSubmit={onSubmit}
        className="flex flex-col gap-5"
      >
        <FormField
          id="security-current-password"
          label="当前密码"
          error={errors.currentPassword?.message}
          aside={
            <Link
              href="/forgot-password"
              className="link-draw text-sm text-link hover:text-link-hover"
            >
              {CHANGE_PASSWORD_MESSAGES.forgot}
            </Link>
          }
        >
          {(control) => (
            <PasswordInput
              {...control}
              {...register("currentPassword")}
              autoComplete="current-password"
              spellCheck={false}
              readOnly={busy}
            />
          )}
        </FormField>
        <FormField
          id="security-new-password"
          label="新密码"
          hint="至少 8 位"
          error={errors.newPassword?.message}
        >
          {(control) => (
            <PasswordInput
              {...control}
              {...register("newPassword")}
              autoComplete="new-password"
              spellCheck={false}
              readOnly={busy}
            />
          )}
        </FormField>
        <FormField
          id="security-confirm-password"
          label="确认新密码"
          error={errors.confirmPassword?.message}
        >
          {(control) => (
            <PasswordInput
              {...control}
              {...register("confirmPassword")}
              autoComplete="new-password"
              spellCheck={false}
              readOnly={busy}
            />
          )}
        </FormField>
        <p className="text-sm leading-5 text-fg-3">{CHANGE_PASSWORD_MESSAGES.note}</p>
        <div>
          <button type="submit" aria-disabled={busy || undefined} className={btnPrimary}>
            {busy ? CHANGE_PASSWORD_MESSAGES.submitting : CHANGE_PASSWORD_MESSAGES.submit}
          </button>
        </div>
      </form>
    </SettingsCard>
  );
}
