import { makePaper } from "@tests/fixtures/paper";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EMPTY_PAPER_QUERY } from "@/lib/portal-api/paper-search";
import {
  fetchPublicationFacets,
  fetchPublicationSearch,
} from "@/lib/portal-api/publication-search";

const BASE = "http://gw.test:9528/patra-catalog/portal/publications";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

describe("fetchPublicationSearch", () => {
  beforeEach(() => {
    process.env.PATRA_GATEWAY_BASE_URL = "http://gw.test:9528";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.PATRA_GATEWAY_BASE_URL;
  });

  it("拼出 search URL（重复参数）并返回 PageResult", async () => {
    const page = { page: 1, pageSize: 20, total: 1, totalPages: 1, items: [makePaper()] };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(page));
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchPublicationSearch({ ...EMPTY_PAPER_QUERY, type: ["A, B", "C"] });

    expect(fetchMock).toHaveBeenCalledWith(
      `${BASE}/search?type=A%2C+B&type=C&sort=latest&page=1&pageSize=20`,
      expect.objectContaining({ cache: "no-store", signal: expect.any(AbortSignal) }),
    );
    expect(result).toEqual(page);
  });

  it("非 2xx → throw（冒泡到全局错误页）", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({}, 500)));
    await expect(fetchPublicationSearch(EMPTY_PAPER_QUERY)).rejects.toThrow("文献检索失败：500");
  });

  it("响应缺 items / total → 降级为空页", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ foo: 1 })));
    vi.spyOn(console, "warn").mockImplementation(() => {});
    const result = await fetchPublicationSearch({ ...EMPTY_PAPER_QUERY, page: 3 });
    expect(result).toEqual({ page: 3, pageSize: 20, total: 0, totalPages: 0, items: [] });
  });

  it("未配置 gateway → throw", async () => {
    delete process.env.PATRA_GATEWAY_BASE_URL;
    await expect(fetchPublicationSearch(EMPTY_PAPER_QUERY)).rejects.toThrow(
      "PATRA_GATEWAY_BASE_URL 未配置",
    );
  });
});

describe("fetchPublicationFacets", () => {
  beforeEach(() => {
    process.env.PATRA_GATEWAY_BASE_URL = "http://gw.test:9528";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.PATRA_GATEWAY_BASE_URL;
  });

  it("只带条件请求 facets，并做字段兜底", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        years: [{ value: "2026", count: 5 }],
        types: null,
        evidence: [],
        languages: [{ value: "en", count: 5 }],
        openAccess: 0,
        total: 5,
        lastSyncedAt: "2026-08-30T04:06:51Z",
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const facets = await fetchPublicationFacets({
      ...EMPTY_PAPER_QUERY,
      q: "x",
      page: 3,
      sort: "year",
    });

    expect(fetchMock).toHaveBeenCalledWith(`${BASE}/search/facets?q=x`, expect.anything());
    expect(facets).toEqual({
      years: [{ value: "2026", count: 5 }],
      types: [],
      evidence: [],
      languages: [{ value: "en", count: 5 }],
      openAccess: 0,
      total: 5,
      lastSyncedAt: "2026-08-30T04:06:51Z",
    });
  });

  it("非 2xx → throw", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({}, 503)));
    await expect(fetchPublicationFacets(EMPTY_PAPER_QUERY)).rejects.toThrow(
      "文献 facet 加载失败：503",
    );
  });
});
