"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { type FormEvent, useState } from "react";
import { useForm } from "react-hook-form";
import { AuthLink } from "@/components/auth/AuthLink";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { useAuthSubmit, useServerFieldErrors } from "@/components/auth/use-auth-submit";
import { register as registerAccount } from "@/lib/auth/actions";
import { registerSchema } from "@/lib/auth/form-schemas";
import { FIELD_MESSAGES, REGISTER_UNAVAILABLE_MESSAGE, TOAST_MESSAGES } from "@/lib/auth/messages";
import { authPageHref } from "@/lib/auth/next-path";
import { btnBlock, btnPrimary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 注册表单（spec 第 9 节）：提示条、邮箱、密码、确认密码、不支持找回密码的提醒、提交按钮、去登录的
/// 链接。`next` 已经过 `safeNextPath`。
export function RegisterForm({ next }: { next: string }) {
  const [alert, setAlert] = useState<string | null>(null);
  const serverErrors = useServerFieldErrors();
  const {
    register,
    handleSubmit,
    formState: { errors },
    getFieldState,
    getValues,
    trigger,
    setFocus,
    watch,
  } = useForm({
    resolver: zodResolver(registerSchema),
    mode: "onBlur",
    reValidateMode: "onBlur",
    defaultValues: { email: "", password: "", confirmPassword: "" },
  });
  const loginHref = authPageHref("/login", next);
  const { submitting, submit } = useAuthSubmit({
    next,
    action: registerAccount,
    successToast: TOAST_MESSAGES.registered,
    onFailure: (failure, credentials) => {
      if (failure.reason === "email-taken") {
        serverErrors.show(
          "email",
          credentials.email,
          <>
            {FIELD_MESSAGES.emailTaken}
            <AuthLink replace drawn href={loginHref}>
              {FIELD_MESSAGES.emailTakenLink}
            </AuthLink>
          </>,
        );
        setFocus("email");
        return;
      }
      if (failure.reason === "invalid-fields") {
        const first = serverErrors.showFieldErrors(failure.fieldErrors, credentials);
        if (first) {
          setFocus(first);
          return;
        }
      }
      // 服务不可用，或字段名一条都对不上：保留全部内容，焦点不动（点了提交按钮时就留在按钮上）
      setAlert(REGISTER_UNAVAILABLE_MESSAGE);
    },
  });

  /// 字段显示错误之后，每次输入都重新校验它，改对了错误立即消失（spec 第 9.3 节）
  function revalidate(field: "email" | "password" | "confirmPassword") {
    if (getFieldState(field).error) {
      void trigger(field);
    }
  }

  const [email, password] = watch(["email", "password"]);
  const emailError = errors.email?.message ?? serverErrors.messageFor("email", email.trim());
  const passwordError = errors.password?.message ?? serverErrors.messageFor("password", password);

  /// 每次提交先清掉提示条和后端字段错误，再校验、请求。提交中再次提交由 `submit` 忽略。
  /// 确认密码只在前端比对，不传给后端。
  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAlert(null);
    serverErrors.clear();
    void handleSubmit((values) => submit({ email: values.email, password: values.password }))(
      event,
    );
  }

  return (
    <>
      {/* 副文案到表单 32px；有提示条时，副文案到提示条 24px，提示条到第一个字段 20px */}
      <form
        noValidate
        onSubmit={onSubmit}
        className={cn("flex flex-col gap-5", alert ? "mt-6" : "mt-8")}
      >
        {alert && <FormAlert>{alert}</FormAlert>}
        <FormField id="register-email" label="邮箱" error={emailError}>
          {(control) => (
            <input
              {...control}
              {...register("email", { onChange: () => revalidate("email") })}
              type="email"
              autoComplete="email"
              spellCheck={false}
              placeholder="name@example.com"
              readOnly={submitting}
              // biome-ignore lint/a11y/noAutofocus: 简报 §6 要求进入注册页时焦点在邮箱框
              autoFocus
            />
          )}
        </FormField>
        <FormField id="register-password" label="密码" hint="至少 8 位" error={passwordError}>
          {(control) => (
            <PasswordInput
              {...control}
              {...register("password", {
                onChange: () => {
                  revalidate("password");
                  // 确认密码不为空时，密码每次改动都重新比对确认密码
                  if (getValues("confirmPassword") !== "") {
                    void trigger("confirmPassword");
                  }
                },
              })}
              autoComplete="new-password"
              spellCheck={false}
              readOnly={submitting}
            />
          )}
        </FormField>
        <FormField
          id="register-confirm-password"
          label="确认密码"
          error={errors.confirmPassword?.message}
        >
          {(control) => (
            <PasswordInput
              {...control}
              {...register("confirmPassword", { onChange: () => revalidate("confirmPassword") })}
              autoComplete="new-password"
              spellCheck={false}
              readOnly={submitting}
            />
          )}
        </FormField>
        <div className="flex flex-col gap-4">
          <p className="text-fg-3 text-sm leading-5">请牢记密码，目前不支持找回密码。</p>
          <button
            type="submit"
            aria-disabled={submitting || undefined}
            title={submitting ? "正在注册" : undefined}
            className={cn(btnPrimary, btnBlock)}
          >
            {submitting ? "注册中…" : "注册"}
          </button>
        </div>
      </form>
      <p className="mt-5 text-base text-fg-2 leading-5">
        已有账号？
        <AuthLink replace href={loginHref}>
          登录
        </AuthLink>
      </p>
    </>
  );
}
