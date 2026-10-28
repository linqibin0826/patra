import { NotificationInbox } from "@/components/notifications/NotificationInbox";
import { requireUser } from "@/lib/auth/require-user";

/// 提醒 · @ 我的（简报 §3.19）
export default async function MentionsPage() {
  await requireUser("/notifications/mentions");
  return <NotificationInbox tab="mentions" />;
}
