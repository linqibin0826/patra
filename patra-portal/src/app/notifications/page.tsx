import { NotificationInbox } from "@/components/notifications/NotificationInbox";
import { requireUser } from "@/lib/auth/require-user";

/// 提醒 · 回复我的（简报 §3.19）
export default async function RepliesPage() {
  await requireUser("/notifications");
  return <NotificationInbox tab="replies" />;
}
