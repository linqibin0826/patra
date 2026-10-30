import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { NotificationSettingsForm } from "@/components/settings/notifications/NotificationSettingsForm";
import { NotificationSettingsUnavailable } from "@/components/settings/notifications/NotificationSettingsUnavailable";
import { UnverifiedEmailNotice } from "@/components/settings/notifications/UnverifiedEmailNotice";
import { authPageHref } from "@/lib/auth/next-path";
import { requireUser } from "@/lib/auth/require-user";
import { fetchMyNotificationPreferences } from "@/lib/notification/preferences";

export const metadata: Metadata = { title: "通知 · 设置 · Patra" };

const PAGE = "/settings/notifications";

/// 设置 · 通知（简报 §3.5，PAP-148 spec 第 8 节）：两类提醒邮件各三档，改动即时保存。通知服务不可用时原地出
/// 「暂时打不开」的卡片，不进错误页：其余分区照常能用。
export default async function NotificationSettingsPage() {
  const user = await requireUser(PAGE);
  const preferences = await fetchMyNotificationPreferences();
  if (preferences === "signed-out") {
    redirect(authPageHref("/login", PAGE));
  }
  return (
    <div className="flex flex-col gap-8 max-[880px]:gap-6">
      {!user.emailVerified && <UnverifiedEmailNotice />}
      {preferences === "unavailable" ? (
        <NotificationSettingsUnavailable />
      ) : (
        <NotificationSettingsForm initial={preferences} email={user.email} />
      )}
    </div>
  );
}
