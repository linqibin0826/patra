import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchVenueSuggestions, fetchVenueTitles } from "@/lib/portal-api/venues";

const BASE = "http://gw.test:9528/patra-catalog/portal/venues";

describe("期刊候选与刊名", () => {
  beforeEach(() => {
    process.env.PATRA_GATEWAY_BASE_URL = "http://gw.test:9528";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.PATRA_GATEWAY_BASE_URL;
  });

  it("fetchVenueSuggestions：按 q 取前 8 条，映射为 {id, name, abbr}", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          page: 1,
          pageSize: 8,
          total: 1,
          totalPages: 1,
          items: [{ id: "123", name: "The Lancet", abbr: "Lancet", impactFactor: 98.4 }],
        }),
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    expect(await fetchVenueSuggestions("lan cet")).toEqual([
      { id: "123", name: "The Lancet", abbr: "Lancet" },
    ]);
    expect(fetchMock).toHaveBeenCalledWith(`${BASE}?q=lan+cet&pageSize=8`, expect.anything());
  });

  it("fetchVenueSuggestions：非 2xx → throw", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 500 })));
    await expect(fetchVenueSuggestions("x")).rejects.toThrow("期刊候选加载失败：500");
  });

  it("fetchVenueTitles：并行取刊名；404 / 失败的 id 不出现在结果中", async () => {
    const fetchMock = vi.fn((url: string) => {
      if (url.endsWith("/1"))
        return Promise.resolve(new Response(JSON.stringify({ id: "1", title: "Nature" })));
      if (url.endsWith("/2")) return Promise.resolve(new Response("{}", { status: 404 }));
      return Promise.resolve(new Response("{}", { status: 500 }));
    });
    vi.stubGlobal("fetch", fetchMock);

    expect(await fetchVenueTitles(["1", "2", "3"])).toEqual({ "1": "Nature" });
  });

  it("fetchVenueTitles：空列表不发请求", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    expect(await fetchVenueTitles([])).toEqual({});
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
