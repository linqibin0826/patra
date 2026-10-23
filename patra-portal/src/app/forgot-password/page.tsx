import type { Metadata } from "next";
import { ForgotPasswordForm } from "@/components/auth/ForgotPasswordForm";
import { safeNextPath } from "@/lib/auth/next-path";

export const metadata: Metadata = { title: "找回密码 · Patra", robots: { index: false } };

/// 忘记密码页（简报 §3.17）。已登录也能打开（从安全页、再认证对话框过来），提交后不影响当前登录。
/// `next` 出现多次时取第一个，「返回」「返回登录」回到带同一个 `next` 的登录页。
export default async function ForgotPasswordPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const raw = (await searchParams).next;
  return <ForgotPasswordForm next={safeNextPath(Array.isArray(raw) ? raw[0] : raw)} />;
}
