import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import type { CurrentUser } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

/// 每次调用都新建响应：同一个 Response 的响应体只能读一次
const respondWith = (status: number, body?: unknown) =>
  vi.fn(async () => new Response(body === undefined ? null : JSON.stringify(body), { status }));

const noRetryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

function renderCurrentUser(client: QueryClient = noRetryClient()) {
  return renderHook(() => useCurrentUser(), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  });
}

afterEach(() => vi.unstubAllGlobals());

describe("useCurrentUser", () => {
  it("请求同源的 /api/auth/me，拿到用户之前是 pending", async () => {
    const fetchMock = respondWith(200, { user: USER });
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderCurrentUser();
    expect(result.current).toEqual({ status: "pending" });
    await waitFor(() => expect(result.current).toEqual({ status: "authenticated", user: USER }));
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/me");
  });

  it("没有登录：anonymous", async () => {
    vi.stubGlobal("fetch", respondWith(200, { user: null }));
    const { result } = renderCurrentUser();
    await waitFor(() => expect(result.current).toEqual({ status: "anonymous" }));
  });

  it("门户回 503：anonymous", async () => {
    vi.stubGlobal("fetch", respondWith(503));
    const { result } = renderCurrentUser();
    await waitFor(() => expect(result.current).toEqual({ status: "anonymous" }));
  });

  it("回了不是 JSON 的 200（比如前面的代理换成了错误页）：anonymous", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("<html></html>", { status: 200 })),
    );
    const { result } = renderCurrentUser();
    await waitFor(() => expect(result.current).toEqual({ status: "anonymous" }));
  });

  it("第一次失败、重试还没回来时仍是 pending，重试成功后是 authenticated", async () => {
    let answer: (res: Response) => void = () => {};
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 503 }))
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            answer = resolve;
          }),
      );
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderCurrentUser(
      new QueryClient({ defaultOptions: { queries: { retry: 1, retryDelay: 0 } } }),
    );
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(result.current).toEqual({ status: "pending" });
    answer(Response.json({ user: USER }));
    await waitFor(() => expect(result.current).toEqual({ status: "authenticated", user: USER }));
  });

  it.each([
    ["用户", USER, { status: "authenticated", user: USER }],
    ["null", null, { status: "anonymous" }],
  ])("页面写进 queryKeys.currentUser 的%s直接生效", (_label, cached, expected) => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise<Response>(() => {})),
    );
    const client = noRetryClient();
    client.setQueryData(queryKeys.currentUser, cached);
    const { result } = renderCurrentUser(client);
    expect(result.current).toEqual(expected);
  });
});
