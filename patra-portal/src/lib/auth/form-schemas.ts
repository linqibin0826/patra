import { z } from "zod";
import { FIELD_MESSAGES } from "@/lib/auth/messages";

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

/// 登录表单：后端的登录接口只检查密码非空，前端也只查这一条，不去空格
export const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, FIELD_MESSAGES.passwordRequired),
});

/// 注册表单：密码 8 ~ 64 位，不去空格；确认密码只在前端比对，不传给后端
export const registerSchema = z
  .object({
    email: emailField,
    password: z
      .string()
      .min(1, FIELD_MESSAGES.passwordRequired)
      .refine((value) => codePoints(value) >= 8, FIELD_MESSAGES.passwordTooShort)
      .refine((value) => codePoints(value) <= 64, FIELD_MESSAGES.passwordTooLong),
    confirmPassword: z.string().min(1, FIELD_MESSAGES.confirmRequired),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ["confirmPassword"],
    error: FIELD_MESSAGES.confirmMismatch,
  });

/// 表单里各字段的原始值
export type LoginFormValues = z.input<typeof loginSchema>;
export type RegisterFormValues = z.input<typeof registerSchema>;
