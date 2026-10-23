import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { SESSIONS } from "@tests/fixtures/sessions";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useSessionsQuery } from "@/lib/sessions-query";

afterEach(() => vi.unstubAllGlobals());

function renderSessions() {
  const client = new QueryClient();
  return renderHook(() => useSessionsQuery(), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  });
}

describe("useSessionsQuery", () => {
  it("请求 /api/me/sessions，返回列表", async () => {
    const fetchMock = vi.fn(async () => Response.json({ sessions: SESSIONS }));
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderSessions();
    await waitFor(() => expect(result.current.data).toEqual(SESSIONS));
    expect(fetchMock).toHaveBeenCalledWith("/api/me/sessions");
  });

  it("401：错误带 signedOut，不重试", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({ ok: false, reason: "signed-out" }, { status: 401 }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderSessions();
    await waitFor(() => expect(result.current.error?.signedOut).toBe(true));
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("502：错误不带 signedOut", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ ok: false, reason: "unavailable" }, { status: 502 })),
    );
    const { result } = renderSessions();
    await waitFor(() => expect(result.current.error?.signedOut).toBe(false), { timeout: 5_000 });
  });
});
