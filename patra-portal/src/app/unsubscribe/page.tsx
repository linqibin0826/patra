import type { Metadata } from "next";
import { UnsubscribeLanding } from "@/components/landing/UnsubscribeLanding";

export const metadata: Metadata = { title: "退订提醒邮件 · Patra", robots: { index: false } };

/// 退订确认页（简报 §3.6）：邮件底部「退订这类邮件」的链接 `/unsubscribe#token=…&category=…`，以及一键退订地址
/// 被浏览器打开时的跳转目标。路径是写进已发出邮件的长期契约（PAP-142 第 7 节），改路径要同步改通知服务
export default function UnsubscribePage() {
  return <UnsubscribeLanding />;
}
