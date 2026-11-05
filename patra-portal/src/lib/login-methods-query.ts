import { type UseQueryResult, useQuery } from "@tanstack/react-query";
import type { LoginMethods } from "@/lib/identity/login-methods";
import { queryKeys } from "@/lib/query-keys";

/// 登录方式加载失败；`signedOut` 表示当前会话已不在，按登出处理
export class LoginMethodsError extends Error {
  readonly signedOut: boolean;

  constructor(status: number) {
    super(`登录方式加载失败：${status}`);
    this.signedOut = status === 401;
  }
}

/// 请求门户自己的 `/api/me/login-methods`
async function fetchLoginMethods(): Promise<LoginMethods> {
  const res = await fetch("/api/me/login-methods");
  if (!res.ok) {
    throw new LoginMethodsError(res.status);
  }
  return (await res.json()) as LoginMethods;
}

/// 登录方式（安全页）：会话已不在时不重试
export function useLoginMethodsQuery(): UseQueryResult<LoginMethods, LoginMethodsError> {
  return useQuery({
    queryKey: queryKeys.loginMethods,
    queryFn: fetchLoginMethods,
    retry: (failureCount, error) => !error.signedOut && failureCount < 1,
  });
}
