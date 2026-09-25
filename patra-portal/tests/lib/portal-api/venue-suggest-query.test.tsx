import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useSuggestedVenueNames, useVenueSuggestQuery } from "@/lib/portal-api/venue-suggest-query";
import { queryKeys } from "@/lib/query-keys";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

afterEach(() => vi.unstubAllGlobals());

describe("useVenueSuggestQuery", () => {
  it("按 q 请求 /api/venues/suggest 并返回候选", async () => {
    const venues = [{ id: "1", name: "The Lancet", abbr: "Lancet" }];
    const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(venues))));
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderHook(() => useVenueSuggestQuery("lan cet"), { wrapper });
    await waitFor(() => expect(result.current.data).toEqual(venues));
    expect(fetchMock).toHaveBeenCalledWith("/api/venues/suggest?q=lan%20cet");
  });

  it("q 为空时不发请求", () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderHook(() => useVenueSuggestQuery(""), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("useSuggestedVenueNames：汇总已缓存的全部候选为 id → 刊名", () => {
    const client = new QueryClient();
    client.setQueryData(queryKeys.venueSuggest("lan"), [
      { id: "1", name: "The Lancet", abbr: "Lancet" },
    ]);
    client.setQueryData(queryKeys.venueSuggest("cell"), [{ id: "2", name: "Cell", abbr: "Cell" }]);
    const { result } = renderHook(() => useSuggestedVenueNames(), {
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    });
    expect(result.current).toEqual({ "1": "The Lancet", "2": "Cell" });
  });

  it("非 2xx 时进入错误态", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(new Response("[]", { status: 502 }))),
    );
    const { result } = renderHook(() => useVenueSuggestQuery("lan"), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
