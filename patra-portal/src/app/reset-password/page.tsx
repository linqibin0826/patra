import type { Metadata } from "next";
import { ResetPasswordLanding } from "@/components/landing/ResetPasswordLanding";

export const metadata: Metadata = { title: "设置新密码 · Patra", robots: { index: false } };

/// 重设密码落地页（简报 §3.18 b）：路径是 identity 配置 `patra.identity.mail.links` 的默认值，改路径要同步改配置
export default function ResetPasswordPage() {
  return <ResetPasswordLanding />;
}
