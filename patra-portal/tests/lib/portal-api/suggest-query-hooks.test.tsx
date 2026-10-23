import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  useMeshSuggestQuery,
  useOrganizationSuggestQuery,
} from "@/lib/portal-api/suggest-query-hooks";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

afterEach(() => vi.unstubAllGlobals());

describe("建议查询", () => {
  it("机构：按 q 请求 /api/organizations/suggest，取 items", async () => {
    const items = [{ rorId: "04jztag35" }];
    const fetchMock = vi.fn(async () => Response.json({ items }));
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderHook(() => useOrganizationSuggestQuery("北京 协和"), { wrapper });
    await waitFor(() => expect(result.current.data).toEqual(items));
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/organizations/suggest?q=%E5%8C%97%E4%BA%AC%20%E5%8D%8F%E5%92%8C",
    );
  });

  it("MeSH：按 q 请求 /api/mesh/suggest", async () => {
    const fetchMock = vi.fn(async () => Response.json({ items: [] }));
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderHook(() => useMeshSuggestQuery("heart"), { wrapper });
    await waitFor(() => expect(result.current.data).toEqual([]));
    expect(fetchMock).toHaveBeenCalledWith("/api/mesh/suggest?q=heart");
  });

  it("q 为空串时不请求", () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderHook(() => useMeshSuggestQuery(""), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("非 2xx：出错", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ items: [] }, { status: 502 })),
    );
    const { result } = renderHook(() => useOrganizationSuggestQuery("协和"), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
