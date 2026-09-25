import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/portal-api/venues", () => ({ fetchVenueSuggestions: vi.fn() }));

import { GET } from "@/app/api/venues/suggest/route";
import { fetchVenueSuggestions } from "@/lib/portal-api/venues";

function request(qs: string): Request {
  return new Request(`http://localhost:4000/api/venues/suggest${qs}`);
}

describe("GET /api/venues/suggest", () => {
  afterEach(() => {
    vi.mocked(fetchVenueSuggestions).mockReset();
  });

  it("q 为空白 → [] 且不打后端", async () => {
    const res = await GET(request("?q=%20%20"));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual([]);
    expect(fetchVenueSuggestions).not.toHaveBeenCalled();
  });

  it("转发去空白后的 q（截断到 200 字）并返回候选", async () => {
    vi.mocked(fetchVenueSuggestions).mockResolvedValue([{ id: "1", name: "Nature", abbr: "Nat" }]);
    const res = await GET(request(`?q=${encodeURIComponent(` ${"n".repeat(250)} `)}`));
    expect(fetchVenueSuggestions).toHaveBeenCalledWith("n".repeat(200));
    expect(await res.json()).toEqual([{ id: "1", name: "Nature", abbr: "Nat" }]);
  });

  it("后端失败 → 502 + []", async () => {
    vi.mocked(fetchVenueSuggestions).mockRejectedValue(new Error("boom"));
    const res = await GET(request("?q=lan"));
    expect(res.status).toBe(502);
    expect(await res.json()).toEqual([]);
  });
});
