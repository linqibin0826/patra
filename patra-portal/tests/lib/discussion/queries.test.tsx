import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { makeThread } from "@tests/fixtures/discussion";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useSingleThread, useThreads } from "@/lib/discussion/queries";

const fetchMock = vi.fn<typeof fetch>();
const PAPER = { type: "publication", key: "1" } as const;

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useThreads", () => {
  it("按排序取第一页，下一页带游标", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json({ items: [makeThread()], nextCursor: "c2" }))
      .mockResolvedValueOnce(Response.json({ items: [], nextCursor: null }));
    const { result } = renderHook(() => useThreads(PAPER, "asc"), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      "/api/discussions/publication/1/threads?sort=asc",
    );
    await result.current.fetchNextPage();
    expect(String(fetchMock.mock.calls[1]?.[0])).toBe(
      "/api/discussions/publication/1/threads?sort=asc&cursor=c2",
    );
  });

  it("失败进入错误态", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    const { result } = renderHook(() => useThreads(PAPER, "latest"), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useSingleThread", () => {
  it("404 → missing，不是错误", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "not-found" }, { status: 404 }));
    const { result } = renderHook(() => useSingleThread(PAPER, "5"), { wrapper });
    await waitFor(() => expect(result.current.data).toEqual({ missing: true }));
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      "/api/discussions/publication/1/comments/5/thread",
    );
  });

  it("commentId 为 null 时不取", () => {
    renderHook(() => useSingleThread(PAPER, null), { wrapper });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("换排序时留着上一份，身份变化清缓存后不留", () => {
  it("缓存被清掉（换了访客）后不拿上一位的列表当占位", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const own = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    fetchMock.mockResolvedValueOnce(Response.json({ items: [makeThread()], nextCursor: null }));
    const { result, rerender } = renderHook(({ sort }) => useThreads(PAPER, sort), {
      wrapper: own,
      initialProps: { sort: "asc" as "asc" | "latest" },
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    fetchMock.mockReturnValue(new Promise(() => {}));
    rerender({ sort: "latest" });
    expect(result.current.data?.pages).toHaveLength(1);
    client.removeQueries({ queryKey: ["discussion"] });
    rerender({ sort: "asc" });
    await waitFor(() => expect(result.current.data).toBeUndefined());
  });
});
