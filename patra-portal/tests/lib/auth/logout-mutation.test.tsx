import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { logout } from "@/lib/auth/actions";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { useLogout } from "@/lib/auth/logout-mutation";
import { queryKeys } from "@/lib/query-keys";

vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

const USER = { userId: "319041872872550658", email: "reader@example.com" };

/// 一个还没回来的请求，`answer` 让它在测试想要的时候回来
function deferred<T>() {
  let answer: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

/// 导航栏的当前用户和退出登录放在同一个组件里，和 NavAccount 一样
function renderAccount(client: QueryClient) {
  return renderHook(() => ({ user: useCurrentUser(), logout: useLogout() }), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  });
}

let client: QueryClient;

beforeEach(() => {
  // 和 createQueryClient 一样，数据 60 秒内不过期：挂载时不会因为数据过期而重新请求
  client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 60_000 } } });
  client.setQueryData(queryKeys.currentUser, USER);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("useLogout", () => {
  it("调用 logout() 时不带参数：Server Action 的参数会被序列化发给服务端", async () => {
    vi.mocked(logout).mockResolvedValue(undefined);
    const { result } = renderAccount(client);
    act(() => result.current.logout.mutate());
    await waitFor(() => expect(result.current.logout.isSuccess).toBe(true));
    expect(vi.mocked(logout).mock.calls).toEqual([[]]);
  });

  it("进行中是 pending；成功后当前用户写成 null，提示「已退出登录」", async () => {
    const pending = deferred<void>();
    vi.mocked(logout).mockReturnValue(pending.promise);
    const { result } = renderAccount(client);
    act(() => result.current.logout.mutate());
    await waitFor(() => expect(result.current.logout.isPending).toBe(true));
    pending.answer();
    await waitFor(() => expect(result.current.user).toEqual({ status: "anonymous" }));
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
    expect(toast.success).toHaveBeenCalledWith("已退出登录");
  });

  it("成功前发出、成功后才回来的 /api/auth/me 不会把 null 覆盖掉", async () => {
    const late = { ok: true, json: vi.fn(async () => ({ user: USER })) };
    const me = deferred<typeof late>();
    vi.stubGlobal(
      "fetch",
      vi.fn(() => me.promise),
    );
    vi.mocked(logout).mockResolvedValue(undefined);
    const { result } = renderAccount(client);
    // 当前用户正在重新请求（比如数据过期了），请求还没回来
    act(() => {
      void client.invalidateQueries({ queryKey: queryKeys.currentUser });
    });
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    act(() => result.current.logout.mutate());
    await waitFor(() => expect(result.current.logout.isSuccess).toBe(true));
    // 请求这时才回来，响应体也读完了
    me.answer(late);
    await waitFor(() => expect(late.json).toHaveBeenCalled());
    await act(async () => {});
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
    expect(result.current.user).toEqual({ status: "anonymous" });
  });

  it("promise 被拒绝：不写 null，重新请求当前用户，不提示", async () => {
    const fetchMock = vi.fn(async () => Response.json({ user: USER }));
    vi.stubGlobal("fetch", fetchMock);
    vi.mocked(logout).mockRejectedValue(new Error("network"));
    const { result } = renderAccount(client);
    act(() => result.current.logout.mutate());
    await waitFor(() => expect(result.current.logout.isError).toBe(true));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/auth/me"));
    expect(result.current.user).toEqual({ status: "authenticated", user: USER });
    expect(toast.success).not.toHaveBeenCalled();
  });
});
