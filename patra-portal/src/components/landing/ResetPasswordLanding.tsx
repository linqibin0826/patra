"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { TokenLandingPage } from "@/components/landing/TokenLandingPage";
import { type ResetPasswordFormValues, resetPasswordSchema } from "@/lib/auth/form-schemas";
import { fieldErrorMessage, RESET_LANDING_MESSAGES as M, waitMinutes } from "@/lib/auth/messages";
import { resetPassword } from "@/lib/identity/token-actions";
import { btnBlock, btnPrimary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import { useAuthHandoffStore } from "@/store/auth-handoff";

/// 重设密码落地页（简报 §3.18 b、spec §8.3）：就绪态里填新密码与确认；成功不停在本页，带着邮箱与「密码已重设」
/// 提示去登录页（邮箱经内存带过去，不进地址栏）。密码不合规与限流留在就绪态就地提示，令牌没被消费可以改了再交。
export function ResetPasswordLanding() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const putHandoff = useAuthHandoffStore((state) => state.put);
  const [alert, setAlert] = useState<string | null>(null);
  const {
    register,
    trigger,
    getValues,
    setError,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    mode: "onSubmit",
    reValidateMode: "onChange",
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  /// 先按前端规则校验，再提交令牌与新密码
  async function submit(token: string) {
    setAlert(null);
    if (!(await trigger())) {
      return { kind: "stay" } as const;
    }
    const result = await resetPassword({ token, newPassword: getValues("newPassword") });
    if (result.ok) {
      // 令牌所属用户的会话已全部撤销；登录着的若是另一个账号，它的会话还在，所以只失效不写 null
      void queryClient.invalidateQueries({ queryKey: queryKeys.currentUser });
      putHandoff({ email: result.email, notice: "password-reset" });
      router.replace("/login");
      return { kind: "leaving" } as const;
    }
    switch (result.reason) {
      case "invalid":
        return { kind: "invalid" } as const;
      case "invalid-password":
        setError("newPassword", { message: fieldErrorMessage("password", result.code) });
        setFocus("newPassword");
        return { kind: "stay" } as const;
      case "throttled":
        setAlert(`提交太频繁，请 ${waitMinutes(result.retryAfterSeconds)} 分钟后再试`);
        return { kind: "stay" } as const;
      default:
        return { kind: "unavailable" } as const;
    }
  }

  return (
    <TokenLandingPage<never>
      title={M.title}
      description={M.description}
      actionLabel={M.action}
      pendingLabel={M.pending}
      focusAction={false}
      submit={submit}
      renderSucceeded={() => ({ tone: "success", title: M.title, description: "" })}
      renderInvalid={() => ({
        tone: "danger",
        title: M.invalidTitle,
        description: M.invalidDescription,
        actions: (
          <Link href="/forgot-password" className={cn(btnPrimary, btnBlock)}>
            {M.again}
          </Link>
        ),
      })}
    >
      {alert && <FormAlert>{alert}</FormAlert>}
      <FormField
        id="reset-new-password"
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
            readOnly={isSubmitting}
            autoFocus
          />
        )}
      </FormField>
      <FormField
        id="reset-confirm-password"
        label="确认新密码"
        error={errors.confirmPassword?.message}
      >
        {(control) => (
          <PasswordInput
            {...control}
            {...register("confirmPassword")}
            autoComplete="new-password"
            spellCheck={false}
          />
        )}
      </FormField>
    </TokenLandingPage>
  );
}
