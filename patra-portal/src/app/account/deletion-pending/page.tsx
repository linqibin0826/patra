import { CalendarClock, TriangleAlert } from "lucide-react";
import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { DeletionPendingPanel } from "@/components/account/DeletionPendingPanel";
import { AuthShell } from "@/components/auth/AuthShell";
import { StateIcon } from "@/components/auth/StateIcon";
import { lookupSession } from "@/lib/auth/current-user";
import { DELETION_PENDING_MESSAGES } from "@/lib/auth/messages";
import { authPageHref, safeNextPath } from "@/lib/auth/next-path";
import { formatDateMinute } from "@/lib/format-time";

export const metadata: Metadata = { title: "取消注销 · Patra", robots: { index: false } };

/// 取消注销页（简报 §3.16、spec §8.5）。只给冷静期账号看：未登录去登录页（登录后冷静期账号会再回到这里），
/// 正常账号直接回 `next`，网关不可用交给错误页。没有「返回」：唯一的出口是两个按钮。
export default async function DeletionPendingPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const raw = (await searchParams).next;
  const next = safeNextPath(Array.isArray(raw) ? raw[0] : raw);
  const session = await lookupSession();
  if (session.status === "unavailable") {
    throw new Error("登录状态暂时不可用");
  }
  if (session.status !== "authenticated") {
    redirect(authPageHref("/login", next));
  }
  const { user } = session;
  if (user.status !== "PENDING_DELETION" || user.deletionDueAt === null) {
    redirect(next);
  }
  return (
    <AuthShell
      title={DELETION_PENDING_MESSAGES.title}
      description={`账号将于 ${formatDateMinute(user.deletionDueAt)} 永久删除。`}
      back={null}
      icon={<StateIcon icon={CalendarClock} tone="warning" />}
    >
      <div className="mt-5 flex items-start gap-2.5 rounded-lg bg-status-warning-bg px-3.5 py-3 text-base leading-5 text-fg-1">
        <TriangleAlert
          className="mt-0.5 size-4 shrink-0 text-status-warning-ink"
          aria-hidden="true"
        />
        <span className="min-w-0">{DELETION_PENDING_MESSAGES.warning}</span>
      </div>
      <DeletionPendingPanel next={next} />
    </AuthShell>
  );
}
