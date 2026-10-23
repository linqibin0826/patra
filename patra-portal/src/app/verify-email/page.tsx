import type { Metadata } from "next";
import { VerifyEmailLanding } from "@/components/landing/VerifyEmailLanding";

export const metadata: Metadata = { title: "验证邮箱 · Patra", robots: { index: false } };

/// 验证邮箱落地页（简报 §3.18 a）：路径是 identity 配置 `patra.identity.mail.links` 的默认值，改路径要同步改配置
export default function VerifyEmailPage() {
  return <VerifyEmailLanding />;
}
