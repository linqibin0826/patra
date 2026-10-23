"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CircleAlert } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { SettingsCard } from "@/components/settings/SettingsCard";
import { SessionRow } from "@/components/settings/security/SessionRow";
import { useLogout } from "@/lib/auth/logout-mutation";
import { revokedOthersToast, SESSIONS_MESSAGES } from "@/lib/auth/messages";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { revokeOtherSessions, revokeSession } from "@/lib/identity/security-actions";
import type { SessionItem } from "@/lib/identity/session-list";
import { btnSecondary, btnText } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { useReauth } from "@/lib/reauth/use-reauth";
import { useSessionsQuery } from "@/lib/sessions-query";
import { cn } from "@/lib/utils";

const PAGE = "/settings/security";
/// 退出成功后淡出的时长，与 `--dur-normal` 一致
const LEAVE_MS = 180;
/// 「最后活跃」每分钟更新一次（后端精度就是 60 秒）
const TICK_MS = 60_000;

/// 安全页 · 登录设备（简报 §3.11 卡片 2、§4.8）：本机置顶的会话列表；踢单个 / 其他全部都经再认证，
/// 不二次确认；本机「退出登录」走普通登出。加载中三行骨架，失败可重试，会话已不在按登出处理。
export function SessionsCard() {
  const queryClient = useQueryClient();
  const leave = useCommonFailureRedirect(PAGE);
  const { withReauth } = useReauth();
  const logout = useLogout();
  const { data, error, refetch } = useSessionsQuery();
  const [now, setNow] = useState(() => Date.now());
  const [busyId, setBusyId] = useState<string | null>(null);
  const [leavingIds, setLeavingIds] = useState<ReadonlySet<string>>(new Set());
  const [revokingAll, setRevokingAll] = useState(false);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (error?.signedOut) leave("signed-out");
  }, [error, leave]);

  const others = data?.filter((session) => !session.current) ?? [];

  /// 从缓存里去掉满足条件的行
  function drop(predicate: (session: SessionItem) => boolean) {
    queryClient.setQueryData<SessionItem[]>(queryKeys.sessions, (previous) =>
      previous?.filter((session) => !predicate(session)),
    );
  }

  /// 踢掉一台设备：成功后淡出再移除
  async function revokeOne(id: string) {
    if (busyId || revokingAll) return;
    setBusyId(id);
    const result = await withReauth(() => revokeSession(id)).catch(
      () => ({ ok: false, reason: "unavailable" }) as const,
    );
    setBusyId(null);
    if (result.ok) {
      toast.success(SESSIONS_MESSAGES.revoked);
      setLeavingIds((previous) => new Set(previous).add(id));
      setTimeout(() => drop((session) => session.id === id), LEAVE_MS);
      return;
    }
    if (result.reason === "cancelled") return;
    if (result.reason === "signed-out" || result.reason === "pending-deletion") {
      leave(result.reason);
      return;
    }
    toast.error(SESSIONS_MESSAGES.revokeFailed);
  }

  /// 踢掉其他全部：只留本机，再重新拉一次
  async function revokeAll() {
    if (revokingAll || busyId || others.length === 0) return;
    setRevokingAll(true);
    const result = await withReauth(() => revokeOtherSessions()).catch(
      () => ({ ok: false, reason: "unavailable" }) as const,
    );
    setRevokingAll(false);
    if (result.ok) {
      toast.success(revokedOthersToast(result.revokedSessions));
      drop((session) => !session.current);
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions });
      return;
    }
    if (result.reason === "cancelled") return;
    if (result.reason === "signed-out" || result.reason === "pending-deletion") {
      leave(result.reason);
      return;
    }
    toast.error(SESSIONS_MESSAGES.revokeFailed);
  }

  return (
    <SettingsCard
      title="登录设备"
      description={SESSIONS_MESSAGES.description}
      asidePlacement="block"
      aside={
        <button
          type="button"
          onClick={revokeAll}
          aria-disabled={revokingAll || others.length === 0 || undefined}
          title={data && others.length === 0 ? SESSIONS_MESSAGES.noOthersTitle : undefined}
          className={cn(btnSecondary, "shrink-0 px-3.5 py-1.5 text-sm")}
        >
          {revokingAll ? SESSIONS_MESSAGES.revokingAll : SESSIONS_MESSAGES.revokeAll}
        </button>
      }
    >
      {data ? (
        <div>
          <ul aria-label="登录设备" className="-my-4">
            {data.map((session) => (
              <SessionRow
                key={session.id}
                session={session}
                now={now}
                busy={busyId === session.id}
                leaving={leavingIds.has(session.id)}
                onAction={() => (session.current ? logout.mutate() : void revokeOne(session.id))}
              />
            ))}
          </ul>
          {others.length === 0 && (
            <p className="mt-6 text-sm leading-5 text-fg-3">{SESSIONS_MESSAGES.noOthers}</p>
          )}
        </div>
      ) : error && !error.signedOut ? (
        <div className="flex items-center gap-2.5 rounded-lg bg-status-danger-bg px-3.5 py-3 text-base leading-5 text-fg-1">
          <CircleAlert className="size-4 shrink-0 text-status-danger" aria-hidden="true" />
          <span className="flex-1">{SESSIONS_MESSAGES.failed}</span>
          <button type="button" onClick={() => void refetch()} className={btnText}>
            {SESSIONS_MESSAGES.retry}
          </button>
        </div>
      ) : (
        <div
          role="status"
          aria-label={SESSIONS_MESSAGES.loading}
          className="flex flex-col gap-4.5 py-4"
        >
          {["w-2/5", "w-1/3", "w-[44%]"].map((width) => (
            <div key={width} className="flex gap-3.5">
              <span className="size-10 shrink-0 rounded-md bg-paper-200 shimmer" />
              <div className="flex flex-1 flex-col gap-2">
                <span className={cn("h-4 rounded-md bg-paper-200 shimmer", width)} />
                <span className="h-3.5 w-3/5 rounded-md bg-paper-200 opacity-70 shimmer" />
              </div>
            </div>
          ))}
        </div>
      )}
    </SettingsCard>
  );
}
