import { NotificationInbox } from "@/components/notifications/NotificationInbox";
import { requireUser } from "@/lib/auth/require-user";

/// 提醒 · 有帮助（v0.16 简报 §3.1）
export default async function HelpfulPage() {
  await requireUser("/notifications/helpful");
  return <NotificationInbox tab="helpful" />;
}
