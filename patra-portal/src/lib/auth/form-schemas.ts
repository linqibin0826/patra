import { z } from "zod";
import { FIELD_MESSAGES, usernameMessage } from "@/lib/auth/messages";
import { normalizeUsername, usernameIssue } from "@/lib/identity/username-rules";

/// 密码长度按码点计，汉字、表情各算 1 位，和后端一致
const codePoints = (value: string): number => [...value].length;

/// 邮箱：去掉首尾空白后为空是「请输入邮箱」；超过 254 个字符或不符合 Zod 默认的 `z.email()`（后端的
/// 正则照抄的就是它）是「邮箱格式不正确」。解析结果是去掉空白之后的值，提交的也是它。
const emailField = z
  .string()
  .trim()
  .min(1, FIELD_MESSAGES.emailRequired)
  .max(254, FIELD_MESSAGES.emailInvalid)
  .pipe(z.email(FIELD_MESSAGES.emailInvalid));

/// 新密码：8 ~ 64 位，不去空格（注册、改密码、重设密码共用；常见密码名单只在后端判）
const newPasswordField = z
  .string()
  .min(1, FIELD_MESSAGES.passwordRequired)
  .refine((value) => codePoints(value) >= 8, FIELD_MESSAGES.passwordTooShort)
  .refine((value) => codePoints(value) <= 64, FIELD_MESSAGES.passwordTooLong);

/// 登录表单：后端的登录接口只检查密码非空，前端也只查这一条，不去空格
export const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, FIELD_MESSAGES.passwordRequired),
});

/// 注册表单：用户名见下；密码 8 ~ 64 位，不去空格；确认密码只在前端比对，不传给后端
export const registerSchema = z
  .object({
    email: emailField,
    /// 用户名：规则与后端同一条（`usernameIssue`），提交的是去空白、转小写之后的值
    username: z
      .string()
      .superRefine((value, ctx) => {
        const issue = usernameIssue(value);
        if (issue) {
          ctx.addIssue({ code: "custom", message: usernameMessage(issue) });
        }
      })
      .transform(normalizeUsername),
    password: newPasswordField,
    confirmPassword: z.string().min(1, FIELD_MESSAGES.confirmRequired),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ["confirmPassword"],
    error: FIELD_MESSAGES.confirmMismatch,
  });

/// 表单里各字段的原始值
export type LoginFormValues = z.input<typeof loginSchema>;
export type RegisterFormValues = z.input<typeof registerSchema>;

/// 账号页改邮箱：同注册页的邮箱规则，另外不能与当前邮箱相同（忽略大小写，后端的 `UNCHANGED` 同一句）
export function emailChangeSchema(currentEmail: string) {
  return z.object({
    newEmail: emailField.refine(
      (value) => value.toLowerCase() !== currentEmail.trim().toLowerCase(),
      FIELD_MESSAGES.emailUnchanged,
    ),
  });
}

/// 安全页改密码：当前密码只查非空；新密码同注册页；确认新密码只在前端比对
export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, FIELD_MESSAGES.currentPasswordRequired),
    newPassword: newPasswordField,
    confirmPassword: z.string().min(1, FIELD_MESSAGES.confirmNewRequired),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ["confirmPassword"],
    error: FIELD_MESSAGES.confirmMismatch,
  });

export type ChangePasswordFormValues = z.input<typeof changePasswordSchema>;

/// 忘记密码：只有邮箱，规则同注册页
export const forgotPasswordSchema = z.object({ email: emailField });

export type ForgotPasswordFormValues = z.input<typeof forgotPasswordSchema>;

/// 重设密码落地页：新密码同注册页，确认新密码只在前端比对
export const resetPasswordSchema = z
  .object({
    newPassword: newPasswordField,
    confirmPassword: z.string().min(1, FIELD_MESSAGES.confirmNewRequired),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ["confirmPassword"],
    error: FIELD_MESSAGES.confirmMismatch,
  });

export type ResetPasswordFormValues = z.input<typeof resetPasswordSchema>;
