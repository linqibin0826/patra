import { type UseQueryResult, useQuery } from "@tanstack/react-query";
import type { SessionItem } from "@/lib/identity/session-list";
import { queryKeys } from "@/lib/query-keys";

/// 设备列表加载失败；`signedOut` 表示当前会话已不在（被别处踢掉或过期），按登出处理
export class SessionsError extends Error {
  readonly signedOut: boolean;

  constructor(status: number) {
    super(`登录设备加载失败：${status}`);
    this.signedOut = status === 401;
  }
}

/// 请求门户自己的 `/api/me/sessions`
async function fetchSessions(): Promise<SessionItem[]> {
  const res = await fetch("/api/me/sessions");
  if (!res.ok) {
    throw new SessionsError(res.status);
  }
  return ((await res.json()) as { sessions: SessionItem[] }).sessions;
}

/// 登录设备列表（安全页，spec §7.2）：切回窗口时重新拉一次（全局默认关着），另一台设备刚登录就能看到；
/// 会话已不在时不重试。
export function useSessionsQuery(): UseQueryResult<SessionItem[], SessionsError> {
  return useQuery({
    queryKey: queryKeys.sessions,
    queryFn: fetchSessions,
    staleTime: 0,
    refetchOnWindowFocus: true,
    retry: (failureCount, error) => !error.signedOut && failureCount < 1,
  });
}
