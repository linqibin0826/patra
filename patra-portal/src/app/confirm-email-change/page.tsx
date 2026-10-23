import type { Metadata } from "next";
import { ConfirmEmailChangeLanding } from "@/components/landing/ConfirmEmailChangeLanding";

export const metadata: Metadata = { title: "确认新邮箱 · Patra", robots: { index: false } };

/// 确认新邮箱落地页（简报 §3.18 c）：路径是 identity 配置 `patra.identity.mail.links` 的默认值，改路径要同步改配置
export default function ConfirmEmailChangePage() {
  return <ConfirmEmailChangeLanding />;
}
