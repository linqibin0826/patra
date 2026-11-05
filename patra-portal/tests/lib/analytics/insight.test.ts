// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchAlsoRead, fetchArticleStats, fetchHotReads } from "@/lib/analytics/insight";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const stat = (articleId: string, viewerCount = 1) => ({
  articleId,
  viewerCount,
  fulltextReaderCount: 0,
  hot: false,
});

describe("fetchArticleStats", () => {
  it("POST /articles/stats，不带会话；按 ID 成表", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [stat("11", 1284), stat("12", 0)] }));
    const map = await fetchArticleStats(["11", "12", "11"]);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-analytics/articles/stats");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ articleIds: ["11", "12"] });
    expect(init?.headers).not.toHaveProperty("Authorization");
    expect(map?.get("11")).toEqual({ viewerCount: 1284, fulltextReaderCount: 0, hot: false });
  });

  it("响应里少了的 ID 不在表里（调用方按 0 处理）", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [stat("11")] }));
    const map = await fetchArticleStats(["11", "12"]);
    expect(map?.has("12")).toBe(false);
  });

  it("超过 100 个分批并行取", async () => {
    fetchMock.mockImplementation(async () => Response.json({ items: [] }));
    await fetchArticleStats(Array.from({ length: 150 }, (_, i) => String(i + 1)));
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body)).articleIds).toHaveLength(50);
  });

  it("空列表不请求", async () => {
    expect(await fetchArticleStats([])).toEqual(new Map());
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["503", () => fetchMock.mockResolvedValue(new Response(null, { status: 503 }))],
    ["422", () => fetchMock.mockResolvedValue(Response.json({}, { status: 422 }))],
    [
      "400（契约 11/02 更新：请求体解析不了）",
      () => fetchMock.mockResolvedValue(Response.json({}, { status: 400 })),
    ],
    ["超时", () => fetchMock.mockRejectedValue(new DOMException("t", "TimeoutError"))],
    ["形状不对", () => fetchMock.mockResolvedValue(Response.json({ items: [{ articleId: 1 }] }))],
  ])("%s：整屏 null", async (_, arrange) => {
    arrange();
    expect(await fetchArticleStats(["11"])).toBeNull();
  });

  it("任一批失败整屏 null，不出现半屏有数", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json({ items: [] }))
      .mockResolvedValueOnce(new Response(null, { status: 503 }));
    expect(
      await fetchArticleStats(Array.from({ length: 150 }, (_, i) => String(i + 1))),
    ).toBeNull();
  });
});

describe("fetchHotReads", () => {
  it("取一页热读", async () => {
    fetchMock.mockResolvedValue(
      Response.json({
        computedAt: "2026-11-02T08:05:03.120Z",
        rankedTotal: 4,
        items: [
          { articleId: "1", source: "RANKED", rank: 1 },
          { articleId: "2", source: "LATEST", rank: null },
        ],
      }),
    );
    expect(await fetchHotReads()).toEqual({
      rankedTotal: 4,
      items: [
        { articleId: "1", source: "RANKED", rank: 1 },
        { articleId: "2", source: "LATEST", rank: null },
      ],
    });
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe("http://gw.test/patra-analytics/hot-reads");
  });

  it("任务从没跑过：空列表", async () => {
    fetchMock.mockResolvedValue(Response.json({ computedAt: null, rankedTotal: 0, items: [] }));
    expect(await fetchHotReads()).toEqual({ rankedTotal: 0, items: [] });
  });

  it("503：null", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 503 }));
    expect(await fetchHotReads()).toBeNull();
  });
});

describe("fetchAlsoRead", () => {
  it("按当前文献取最多 6 篇", async () => {
    const items = [
      { articleId: "2", source: "CO_READ", meshTerm: null, venueName: null },
      { articleId: "3", source: "SAME_MESH", meshTerm: "Shock, Septic", venueName: null },
    ];
    fetchMock.mockResolvedValue(Response.json({ items }));
    expect(await fetchAlsoRead("1")).toEqual(items);
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      "http://gw.test/patra-analytics/articles/1/also-read",
    );
  });

  it("503：null；ID 不合格不请求", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 503 }));
    expect(await fetchAlsoRead("1")).toBeNull();
    fetchMock.mockClear();
    expect(await fetchAlsoRead("01")).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
