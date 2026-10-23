"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { type FormEvent, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { AuthLink } from "@/components/auth/AuthLink";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { useAuthSubmit, useServerFieldErrors } from "@/components/auth/use-auth-submit";
import { useBlurValidation } from "@/components/auth/use-blur-validation";
import { login } from "@/lib/auth/actions";
import { loginSchema } from "@/lib/auth/form-schemas";
import { LOGIN_NOTICES, loginAlertMessage, TOAST_MESSAGES } from "@/lib/auth/messages";
import { authPageHref, forgotPasswordHref } from "@/lib/auth/next-path";
import { btnBlock, btnPrimary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";
import { useAuthHandoffStore } from "@/store/auth-handoff";

/// 登录表单（spec 第 9 节）：提示条、邮箱、密码（标签行右端「忘记密码？」）、提交按钮、去注册的链接。
/// `next` 已经过 `safeNextPath`。从重设密码页过来时（PAP-102 spec §8.1）预填邮箱、表单上方是成功提示、
/// 焦点在密码框。
export function LoginForm({ next }: { next: string }) {
  // 挂载时读一次带过来的内容，读完在 effect 里清空（严格模式下初始化函数跑两次也读得到）
  const [handoff] = useState(() => {
    const { email, notice } = useAuthHandoffStore.getState();
    return { email, notice };
  });
  const takeHandoff = useAuthHandoffStore((state) => state.take);
  const putHandoff = useAuthHandoffStore((state) => state.put);
  useEffect(() => {
    takeHandoff();
  }, [takeHandoff]);
  const afterReset = handoff.notice === "password-reset";
  const [alert, setAlert] = useState<string | null>(null);
  const serverErrors = useServerFieldErrors();
  const {
    register,
    handleSubmit,
    formState: { errors },
    getFieldState,
    getValues,
    trigger,
    setValue,
    setFocus,
    watch,
  } = useForm({
    resolver: zodResolver(loginSchema),
    // RHF 只在提交时校验；离开字段、输入时的校验由下面的 onBlur、onChange 触发。密码只查非空，
    // 空着离开又不校验，离开密码框时没有可查的，所以密码框不接 onBlur
    mode: "onSubmit",
    reValidateMode: "onSubmit",
    defaultValues: { email: handoff.email ?? "", password: "" },
  });
  const validateOnBlur = useBlurValidation(trigger);
  const { submitting, submit } = useAuthSubmit({
    next,
    action: login,
    successToast: TOAST_MESSAGES.loggedIn,
    onFailure: (failure, credentials) => {
      if (failure.reason === "invalid-fields") {
        const first = serverErrors.showFieldErrors(failure.fieldErrors, credentials);
        if (first) {
          setFocus(first);
          return;
        }
      }
      // 字段名一条都对不上时，按服务不可用处理
      setAlert(
        loginAlertMessage(
          failure.reason === "invalid-fields" ? { ok: false, reason: "unavailable" } : failure,
        ),
      );
      setValue("password", "");
      setFocus("password");
    },
  });

  /// 字段显示错误之后，每次输入都重新校验它，改对了错误立即消失（spec 第 9.3 节）
  const revalidateOnChange = (field: "email" | "password") => ({
    onChange: () => {
      if (getFieldState(field).error) {
        void trigger(field);
      }
    },
  });

  const [email, password] = watch(["email", "password"]);
  const emailError = errors.email?.message ?? serverErrors.messageFor("email", email.trim());
  const passwordError = errors.password?.message ?? serverErrors.messageFor("password", password);

  /// 每次提交先清掉提示条和后端字段错误，再校验、请求。提交中再次提交由 `submit` 忽略
  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAlert(null);
    serverErrors.clear();
    void handleSubmit((values) => submit(values))(event);
  }

  return (
    <>
      {/* 副文案到表单 32px；有提示条时，副文案到提示条 24px，提示条到第一个字段 20px */}
      <form
        // 页面还没接管表单就提交时（脚本没加载完），浏览器按这里的 method 提交：用 POST，邮箱、密码不进地址栏
        method="post"
        noValidate
        onSubmit={onSubmit}
        className={cn("flex flex-col gap-5", alert ? "mt-6" : "mt-8")}
      >
        {alert ? (
          <FormAlert>{alert}</FormAlert>
        ) : (
          afterReset && <FormAlert tone="success">{LOGIN_NOTICES.passwordReset}</FormAlert>
        )}
        <FormField id="login-email" label="邮箱" error={emailError}>
          {(control) => (
            <input
              {...control}
              {...register("email", {
                onBlur: validateOnBlur("email"),
                ...revalidateOnChange("email"),
              })}
              type="email"
              autoComplete="username"
              spellCheck={false}
              placeholder="name@example.com"
              readOnly={submitting}
              // biome-ignore lint/a11y/noAutofocus: 简报 §6 要求进入登录页时焦点在邮箱框
              autoFocus={!afterReset}
            />
          )}
        </FormField>
        <FormField
          id="login-password"
          label="密码"
          error={passwordError}
          aside={
            <span className="text-sm">
              <AuthLink
                href={forgotPasswordHref(next)}
                onClick={() =>
                  putHandoff({ email: getValues("email").trim() || null, notice: null })
                }
              >
                忘记密码？
              </AuthLink>
            </span>
          }
        >
          {(control) => (
            <PasswordInput
              {...control}
              {...register("password", revalidateOnChange("password"))}
              autoComplete="current-password"
              spellCheck={false}
              readOnly={submitting}
              autoFocus={afterReset}
            />
          )}
        </FormField>
        <div className="mt-2">
          <button
            type="submit"
            aria-disabled={submitting || undefined}
            title={submitting ? "正在登录" : undefined}
            className={cn(btnPrimary, btnBlock)}
          >
            {submitting ? "登录中…" : "登录"}
          </button>
        </div>
      </form>
      <p className="mt-5 text-base text-fg-2 leading-5">
        还没有账号？
        <AuthLink replace href={authPageHref("/register", next)}>
          注册
        </AuthLink>
      </p>
    </>
  );
}
