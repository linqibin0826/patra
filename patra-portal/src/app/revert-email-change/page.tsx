import type { Metadata } from "next";
import { RevertEmailChangeLanding } from "@/components/landing/RevertEmailChangeLanding";

export const metadata: Metadata = { title: "撤销邮箱更改 · Patra", robots: { index: false } };

/// 撤销改邮箱落地页（简报 §3.18 d）：路径是 identity 配置 `patra.identity.mail.links` 的默认值，改路径要同步改配置
export default function RevertEmailChangePage() {
  return <RevertEmailChangeLanding />;
}
